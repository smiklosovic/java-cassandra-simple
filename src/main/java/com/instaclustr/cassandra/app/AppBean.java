package com.instaclustr.cassandra.app;

import com.datastax.driver.core.*;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.UUID;

@Component
public class AppBean {

    private static final Logger logger = LoggerFactory.getLogger(AppBean.class);

    private final Cluster cluster;
    private final Session session;

    public volatile boolean active = true;

    @Autowired
    public AppBean(final Cluster cluster, final Session session) {
        this.cluster = cluster;
        this.session = session;
    }

    public void select() {
        while (active) {
            try {
                Thread.sleep(1000);

                SimpleStatement statement = new SimpleStatement("SELECT * FROM test.test WHERE id = 1416377e-c6a2-4bb4-9e38-3f671b7ead53");
                statement.setKeyspace("test");

                ProtocolVersion protocolVersion = cluster.getConfiguration().getProtocolOptions().getProtocolVersion();
                statement.setRoutingKey(TypeCodec.uuid().serialize(UUID.fromString("1416377e-c6a2-4bb4-9e38-3f671b7ead53"), protocolVersion));

                ResultSet execute = session.execute(statement);

                execute.forEach(row -> {
                    logger.info("Response from {}: {}",
                            execute.getExecutionInfo().getQueriedHost().getEndPoint().resolve(),
                            row.getUUID(0).toString());
                });
            } catch (final InterruptedException ex) {
                System.out.println("catching interrupted");
            } catch (final Exception ex) {
                logger.warn(ex.getMessage());
            }
        }
    }

    @PreDestroy
    public void close() {
        System.out.println("closing selection bean");
        active = false;
    }
}
