/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.strimzi.kafka.metrics.kafka.KafkaCollector;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.KafkaMetricsContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.strimzi.kafka.metrics.MetricsUtils.getMetrics;
import static io.strimzi.kafka.metrics.MetricsUtils.newKafkaMetric;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KafkaPrometheusMetricsReporterTest {

    private final Map<String, String> labels = Collections.singletonMap("key", "value");
    private Map<String, String> configs;
    private PrometheusRegistry registry;
    private KafkaCollector kafkaCollector;

    @BeforeEach
    public void setup() {
        configs = new HashMap<>();
        configs.put(PrometheusMetricsReporterConfig.LISTENER_CONFIG, "http://:0");
        registry = new PrometheusRegistry();
        PrometheusCollector prometheusCollector = new PrometheusCollector();
        kafkaCollector = new KafkaCollector(prometheusCollector);
        registry.register(prometheusCollector);
    }

    @Test
    public void testLifeCycle() throws Exception {
        KafkaPrometheusMetricsReporter reporter = new KafkaPrometheusMetricsReporter(registry, kafkaCollector);
        configs.put(PrometheusMetricsReporterConfig.ALLOWLIST_CONFIG, "kafka_server_group_name.*");
        reporter.configure(configs);
        reporter.contextChange(new KafkaMetricsContext("kafka.server"));

        int port = reporter.getPort().orElseThrow();
        assertEquals(0, getMetrics(port).size());

        // Adding a metric not matching the allowlist does nothing
        KafkaMetric metric1 = newKafkaMetric("other", "group", (config, now) -> 0, labels);
        reporter.init(Collections.singletonList(metric1));
        List<String> metrics = getMetrics(port);
        assertEquals(0, metrics.size());

        // Adding a metric that matches the allowlist
        KafkaMetric metric2 = newKafkaMetric("name", "group", (config, now) -> 0, labels);
        reporter.metricChange(metric2);
        metrics = getMetrics(port);
        assertEquals(1, metrics.size());

        // Adding a non-numeric metric
        KafkaMetric metric3 = newKafkaMetric("name1", "group", (config, now) -> "hello", labels);
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
        KafkaPrometheusMetricsReporter reporter1 = new KafkaPrometheusMetricsReporter(registry, kafkaCollector);
        reporter1.configure(configs);
        reporter1.contextChange(new KafkaMetricsContext("kafka.server"));
        Optional<Integer> port1 = reporter1.getPort();
        assertTrue(port1.isPresent());
        assertEquals(0, getMetrics(port1.get()).size());

        KafkaPrometheusMetricsReporter reporter2 = new KafkaPrometheusMetricsReporter(registry, kafkaCollector);
        reporter2.configure(configs);
        reporter2.contextChange(new KafkaMetricsContext("kafka.server"));
        Optional<Integer> port2 = reporter1.getPort();
        assertTrue(port2.isPresent());
        assertEquals(0, getMetrics(port2.get()).size());

        assertEquals(port1, port2);

        KafkaMetric metric1 = newKafkaMetric("name", "group", (config, now) -> 0, Collections.singletonMap("name", "metric1"));
        reporter1.init(Collections.singletonList(metric1));
        KafkaMetric metric2 = newKafkaMetric("name", "group", (config, now) -> 0, Collections.singletonMap("name", "metric2"));
        reporter2.init(Collections.singletonList(metric2));
        assertEquals(2, getMetrics(port1.get()).size());

        reporter1.metricRemoval(metric1);
        reporter1.close();

        assertEquals(1, getMetrics(port1.get()).size());

        reporter2.close();
    }

}
