package com.instaclustr.cassandra.app.configuration;

import com.datastax.driver.core.*;
import com.datastax.driver.core.policies.*;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.ByteBuffer;
import java.util.*;

@Configuration
public class ApplicationConfiguration {
    @Bean
    public Cluster getCluster(final CassandraProperties cassandraProperties) {
        return Cluster.builder()
                .addContactPoints(cassandraProperties.contactPointsLocalDc)
                .withAuthProvider(new PlainTextAuthProvider(cassandraProperties.username, cassandraProperties.password))
                .withPort(cassandraProperties.contactPort)
                //.withSSL(CassandraSSLHelper.getSSLOptions(cassandraProperties))
                .withLoadBalancingPolicy(new LoggingTokenAwarePolicy(
                        new TokenAwarePolicy(LatencyAwarePolicy.builder(
                                new DCAwareRoundRobinPolicy.Builder().withLocalDc("dc1").build()).build())))
                .withoutMetrics()
                .withoutJMXReporting()
                //.withProtocolVersion(ProtocolVersion.V4)
                .build();
    }

    @Bean
    public Session getLocalDcSession(final Cluster cluster) {
        return cluster.connect();
    }

    public static class LoggingTokenAwarePolicy extends TokenAwarePolicy {

        private static final Logger logger = LoggerFactory.getLogger(LoggingTokenAwarePolicy.class);

        private final ReplicaOrdering replicaOrdering;
        private final LoadBalancingPolicy childPolicy;
        private volatile Metadata clusterMetadata;
        private volatile ProtocolVersion protocolVersion;
        private volatile CodecRegistry codecRegistry;

        public LoggingTokenAwarePolicy(LoadBalancingPolicy childPolicy, ReplicaOrdering replicaOrdering) {
            super(childPolicy, replicaOrdering);
            this.childPolicy = childPolicy;
            this.replicaOrdering = replicaOrdering;
        }

        public LoggingTokenAwarePolicy(LoadBalancingPolicy childPolicy) {
            this(childPolicy, ReplicaOrdering.RANDOM);
        }

        @Override
        public void init(Cluster cluster, Collection<Host> hosts) {
            clusterMetadata = cluster.getMetadata();
            protocolVersion = cluster.getConfiguration().getProtocolOptions().getProtocolVersion();
            codecRegistry = cluster.getConfiguration().getCodecRegistry();
            super.init(cluster, hosts);
        }

        @Override
        public Iterator<Host> newQueryPlan(final String loggedKeyspace, final Statement statement) {

            ByteBuffer partitionKey = statement.getRoutingKey(protocolVersion, codecRegistry);
            String keyspace = statement.getKeyspace();
            if (keyspace == null) keyspace = loggedKeyspace;

            if (partitionKey == null || keyspace == null) {
                logger.info("Routing key for statement is null! Returning query plan of child policy!");
                return childPolicy.newQueryPlan(keyspace, statement);
            }

            final Set<Host> replicas = clusterMetadata.getReplicas(Metadata.quote(keyspace), partitionKey);

            if (replicas.isEmpty()) {
                logger.info("Replicas for partition key is empty set! Returning query plan of child policy");
                return childPolicy.newQueryPlan(loggedKeyspace, statement);
            } else {
                logger.info("Replicas for key are {}", replicas);
            }

            if (replicaOrdering == ReplicaOrdering.NEUTRAL) {

                final Iterator<Host> childIterator = childPolicy.newQueryPlan(keyspace, statement);

                return new AbstractIterator<Host>() {

                    private List<Host> nonReplicas;
                    private Iterator<Host> nonReplicasIterator;

                    @Override
                    protected Host computeNext() {

                        while (childIterator.hasNext()) {

                            Host host = childIterator.next();

                            if (host.isUp()
                                    && replicas.contains(host)
                                    && childPolicy.distance(host) == HostDistance.LOCAL) {
                                // UP replicas should be prioritized, retaining order from childPolicy
                                logger.info(String.format("Neutral replica ordering, using host %s", host));
                                return host;
                            } else {
                                // save for later
                                if (nonReplicas == null) nonReplicas = new ArrayList<Host>();
                                logger.info("Saving {} to nonreplicas", host);
                                nonReplicas.add(host);
                            }
                        }

                        // This should only engage if all local replicas are DOWN
                        if (nonReplicas != null) {

                            if (nonReplicasIterator == null) nonReplicasIterator = nonReplicas.iterator();

                            if (nonReplicasIterator.hasNext()) {
                                Host next = nonReplicasIterator.next();
                                logger.info("Returning {} from non replicas", next);
                            };
                        }

                        logger.info("END OF DATA");
                        return endOfData();
                    }
                };

            } else {

                final Iterator<Host> replicasIterator;

                if (replicaOrdering == ReplicaOrdering.RANDOM) {
                    List<Host> replicasList = Lists.newArrayList(replicas);
                    Collections.shuffle(replicasList);
                    replicasIterator = replicasList.iterator();
                } else {
                    replicasIterator = replicas.iterator();
                }

                return new AbstractIterator<Host>() {

                    private Iterator<Host> childIterator;

                    @Override
                    protected Host computeNext() {
                        while (replicasIterator.hasNext()) {
                            Host host = replicasIterator.next();
                            if (host.isUp() && childPolicy.distance(host) == HostDistance.LOCAL) {
                                logger.info(String.format("Using host %s", host));
                                return host;
                            }
                        }

                        if (childIterator == null)
                            childIterator = childPolicy.newQueryPlan(loggedKeyspace, statement);

                        while (childIterator.hasNext()) {
                            Host host = childIterator.next();
                            // Skip it if it was already a local replica
                            if (!replicas.contains(host) || childPolicy.distance(host) != HostDistance.LOCAL) {
                                logger.info(String.format("Using host %s", host));
                                return host;
                            }
                        }
                        logger.info("END OF DATA");
                        return endOfData();
                    }
                };
            }
        }
    }
}
