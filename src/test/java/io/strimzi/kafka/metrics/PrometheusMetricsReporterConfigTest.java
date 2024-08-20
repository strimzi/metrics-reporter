/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import io.prometheus.metrics.model.registry.PrometheusRegistry;
import org.apache.kafka.common.config.ConfigException;
import org.junit.jupiter.api.Test;

import java.net.BindException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static io.strimzi.kafka.metrics.PrometheusMetricsReporterConfig.ALLOWLIST_CONFIG;
import static io.strimzi.kafka.metrics.PrometheusMetricsReporterConfig.LISTENER_CONFIG;
import static io.strimzi.kafka.metrics.PrometheusMetricsReporterConfig.LISTENER_ENABLE_CONFIG;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class PrometheusMetricsReporterConfigTest {
    @Test
    public void testDefaults() {
        PrometheusMetricsReporterConfig config = new PrometheusMetricsReporterConfig(emptyMap(), new PrometheusRegistry());
        assertEquals(PrometheusMetricsReporterConfig.LISTENER_CONFIG_DEFAULT, config.listener());
        assertTrue(config.isAllowed("random_name"));
    }

    @Test
    public void testOverrides() {
        Map<String, String> props = new HashMap<>();
        props.put(LISTENER_CONFIG, "http://:0");
        props.put(ALLOWLIST_CONFIG, "kafka_server.*");
        PrometheusMetricsReporterConfig config = new PrometheusMetricsReporterConfig(props, new PrometheusRegistry());

        assertEquals("http://:0", config.listener());
        assertFalse(config.isAllowed("random_name"));
        assertTrue(config.isAllowed("kafka_server_metric"));
    }

    @Test
    public void testAllowList() {
        Map<String, String> props = singletonMap(ALLOWLIST_CONFIG, "kafka_server.*,kafka_network.*");
        PrometheusMetricsReporterConfig config = new PrometheusMetricsReporterConfig(props, new PrometheusRegistry());

        assertFalse(config.isAllowed("random_name"));
        assertTrue(config.isAllowed("kafka_server_metric"));
        assertTrue(config.isAllowed("kafka_network_metric"));

        assertThrows(ConfigException.class,
                () -> new PrometheusMetricsReporterConfig(singletonMap(ALLOWLIST_CONFIG, "hell[o,s]world"), null));
        assertThrows(ConfigException.class,
                () -> new PrometheusMetricsReporterConfig(singletonMap(ALLOWLIST_CONFIG, "hello\\,world"), null));
    }

    @Test
    public void testIsListenerEnabled() {
        Map<String, String> props = new HashMap<>();
        props.put(LISTENER_ENABLE_CONFIG, "true");
        props.put(LISTENER_CONFIG, "http://:0");
        PrometheusMetricsReporterConfig config = new PrometheusMetricsReporterConfig(props, new PrometheusRegistry());
        Optional<HttpServers.ServerCounter> httpServerOptional = config.startHttpServer();

        assertTrue(config.isListenerEnabled());
        assertTrue(httpServerOptional.isPresent());
        HttpServers.release(httpServerOptional.get());
    }

    @Test
    public void testIsListenerDisabled() {
        Map<String, Boolean> props = singletonMap(LISTENER_ENABLE_CONFIG, false);
        PrometheusMetricsReporterConfig config = new PrometheusMetricsReporterConfig(props, new PrometheusRegistry());
        Optional<HttpServers.ServerCounter> httpServerOptional = config.startHttpServer();

        assertTrue(httpServerOptional.isEmpty());
        assertFalse(config.isListenerEnabled());
    }

    @Test
    public void testStartHttpServer() {
        Map<String, String> props = new HashMap<>();
        props.put(LISTENER_CONFIG, "http://:0");
        PrometheusMetricsReporterConfig config = new PrometheusMetricsReporterConfig(props, new PrometheusRegistry());
        Optional<HttpServers.ServerCounter> httpServerOptional = config.startHttpServer();
        assertTrue(httpServerOptional.isPresent());

        PrometheusMetricsReporterConfig config2 = new PrometheusMetricsReporterConfig(props, new PrometheusRegistry());
        Optional<HttpServers.ServerCounter> httpServerOptional2 = config2.startHttpServer();
        assertTrue(httpServerOptional2.isPresent());

        props = new HashMap<>();
        props.put(LISTENER_CONFIG, "http://:" + httpServerOptional.get().port());
        PrometheusMetricsReporterConfig config3 = new PrometheusMetricsReporterConfig(props, new PrometheusRegistry());
        Exception exc = assertThrows(RuntimeException.class, config3::startHttpServer);
        assertInstanceOf(BindException.class, exc.getCause());
    }
}

