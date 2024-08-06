/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot;
import io.prometheus.metrics.model.snapshots.InfoSnapshot;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;
import io.prometheus.metrics.model.snapshots.PrometheusNaming;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.metrics.Gauge;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.Measurable;
import org.apache.kafka.common.metrics.MetricConfig;
import org.apache.kafka.common.utils.Time;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class KafkaMetricsCollectorTest {

    private final MetricConfig metricConfig = new MetricConfig();
    private final Time time = Time.SYSTEM;
    private Map<String, String> tagsMap;
    private Labels labels;

    @BeforeEach
    public void setup() {
        tagsMap = new LinkedHashMap<>();
        tagsMap.put("k1", "v1");
        tagsMap.put("k2", "v2");
        Labels.Builder labelsBuilder = Labels.builder();
        for (Map.Entry<String, String> tag : tagsMap.entrySet()) {
            labelsBuilder.label(tag.getKey(), tag.getValue());
        }
        labels = labelsBuilder.build();
    }

    @Test
    public void testMetricLifecycle() {
        Map<String, String> props = new HashMap<>();
        props.put(PrometheusMetricsReporterConfig.ALLOWLIST_CONFIG, "kafka_server_group_name.*");
        PrometheusMetricsReporterConfig config = new PrometheusMetricsReporterConfig(props, new PrometheusRegistry());
        KafkaMetricsCollector collector = new KafkaMetricsCollector(config);
        collector.setPrefix("kafka.server");

        MetricSnapshots metrics = collector.collect();
        assertEquals(0, metrics.size());

        // Adding a metric not matching the allowlist does nothing
        collector.addMetric(buildMetric("name", "other", 2.0));
        metrics = collector.collect();
        assertEquals(0, metrics.size());

        // Adding a metric that matches the allowlist
        collector.addMetric(buildMetric("name", "group", 1.0));
        metrics = collector.collect();
        assertEquals(1, metrics.size());

        MetricSnapshot snapshot = metrics.get(0);
        assertGaugeSnapshot(snapshot, 1.0, labels);

        // Adding the same metric updates its value
        collector.addMetric(buildMetric("name", "group", 3.0));
        metrics = collector.collect();
        assertEquals(1, metrics.size());

        MetricSnapshot updatedSnapshot = metrics.get(0);
        assertGaugeSnapshot(updatedSnapshot, 3.0, labels);

        // Removing the metric
        collector.removeMetric(buildMetric("name", "group", 4.0));
        metrics = collector.collect();
        assertEquals(0, metrics.size());
    }

    @Test
    public void testCollectNonNumericMetric() {
        Map<String, String> props = new HashMap<>();
        props.put(PrometheusMetricsReporterConfig.ALLOWLIST_CONFIG, "kafka_server_group_name.*");
        PrometheusMetricsReporterConfig config = new PrometheusMetricsReporterConfig(props, new PrometheusRegistry());
        KafkaMetricsCollector collector = new KafkaMetricsCollector(config);
        collector.setPrefix("kafka.server");

        MetricSnapshots metrics = collector.collect();
        assertEquals(0, metrics.size());

        // Adding a non-numeric metric converted
        String nonNumericValue = "myValue";
        KafkaMetric nonNumericMetric = buildNonNumericMetric("name", "group", nonNumericValue);
        collector.addMetric(nonNumericMetric);
        metrics = collector.collect();

        assertEquals(1, metrics.size());
        MetricSnapshot snapshot = metrics.get(0);
        assertInstanceOf(InfoSnapshot.class, snapshot);
        assertEquals(1, snapshot.getDataPoints().size());
        Labels expectedLabels = labels.add("name", nonNumericValue);
        assertEquals(expectedLabels, snapshot.getDataPoints().get(0).getLabels());
    }

    @Test
    public void testLabelsFromTags() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("k-1", "v1");
        tags.put("k_1", "v2");

        Labels labels = KafkaMetricsCollector.labelsFromTags(tags, "name");

        assertEquals("k_1", PrometheusNaming.sanitizeLabelName("k-1"));
        assertEquals("v1", labels.get("k_1"));
        assertEquals(1, labels.size());
    }

    @Test
    public void testMetricName() {
        PrometheusMetricsReporterConfig config = new PrometheusMetricsReporterConfig(Collections.emptyMap(), new PrometheusRegistry());
        KafkaMetricsCollector collector = new KafkaMetricsCollector(config);
        collector.setPrefix("kafka.server");

        String metricName = collector.metricName(new MetricName("NaMe", "KafKa.neTwork", "", Collections.emptyMap()));
        assertEquals("kafka_server_kafka_network_name", metricName);
    }

    private void assertGaugeSnapshot(MetricSnapshot snapshot, double expectedValue, Labels expectedLabels) {
        assertInstanceOf(GaugeSnapshot.class, snapshot);
        GaugeSnapshot gaugeSnapshot = (GaugeSnapshot) snapshot;
        assertEquals(1, gaugeSnapshot.getDataPoints().size());
        GaugeSnapshot.GaugeDataPointSnapshot datapoint = gaugeSnapshot.getDataPoints().get(0);
        assertEquals(expectedValue, datapoint.getValue());
        assertEquals(expectedLabels, datapoint.getLabels());
    }

    private KafkaMetric buildMetric(String name, String group, double value) {
        Measurable measurable = (config, now) -> value;
        return new KafkaMetric(
                new Object(),
                new MetricName(name, group, "", tagsMap),
                measurable,
                metricConfig,
                time);
    }

    private KafkaMetric buildNonNumericMetric(String name, String group, String value) {
        Gauge<String> measurable = (config, now) -> value;
        return new KafkaMetric(
                new Object(),
                new MetricName(name, group, "", tagsMap),
                measurable,
                metricConfig,
                time);
    }

}
