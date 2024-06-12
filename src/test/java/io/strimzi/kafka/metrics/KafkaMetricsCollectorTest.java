/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import io.prometheus.client.Collector;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.metrics.Gauge;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.Measurable;
import org.apache.kafka.common.metrics.MetricConfig;
import org.apache.kafka.common.utils.Time;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KafkaMetricsCollectorTest {

    private final MetricConfig metricConfig = new MetricConfig();
    private final Time time = Time.SYSTEM;
    private Map<String, String> labels;

    @BeforeEach
    public void setup() {
        labels = new HashMap<>();
        labels.put("key", "value");
    }

    @Test
    public void testCollect() {
        Map<String, String> props = new HashMap<>();
        props.put(PrometheusMetricsReporterConfig.ALLOWLIST_CONFIG, "kafka_server_group_name.*");
        PrometheusMetricsReporterConfig config = new PrometheusMetricsReporterConfig(props);
        KafkaMetricsCollector collector = new KafkaMetricsCollector(config);
        collector.setPrefix("kafka.server");

        List<Collector.MetricFamilySamples> metrics = collector.collect();
        assertTrue(metrics.isEmpty());

        // Adding a metric not matching the allowlist does nothing
        collector.addMetric(buildMetric("name", "other", 2.0));
        metrics = collector.collect();
        assertTrue(metrics.isEmpty());

        // Adding a metric that matches the allowlist
        collector.addMetric(buildMetric("name", "group", 1.0));
        metrics = collector.collect();
        assertMetric(metrics, "kafka_server_group_name", 1.0, labels);
        assertEquals(1, metrics.size());

        Collector.MetricFamilySamples metricFamilySamples = metrics.get(0);
        Collector.MetricFamilySamples.Sample serverGroupNameSamples = metricFamilySamples.samples.get(0);

        assertEquals("kafka_server_group_name", metricFamilySamples.name);
        assertEquals(1, metricFamilySamples.samples.size());
        assertEquals(1.0, serverGroupNameSamples.value, 0.1);
        assertEquals(new ArrayList<>(labels.keySet()), serverGroupNameSamples.labelNames);
        assertEquals(new ArrayList<>(labels.values()), serverGroupNameSamples.labelValues);

        // Adding the same metric updates its value
        collector.addMetric(buildMetric("name", "group", 3.0));
        metrics = collector.collect();
        assertMetric(metrics, "kafka_server_group_name", 3.0, labels);
        assertEquals(1, metrics.size());

        Collector.MetricFamilySamples metricFamilySamples1 = metrics.get(0);
        Collector.MetricFamilySamples.Sample serverGroupNameSamples1 = metricFamilySamples1.samples.get(0);

        assertEquals("kafka_server_group_name", metricFamilySamples1.name);
        assertEquals(1, metricFamilySamples1.samples.size());
        assertEquals(3.0, serverGroupNameSamples1.value, 0.1);

        // Removing the metric
        collector.removeMetric(buildMetric("name", "group", 4.0));
        metrics = collector.collect();
        assertTrue(metrics.isEmpty());
    }

    @Test
    public void testCollectNonNumericMetric() {
        Map<String, String> props = new HashMap<>();
        props.put(PrometheusMetricsReporterConfig.ALLOWLIST_CONFIG, "kafka_server_group_name.*");
        PrometheusMetricsReporterConfig config = new PrometheusMetricsReporterConfig(props);
        KafkaMetricsCollector collector = new KafkaMetricsCollector(config);
        collector.setPrefix("kafka.server");

        List<Collector.MetricFamilySamples> metrics = collector.collect();
        assertTrue(metrics.isEmpty());

        // Adding a non-numeric metric converted
        KafkaMetric nonNumericMetric = buildNonNumericMetric("name", "group");
        collector.addMetric(nonNumericMetric);
        metrics = collector.collect();

        Map<String, String> expectedLabels = new HashMap<>(labels);
        expectedLabels.put("kafka_server_group_name", "hello");
        assertMetric(metrics, "kafka_server_group_name", 1.0, expectedLabels);
        assertEquals(1, metrics.size());

        Collector.MetricFamilySamples metricFamilySamples = metrics.get(0);
        Collector.MetricFamilySamples.Sample serverGroupNameSamples = metricFamilySamples.samples.get(0);

        assertEquals("kafka_server_group_name", metricFamilySamples.name);
        assertEquals(1, metricFamilySamples.samples.size());
        assertEquals(1.0, serverGroupNameSamples.value, 0);
        assertTrue(serverGroupNameSamples.labelNames.contains("kafka_server_group_name"));
    }

    public void assertMetric(List<Collector.MetricFamilySamples> metrics, String expectedName, double expectedValue, Map<String, String> expectedLabels) {
        boolean metricFound = false;
        for (Collector.MetricFamilySamples metricFamilySamples : metrics) {
            if (metricFamilySamples.name.equals(expectedName)) {
                for (Collector.MetricFamilySamples.Sample sample : metricFamilySamples.samples) {
                    if (sample.value == expectedValue &&
                            sample.labelNames.equals(new ArrayList<>(expectedLabels.keySet())) &&
                            sample.labelValues.equals(new ArrayList<>(expectedLabels.values()))) {
                        metricFound = true;
                        break;
                    }
                }
            }
        }
        assertTrue(metricFound, "Expected metric not found: " + expectedName);
    }

    private KafkaMetric buildMetric(String name, String group, double value) {
        Measurable measurable = (config, now) -> value;
        return new KafkaMetric(
                new Object(),
                new MetricName(name, group, "", labels),
                measurable,
                metricConfig,
                time);
    }

    private KafkaMetric buildNonNumericMetric(String name, String group) {
        Gauge<String> measurable = (config, now) -> "hello";
        return new KafkaMetric(
                new Object(),
                new MetricName(name, group, "", labels),
                measurable,
                metricConfig,
                time);
    }

}