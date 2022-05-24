package com.instaclustr.cassandra.app;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.ProtocolVersion;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.Optional;
import java.util.UUID;

@Component
public class AppBean {

    private static final Logger logger = LoggerFactory.getLogger(AppBean.class);

    private final CqlSession session;

    public volatile boolean active = true;

    private static final UUID uuid = UUID.fromString("1416377e-c6a2-4bb4-9e38-3f671b7ead53");

    @Autowired
    public AppBean(final CqlSession session) {
        this.session = session;
    }

    public void select() {
        while (active) {
            try {
                Thread.sleep(1000);

                SimpleStatement select = QueryBuilder.selectFrom("test", "test").all()
                        .whereColumn("id")
                        .isEqualTo(QueryBuilder.literal(uuid))
                        .build();

                select.setKeyspace("test");

                ProtocolVersion protocolVersion = session.getContext().getProtocolVersion();
                select = select.setRoutingKey(TypeCodecs.UUID.encode(uuid, protocolVersion));

                ResultSet execute = session.execute(select);

                execute.forEach(row -> {
                    Node coordinator = execute.getExecutionInfo().getCoordinator();
                    if (coordinator != null) {
                        logger.info("Response from {}: {}",
                                    coordinator.getEndPoint().resolve(),
                                    Optional.ofNullable(row.getUuid("id")).map(UUID::toString).orElseGet(() -> "NOT FOUND"));
                    }
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
