package com.instaclustr.cassandra.app.configuration;

import com.datastax.driver.core.RemoteEndpointAwareNettySSLOptions;
import com.datastax.driver.core.SSLOptions;
import io.netty.handler.ssl.SslContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.Collections;
import java.util.Optional;

import static io.netty.handler.ssl.SslProvider.JDK;
import static java.lang.String.format;

public final class CassandraSSLHelper {

    private static final Logger logger = LoggerFactory.getLogger(CassandraSSLHelper.class);

    public static final SSLOptions getSSLOptions(final CassandraProperties cassandraProperties) {

        if (!cassandraProperties.enableSSL) {
            return null;
        }

        try {
            final Optional<KeyStore> keyStore = getKeystore(cassandraProperties.keystoreFile, cassandraProperties.keystorePassword);
            final Optional<KeyStore> trustStore = getKeystore(cassandraProperties.truststoreFile, cassandraProperties.truststorePassword);

            Optional<TrustManagerFactory> tmf = Optional.empty();

            if (trustStore.isPresent()) {
                TrustManagerFactory tmfInstance = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmfInstance.init(trustStore.get());
                tmf = Optional.of(tmfInstance);
            }

            Optional<KeyManagerFactory> kmf = Optional.empty();

            if (cassandraProperties.clientAuth) {
                if (keyStore.isPresent()) {
                    KeyManagerFactory kmfInstance = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                    kmfInstance.init(keyStore.get(), Optional.ofNullable(cassandraProperties.keystorePassword).map(String::toCharArray).orElse(null));
                    kmf = Optional.of(kmfInstance);
                }
            }

            final SslContextBuilder builder = SslContextBuilder
                    .forClient()
                    .sslProvider(JDK)
                    .trustManager(tmf.orElse(null))
                    .keyManager(kmf.orElse(null))
                    .ciphers(Collections.singletonList("TLS_RSA_WITH_AES_256_CBC_SHA"));

            return new RemoteEndpointAwareNettySSLOptions(builder.build());
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to construct SSLOptions for Cassandra cluster", ex);
        }
    }

    private static Optional<KeyStore> getKeystore(final String ksPath, final String ksPassword) {

        if (ksPath == null) {
            return Optional.empty();
        }

        try (final InputStream ksIn = new BufferedInputStream(new FileInputStream(new File(ksPath)))) {
            final KeyStore ks = KeyStore.getInstance("JKS");

            ks.load(ksIn, Optional.ofNullable(ksPassword).map(String::toCharArray).orElse(null));

            return Optional.of(ks);
        } catch (Exception ex) {
            logger.debug(format("Unable to load keystore from external location: %s", ksPath));
            return Optional.empty();
        }
    }

}
