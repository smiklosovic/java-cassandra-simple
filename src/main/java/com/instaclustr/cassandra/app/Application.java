package com.instaclustr.cassandra.app;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.cassandra.CassandraAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication(exclude = {CassandraAutoConfiguration.class})
public class Application {

    private static ConfigurableApplicationContext ctx;

    public static void main(String[] args) {
        ctx = new SpringApplicationBuilder(Application.class)
                .web(WebApplicationType.NONE)
                .run(args);

        ctx.getBean(AppBean.class).select();

        ctx.close();
    }
}
