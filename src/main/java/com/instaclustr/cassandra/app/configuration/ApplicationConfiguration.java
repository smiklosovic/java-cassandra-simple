package com.instaclustr.cassandra.app.configuration;

import com.datastax.oss.driver.api.core.CqlSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfiguration {

    @Bean
    public CqlSession getLocalDcCluster() {
        return CqlSession.builder().build();
    }
}
