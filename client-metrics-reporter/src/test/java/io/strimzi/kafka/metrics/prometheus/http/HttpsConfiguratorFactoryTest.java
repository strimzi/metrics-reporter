/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.prometheus.http;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import org.apache.kafka.common.config.ConfigException;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.net.InetSocketAddress;
import java.util.List;

import static io.strimzi.kafka.metrics.prometheus.ClientMetricsReporterConfig.LISTENER_SSL_ENABLED_CIPHER_SUITES_CONFIG;
import static io.strimzi.kafka.metrics.prometheus.ClientMetricsReporterConfig.LISTENER_SSL_ENABLED_PROTOCOLS_CONFIG;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpsConfiguratorFactoryTest {

    @Test
    public void testCreateConfiguresProtocolsAndCipherSuites() throws Exception {
        SSLContext sslContext = SSLContext.getDefault();
        HttpsConfiguratorFactory factory = new HttpsConfiguratorFactory(
                List.of("TLSv1.3"),
                List.of("TLS_AES_128_GCM_SHA256"));
        HttpsConfigurator configurator = factory.create(sslContext);
        TestHttpsParameters params = new TestHttpsParameters(configurator);

        configurator.configure(params);

        assertArrayEquals(new String[]{"TLSv1.3"}, params.sslParameters.getProtocols());
        assertArrayEquals(new String[]{"TLS_AES_128_GCM_SHA256"}, params.sslParameters.getCipherSuites());
    }

    @Test
    public void testCreateLeavesDefaultCipherSuitesWhenEmpty() throws Exception {
        SSLContext sslContext = SSLContext.getDefault();
        HttpsConfiguratorFactory factory = new HttpsConfiguratorFactory(List.of("TLSv1.2"), List.of());
        HttpsConfigurator configurator = factory.create(sslContext);
        TestHttpsParameters params = new TestHttpsParameters(configurator);

        configurator.configure(params);

        assertArrayEquals(new String[]{"TLSv1.2"}, params.sslParameters.getProtocols());
        assertArrayEquals(
                sslContext.getDefaultSSLParameters().getCipherSuites(),
                params.sslParameters.getCipherSuites());
    }

    @Test
    public void testCreateRejectsUnsupportedProtocol() throws Exception {
        HttpsConfiguratorFactory factory = new HttpsConfiguratorFactory(List.of("TLSv1.9000"), List.of());

        ConfigException exception = assertThrows(ConfigException.class, () -> factory.create(SSLContext.getDefault()));

        assertTrue(exception.getMessage().contains(LISTENER_SSL_ENABLED_PROTOCOLS_CONFIG));
        assertTrue(exception.getMessage().contains("TLSv1.9000"));
    }

    @Test
    public void testCreateRejectsUnsupportedCipherSuite() throws Exception {
        HttpsConfiguratorFactory factory = new HttpsConfiguratorFactory(
                List.of("TLSv1.3"),
                List.of("TLS_FAKE_WITH_NOTHING_SHA256"));

        ConfigException exception = assertThrows(ConfigException.class, () -> factory.create(SSLContext.getDefault()));

        assertTrue(exception.getMessage().contains(LISTENER_SSL_ENABLED_CIPHER_SUITES_CONFIG));
        assertTrue(exception.getMessage().contains("TLS_FAKE_WITH_NOTHING_SHA256"));
    }

    private static class TestHttpsParameters extends HttpsParameters {
        private final HttpsConfigurator configurator;
        private SSLParameters sslParameters;

        private TestHttpsParameters(HttpsConfigurator configurator) {
            this.configurator = configurator;
        }

        @Override
        public HttpsConfigurator getHttpsConfigurator() {
            return configurator;
        }

        @Override
        public InetSocketAddress getClientAddress() {
            return new InetSocketAddress("localhost", 0);
        }

        @Override
        public void setSSLParameters(SSLParameters sslParameters) {
            this.sslParameters = sslParameters;
        }
    }
}
