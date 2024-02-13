/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PrometheusMetricsReporterConfigTest {
    @Test
    public void testDefaults() {
        PrometheusMetricsReporterConfig config = new PrometheusMetricsReporterConfig(Collections.emptyMap());

        assertEquals(PrometheusMetricsReporterConfig.LISTENER_CONFIG_DEFAULT, config.listener());
        assertTrue(config.isAllowed("random_name"));
    }

    @Test
    public void testOverrides() {
        Map<String, String> props = new HashMap<>();
        props.put(PrometheusMetricsReporterConfig.LISTENER_CONFIG, "http://:0");
        props.put(PrometheusMetricsReporterConfig.ALLOWLIST_CONFIG, "kafka_server.*");
        PrometheusMetricsReporterConfig config = new PrometheusMetricsReporterConfig(props);

        assertEquals("http://:0", config.listener());
        assertFalse(config.isAllowed("random_name"));
        assertTrue(config.isAllowed("kafka_server_metric"));
    }

    @Test
    public void testAllowlist() {
        Map<String, String> props = new HashMap<>();
        props.put(PrometheusMetricsReporterConfig.ALLOWLIST_CONFIG, "kafka_server.*,kafka_network.*");
        PrometheusMetricsReporterConfig config = new PrometheusMetricsReporterConfig(props);

        assertFalse(config.isAllowed("random_name"));
        assertTrue(config.isAllowed("kafka_server_metric"));
        assertTrue(config.isAllowed("kafka_network_metric"));
    }
}
