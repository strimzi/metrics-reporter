/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.prometheus;

import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.strimzi.kafka.metrics.prometheus.http.HttpServers;
import org.apache.kafka.common.config.ConfigException;
import org.junit.jupiter.api.Test;

import javax.net.ssl.HttpsURLConnection;
import java.net.BindException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.strimzi.kafka.metrics.prometheus.ClientMetricsReporterConfig.ALLOWLIST_CONFIG;
import static io.strimzi.kafka.metrics.prometheus.ClientMetricsReporterConfig.LISTENER_CONFIG;
import static io.strimzi.kafka.metrics.prometheus.ClientMetricsReporterConfig.LISTENER_ENABLE_CONFIG;
import static io.strimzi.kafka.metrics.prometheus.ClientMetricsReporterConfig.LISTENER_SSL_CERTIFICATE_CONFIG;
import static io.strimzi.kafka.metrics.prometheus.ClientMetricsReporterConfig.LISTENER_SSL_CERTIFICATE_LOCATION_CONFIG;
import static io.strimzi.kafka.metrics.prometheus.ClientMetricsReporterConfig.LISTENER_SSL_ENABLED_CIPHER_SUITES_CONFIG;
import static io.strimzi.kafka.metrics.prometheus.ClientMetricsReporterConfig.LISTENER_SSL_ENABLED_PROTOCOLS_CONFIG;
import static io.strimzi.kafka.metrics.prometheus.ClientMetricsReporterConfig.LISTENER_SSL_KEY_CONFIG;
import static io.strimzi.kafka.metrics.prometheus.ClientMetricsReporterConfig.LISTENER_SSL_KEY_LOCATION_CONFIG;
import static io.strimzi.kafka.metrics.prometheus.http.SslTestUtils.CERTIFICATE;
import static io.strimzi.kafka.metrics.prometheus.http.SslTestUtils.PRIVATE_KEY;
import static io.strimzi.kafka.metrics.prometheus.http.SslTestUtils.trustingClientSslContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class ClientMetricsReporterConfigTest {

    @Test
    public void testDefaults() {
        ClientMetricsReporterConfig config = new ClientMetricsReporterConfig(Map.of(), new PrometheusRegistry());
        assertEquals(ClientMetricsReporterConfig.LISTENER_CONFIG_DEFAULT, config.listener());
        assertTrue(config.isAllowed("random_name"));
    }

    @Test
    public void testOverrides() {
        Map<String, String> props = Map.of(
            LISTENER_CONFIG, "http://:0",
            ALLOWLIST_CONFIG, "kafka_server.*");
        ClientMetricsReporterConfig config = new ClientMetricsReporterConfig(props, new PrometheusRegistry());

        assertEquals("http://:0", config.listener());
        assertFalse(config.isAllowed("random_name"));
        assertTrue(config.isAllowed("kafka_server_metric"));

        props = Map.of(LISTENER_CONFIG, "https://:0");
        config = new ClientMetricsReporterConfig(props, new PrometheusRegistry());

        assertEquals("https://:0", config.listener());
    }

    @Test
    public void testHttpListenerDoesNotCreateSslFactories() {
        ClientMetricsReporterConfig config = new ClientMetricsReporterConfig(Map.of(), new PrometheusRegistry());

        assertNull(config.sslContextFactory);
        assertNull(config.httpsConfiguratorFactory);
    }

    @Test
    public void testHttpsListenerCreatesSslFactoriesWithDefaults() {
        ClientMetricsReporterConfig config = new ClientMetricsReporterConfig(
                Map.of(LISTENER_CONFIG, "https://:0"),
                new PrometheusRegistry());

        assertNull(config.sslContextFactory.certificateLocation);
        assertNull(config.sslContextFactory.keyLocation);
        assertNull(config.sslContextFactory.certificate);
        assertNull(config.sslContextFactory.key);
        assertEquals(List.of("TLSv1.2", "TLSv1.3"), config.httpsConfiguratorFactory.enabledProtocols());
        assertTrue(config.httpsConfiguratorFactory.enabledCipherSuites().isEmpty());
    }

    @Test
    public void testSslConfigOverrides() {
        Map<String, String> props = Map.of(
                LISTENER_CONFIG, "https://:0",
                LISTENER_SSL_CERTIFICATE_LOCATION_CONFIG, "/tmp/tls.crt",
                LISTENER_SSL_KEY_LOCATION_CONFIG, "/tmp/tls.key",
                LISTENER_SSL_ENABLED_PROTOCOLS_CONFIG, "TLSv1.3",
                LISTENER_SSL_ENABLED_CIPHER_SUITES_CONFIG, "TLS_AES_128_GCM_SHA256,TLS_AES_256_GCM_SHA384");
        ClientMetricsReporterConfig config = new ClientMetricsReporterConfig(props, new PrometheusRegistry());

        assertEquals("/tmp/tls.crt", config.sslContextFactory.certificateLocation);
        assertEquals("/tmp/tls.key", config.sslContextFactory.keyLocation);
        assertEquals(List.of("TLSv1.3"), config.httpsConfiguratorFactory.enabledProtocols());
        assertEquals(
                List.of("TLS_AES_128_GCM_SHA256", "TLS_AES_256_GCM_SHA384"),
                config.httpsConfiguratorFactory.enabledCipherSuites());
    }

    @Test
    public void testInlineSslCertificateAndKeyDoNotOverrideLocationsInConfig() {
        Map<String, String> props = Map.of(
                LISTENER_CONFIG, "https://:0",
                LISTENER_SSL_CERTIFICATE_LOCATION_CONFIG, "/tmp/tls.crt",
                LISTENER_SSL_KEY_LOCATION_CONFIG, "/tmp/tls.key",
                LISTENER_SSL_CERTIFICATE_CONFIG, "-----BEGIN CERTIFICATE-----",
                LISTENER_SSL_KEY_CONFIG, "-----BEGIN PRIVATE KEY-----");
        ClientMetricsReporterConfig config = new ClientMetricsReporterConfig(props, new PrometheusRegistry());

        assertEquals("/tmp/tls.crt", config.sslContextFactory.certificateLocation);
        assertEquals("/tmp/tls.key", config.sslContextFactory.keyLocation);
        assertEquals("-----BEGIN CERTIFICATE-----", config.sslContextFactory.certificate);
        assertEquals("-----BEGIN PRIVATE KEY-----", config.sslContextFactory.key);
    }

    @Test
    public void testInlineSslCertificateAndKeyAreHiddenInToString() {
        Map<String, String> props = Map.of(
                LISTENER_CONFIG, "https://:0",
                LISTENER_SSL_CERTIFICATE_CONFIG, "certificate-content",
                LISTENER_SSL_KEY_CONFIG, "key-content");
        ClientMetricsReporterConfig config = new ClientMetricsReporterConfig(props, new PrometheusRegistry());

        assertFalse(config.toString().contains("certificate-content"));
        assertFalse(config.toString().contains("key-content"));
        assertTrue(config.toString().contains("certificate=[hidden]"));
        assertTrue(config.toString().contains("key=[hidden]"));
    }

    @Test
    public void testAllowList() {
        Map<String, String> props = Map.of(ALLOWLIST_CONFIG, "kafka_server.*,kafka_network.*");
        ClientMetricsReporterConfig config = new ClientMetricsReporterConfig(props, new PrometheusRegistry());

        assertTrue(config.allowlist().pattern().contains("kafka_server.*"));
        assertTrue(config.allowlist().pattern().contains("kafka_network.*"));

        assertFalse(config.isAllowed("random_name"));
        assertTrue(config.isAllowed("kafka_server_metric"));
        assertTrue(config.isAllowed("kafka_network_metric"));

        assertThrows(ConfigException.class,
                () -> new ClientMetricsReporterConfig(Map.of(ALLOWLIST_CONFIG, "hell[o,s]world"), null));
        assertThrows(ConfigException.class,
                () -> new ClientMetricsReporterConfig(Map.of(ALLOWLIST_CONFIG, "hello\\,world"), null));
    }

    @Test
    public void testIsListenerEnabled() {
        Map<String, String> props = Map.of(
            LISTENER_ENABLE_CONFIG, "true",
            LISTENER_CONFIG, "http://:0");
        ClientMetricsReporterConfig config = new ClientMetricsReporterConfig(props, new PrometheusRegistry());
        Optional<HttpServers.ServerCounter> httpServerOptional = config.startHttpServer();

        assertTrue(config.isListenerEnabled());
        assertTrue(httpServerOptional.isPresent());
        HttpServers.release(httpServerOptional.get());
    }

    @Test
    public void testIsListenerDisabled() {
        Map<String, Boolean> props = Map.of(LISTENER_ENABLE_CONFIG, false);
        ClientMetricsReporterConfig config = new ClientMetricsReporterConfig(props, new PrometheusRegistry());
        Optional<HttpServers.ServerCounter> httpServerOptional = config.startHttpServer();

        assertTrue(httpServerOptional.isEmpty());
        assertFalse(config.isListenerEnabled());
    }

    @Test
    public void testStartHttpServer() {
        Map<String, String> props = Map.of(LISTENER_CONFIG, "http://:0");
        ClientMetricsReporterConfig config = new ClientMetricsReporterConfig(props, new PrometheusRegistry());
        Optional<HttpServers.ServerCounter> httpServerOptional = config.startHttpServer();
        assertTrue(httpServerOptional.isPresent());

        ClientMetricsReporterConfig config2 = new ClientMetricsReporterConfig(props, new PrometheusRegistry());
        Optional<HttpServers.ServerCounter> httpServerOptional2 = config2.startHttpServer();
        assertTrue(httpServerOptional2.isPresent());

        props = Map.of(LISTENER_CONFIG, "http://:" + httpServerOptional.get().port());
        ClientMetricsReporterConfig config3 = new ClientMetricsReporterConfig(props, new PrometheusRegistry());
        Exception exc = assertThrows(RuntimeException.class, config3::startHttpServer);
        assertInstanceOf(BindException.class, exc.getCause());

        HttpServers.release(httpServerOptional.get());
        HttpServers.release(httpServerOptional2.get());
    }

    @Test
    public void testStartHttpsServer() throws Exception {
        Map<String, String> props = Map.of(
                LISTENER_CONFIG, "https://localhost:0",
                LISTENER_SSL_CERTIFICATE_CONFIG, CERTIFICATE,
                LISTENER_SSL_KEY_CONFIG, PRIVATE_KEY);
        ClientMetricsReporterConfig config = new ClientMetricsReporterConfig(props, new PrometheusRegistry());
        Optional<HttpServers.ServerCounter> httpsServerOptional = config.startHttpServer();

        assertTrue(httpsServerOptional.isPresent());
        try {
            assertTrue(httpsListenerStarted("localhost", httpsServerOptional.get().port()));
        } finally {
            HttpServers.release(httpsServerOptional.get());
        }
        assertFalse(httpsListenerStarted("localhost", httpsServerOptional.get().port()));
    }

    @Test
    public void testStartHttpsServerWithMissingSslConfigFails() {
        ClientMetricsReporterConfig config = new ClientMetricsReporterConfig(
                Map.of(LISTENER_CONFIG, "https://localhost:0"),
                new PrometheusRegistry());

        assertThrows(ConfigException.class, config::startHttpServer);
    }

    @Test
    public void testStartHttpsServerWithInvalidProtocolFails() {
        ClientMetricsReporterConfig config = new ClientMetricsReporterConfig(
                Map.of(
                        LISTENER_CONFIG, "https://localhost:0",
                        LISTENER_SSL_CERTIFICATE_CONFIG, CERTIFICATE,
                        LISTENER_SSL_KEY_CONFIG, PRIVATE_KEY,
                        LISTENER_SSL_ENABLED_PROTOCOLS_CONFIG, "TLSv1.9000"),
                new PrometheusRegistry());

        ConfigException exception = assertThrows(ConfigException.class, config::startHttpServer);

        assertTrue(exception.getMessage().contains(LISTENER_SSL_ENABLED_PROTOCOLS_CONFIG));
        assertTrue(exception.getMessage().contains("TLSv1.9000"));
    }

    private boolean httpsListenerStarted(String host, int port) {
        try {
            URL url = new URL("https://" + host + ":" + port + "/metrics");
            HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
            con.setSSLSocketFactory(trustingClientSslContext().getSocketFactory());
            con.setRequestMethod("HEAD");
            con.connect();
            return con.getResponseCode() == HttpsURLConnection.HTTP_OK;
        } catch (Exception e) {
            return false;
        }
    }
}
