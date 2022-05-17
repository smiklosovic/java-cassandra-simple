package com.instaclustr.cassandra.app.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CassandraProperties {

    @Value("${cassandra.contact.points.local}")
    public String[] contactPointsLocalDc = new String[]{"127.0.0.1"};

    @Value("${cassandra.contact.port}")
    public int contactPort = 9042;

    @Value("${cassandra.username:cassandra}")
    public String username;

    @Value("${cassandra.password:cassandra}")
    public String password;

    @Value("${cassandra.keystore.file:}")
    public String keystoreFile;

    @Value("${cassandra.keystore.password:}")
    public String keystorePassword;

    @Value("${cassandra.truststore.file:}")
    public String truststoreFile;

    @Value("${cassandra.truststore.password:}")
    public String truststorePassword;

    @Value("${cassandra.dc.local:dc1}")
    public String localDC;

    @Value("${cassandra.enableSSL}")
    public boolean enableSSL;

    @Value("${cassandra.clientAuth}")
    public boolean clientAuth = false;
}
