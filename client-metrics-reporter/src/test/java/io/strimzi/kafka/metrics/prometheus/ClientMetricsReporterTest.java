/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.prometheus;

import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.strimzi.kafka.metrics.prometheus.common.PrometheusCollector;
import io.strimzi.kafka.metrics.prometheus.kafka.KafkaCollector;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.KafkaMetricsContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.strimzi.kafka.metrics.prometheus.ClientMetricsReporter.PREFIXES;
import static io.strimzi.kafka.metrics.prometheus.KafkaMetricsUtils.newKafkaMetric;
import static io.strimzi.kafka.metrics.prometheus.MetricsUtils.getMetrics;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClientMetricsReporterTest {

    static final Map<String, String> LABELS = Map.of("key", "value");
    Map<String, String> configs;
    PrometheusRegistry registry;
    KafkaCollector kafkaCollector;

    @BeforeEach
    public void setup() {
        configs = new HashMap<>();
        configs.put(ClientMetricsReporterConfig.LISTENER_CONFIG, "http://:0");
        registry = new PrometheusRegistry();
        PrometheusCollector prometheusCollector = new PrometheusCollector();
        kafkaCollector = new KafkaCollector(prometheusCollector);
        registry.register(prometheusCollector);
    }

    @Test
    public void testLifeCycle() throws Exception {
        ClientMetricsReporter reporter = new ClientMetricsReporter(registry, kafkaCollector);
        configs.put(ClientMetricsReporterConfig.ALLOWLIST_CONFIG, "kafka_producer_group_name.*");
        reporter.configure(configs);
        reporter.contextChange(new KafkaMetricsContext("kafka.producer"));

        int port = reporter.getPort().orElseThrow();
        assertEquals(0, getMetrics(port).size());

        // Adding a metric not matching the allowlist does nothing
        KafkaMetric metric1 = newKafkaMetric("other", "group", (config, now) -> 0, LABELS);
        reporter.init(List.of(metric1));
        List<String> metrics = getMetrics(port);
        assertEquals(0, metrics.size());

        // Adding a metric that matches the allowlist
        KafkaMetric metric2 = newKafkaMetric("name", "group", (config, now) -> 0, LABELS);
        reporter.metricChange(metric2);
        metrics = getMetrics(port);
        assertEquals(1, metrics.size());

        // Adding a non-numeric metric
        KafkaMetric metric3 = newKafkaMetric("name1", "group", (config, now) -> "hello", LABELS);
        reporter.metricChange(metric3);
        metrics = getMetrics(port);
        assertEquals(2, metrics.size());

        // Removing a metric
        reporter.metricRemoval(metric3);
        metrics = getMetrics(port);
        assertEquals(1, metrics.size());

        reporter.close();
    }

    @Test
    public void testMultipleReporters() throws Exception {
        ClientMetricsReporter reporter1 = new ClientMetricsReporter(registry, kafkaCollector);
        reporter1.configure(configs);
        reporter1.contextChange(new KafkaMetricsContext("kafka.producer"));
        Optional<Integer> port1 = reporter1.getPort();
        assertTrue(port1.isPresent());
        assertEquals(0, getMetrics(port1.get()).size());

        ClientMetricsReporter reporter2 = new ClientMetricsReporter(registry, kafkaCollector);
        reporter2.configure(configs);
        reporter2.contextChange(new KafkaMetricsContext("kafka.producer"));
        Optional<Integer> port2 = reporter1.getPort();
        assertTrue(port2.isPresent());
        assertEquals(0, getMetrics(port2.get()).size());

        assertEquals(port1, port2);

        KafkaMetric metric1 = newKafkaMetric("name", "group", (config, now) -> 0, Map.of("name", "metric1"));
        reporter1.init(List.of(metric1));
        KafkaMetric metric2 = newKafkaMetric("name", "group", (config, now) -> 0, Map.of("name", "metric2"));
        reporter2.init(List.of(metric2));
        assertEquals(2, getMetrics(port1.get()).size());

        reporter1.metricRemoval(metric1);
        reporter1.close();

        assertEquals(1, getMetrics(port1.get()).size());

        reporter2.close();
    }

    @Test
    public void testReconfigurableConfigs() {
        try (ClientMetricsReporter reporter = new ClientMetricsReporter(registry, kafkaCollector)) {
            assertTrue(reporter.reconfigurableConfigs().isEmpty());
        }
    }

    @Test
    public void testContextChange() {
        try (ClientMetricsReporter reporter = new ClientMetricsReporter(registry, kafkaCollector)) {
            for (String prefix : PREFIXES) {
                reporter.contextChange(new KafkaMetricsContext(prefix));
            }
            assertThrows(IllegalStateException.class, () -> reporter.contextChange(new KafkaMetricsContext("other")));
        }
    }

}
