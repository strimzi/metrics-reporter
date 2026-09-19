/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.prometheus;

import io.prometheus.metrics.model.registry.PrometheusRegistry;
import org.apache.kafka.common.config.ConfigException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.strimzi.kafka.metrics.prometheus.ClientMetricsReporterConfig.ALLOWLIST_CONFIG;
import static io.strimzi.kafka.metrics.prometheus.ServerMetricsReporterConfig.CLIENT_TELEMETRY_LABELS_CONFIG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    public void testTelemetryLabelsDefault() {
        Map<String, String> props = Map.of();
        ServerMetricsReporterConfig config = new ServerMetricsReporterConfig(props, new PrometheusRegistry());
        assertEquals(List.of("client_id"), config.telemetryLabels());
    }

    @Test
    public void testTelemetryLabelsCustom() {
        Map<String, String> props = Map.of(CLIENT_TELEMETRY_LABELS_CONFIG, "client_id,listener_name,principal,principal");
        ServerMetricsReporterConfig config = new ServerMetricsReporterConfig(props, new PrometheusRegistry());
        assertEquals(List.of("client_id", "listener_name", "principal"), config.telemetryLabels());
    }

    @Test
    public void testTelemetryLabelsInvalid() {
        Map<String, String> props = Map.of(CLIENT_TELEMETRY_LABELS_CONFIG, "invalid_label");
        assertThrows(ConfigException.class, () -> new ServerMetricsReporterConfig(props, new PrometheusRegistry()));
    }

    @Test
    public void testReconfigureTelemetryLabels() {
        Map<String, String> props = Map.of(
            ALLOWLIST_CONFIG, ".*",
            CLIENT_TELEMETRY_LABELS_CONFIG, "client_id");
        ServerMetricsReporterConfig config = new ServerMetricsReporterConfig(props, new PrometheusRegistry());
        assertEquals(List.of("client_id"), config.telemetryLabels());

        Map<String, String> newProps = Map.of(
            ALLOWLIST_CONFIG, ".*",
            CLIENT_TELEMETRY_LABELS_CONFIG, "client_id,listener_name");
        config.reconfigure(newProps);
        assertEquals(List.of("client_id", "listener_name"), config.telemetryLabels());
    }

    @Test
    public void testValidateReconfiguration() {
        Map<String, String> props = Map.of();
        ServerMetricsReporterConfig config = new ServerMetricsReporterConfig(props, new PrometheusRegistry());

        Map<String, String> validProps = Map.of(
            ALLOWLIST_CONFIG, ".*",
            CLIENT_TELEMETRY_LABELS_CONFIG, "client_id,principal");
        config.validate(validProps);

        Map<String, String> invalidProps = Map.of(CLIENT_TELEMETRY_LABELS_CONFIG, "invalid");
        assertThrows(ConfigException.class, () -> config.validate(invalidProps));
    }
}
