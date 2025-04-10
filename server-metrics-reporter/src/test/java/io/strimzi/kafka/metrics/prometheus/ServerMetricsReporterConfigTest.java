/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.prometheus;

import io.prometheus.metrics.model.registry.PrometheusRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.strimzi.kafka.metrics.prometheus.ClientMetricsReporterConfig.ALLOWLIST_CONFIG;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class ServerMetricsReporterConfigTest {

    @Test
    public void testReconfigure() {
        Map<String, String> props = Map.of(ALLOWLIST_CONFIG, "pattern1");
        ServerMetricsReporterConfig config = new ServerMetricsReporterConfig(props, new PrometheusRegistry());
        assertTrue(config.allowlist().pattern().contains("pattern1"));

        props = Map.of(ALLOWLIST_CONFIG, "pattern2");
        config.reconfigure(props);
        assertFalse(config.allowlist().pattern().contains("pattern1"));
        assertTrue(config.allowlist().pattern().contains("pattern2"));
    }
}

