/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.prometheus;

import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.KafkaMetricsContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.strimzi.kafka.metrics.prometheus.KafkaMetricsUtils.newKafkaMetric;
import static io.strimzi.kafka.metrics.prometheus.MetricsUtils.getMetrics;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ServerKafkaMetricsReporterTest extends ClientMetricsReporterTest {

    @Test
    public void testReconfigurableConfigs() {
        try (ServerKafkaMetricsReporter reporter = new ServerKafkaMetricsReporter(registry, kafkaCollector)) {
            assertFalse(reporter.reconfigurableConfigs().isEmpty());
        }
    }

    @Test
    public void testReconfigure() throws Exception {
        try (ServerKafkaMetricsReporter reporter = new ServerKafkaMetricsReporter(registry, kafkaCollector)) {
            configs.put(ServerMetricsReporterConfig.ALLOWLIST_CONFIG, "kafka_server_group_name.*");
            reporter.configure(configs);
            reporter.contextChange(new KafkaMetricsContext("kafka.server"));

            int port = reporter.getPort().orElseThrow();
            assertEquals(0, getMetrics(port).size());

            // Adding a metric not matching the allowlist does nothing
            KafkaMetric metric1 = newKafkaMetric("other", "group", (config, now) -> 0, LABELS);
            reporter.init(List.of(metric1));
            List<String> metrics = getMetrics(port);
            assertEquals(0, metrics.size());

            // Adding a metric matching the allowlist
            KafkaMetric metric2 = newKafkaMetric("name", "group", (config, now) -> 0, LABELS);
            reporter.metricChange(metric2);
            metrics = getMetrics(port);
            assertEquals(1, metrics.size());
            assertTrue(metrics.get(0).contains("name"));

            configs.put(ServerMetricsReporterConfig.ALLOWLIST_CONFIG, "kafka_server_group_other.*");
            reporter.reconfigure(configs);

            metrics = getMetrics(port);
            assertEquals(1, metrics.size());
            assertTrue(metrics.get(0).contains("other"));
        }
    }

    @Test
    public void testValidateReconfiguration() {
        try (ServerKafkaMetricsReporter reporter = new ServerKafkaMetricsReporter(registry, kafkaCollector)) {
            configs.put(ServerMetricsReporterConfig.ALLOWLIST_CONFIG, "kafka_server_group_name.*");
            reporter.configure(configs);
            reporter.contextChange(new KafkaMetricsContext("kafka.server"));

            configs.put(ServerMetricsReporterConfig.ALLOWLIST_CONFIG, ".*");
            reporter.validateReconfiguration(configs);

            configs.put(ServerMetricsReporterConfig.ALLOWLIST_CONFIG, "[\\]");
            assertThrows(ConfigException.class, () -> reporter.validateReconfiguration(configs));
        }
    }
}
