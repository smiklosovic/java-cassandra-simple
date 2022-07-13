package com.instaclustr.cassandra.app.configuration;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.loadbalancing.NodeDistance;
import com.datastax.oss.driver.api.core.loadbalancing.NodeDistanceEvaluator;
import com.datastax.oss.driver.api.core.metadata.Node;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Configuration
public class ApplicationConfiguration {


    @Bean
    public CqlSession getLocalDcCluster() {
        return CqlSession.builder()
                .withNodeDistanceEvaluator(new FilteringEvaluator("a89e3ed3-bb53-4b67-b463-428a79871b27",
                                                                  "ab6f0a96-4a5b-42b2-9e3a-a342a31ea66c"))
                .build();
    }

    private static class FilteringEvaluator implements NodeDistanceEvaluator {

        private final Set<UUID> whitelistedNodes;

        public FilteringEvaluator(String... whitelistedNodes) {
            this(Arrays.stream(whitelistedNodes).collect(Collectors.toSet()));
        }

        public FilteringEvaluator(Set<String> whitelistedNodes) {
            this.whitelistedNodes = whitelistedNodes.stream().map(UUID::fromString).collect(Collectors.toSet());
        }

        @Nullable
        @Override
        public NodeDistance evaluateDistance(@NonNull Node node, @Nullable String localDc) {
            return whitelistedNodes.contains(node.getHostId()) ? null : NodeDistance.IGNORED;
        }
    }
}
