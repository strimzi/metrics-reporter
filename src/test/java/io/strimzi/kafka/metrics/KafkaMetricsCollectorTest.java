/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import io.prometheus.metrics.model.snapshots.GaugeSnapshot;
import io.prometheus.metrics.model.snapshots.InfoSnapshot;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.metrics.Gauge;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.Measurable;
import org.apache.kafka.common.metrics.MetricConfig;
import org.apache.kafka.common.utils.Time;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class KafkaMetricsCollectorTest {

    private static final String METRIC_PREFIX = "kafka.server";
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
    public void testCollect() {
        KafkaMetricsCollector collector = new KafkaMetricsCollector();

        MetricSnapshots metrics = collector.collect();
        assertEquals(0, metrics.size());

        // Add a metric
        MetricName metricName = new MetricName("name", "group", "description", tagsMap);
        MetricWrapper metricWrapper = newMetric(metricName, 1.0);

        collector.addMetric(metricName, metricWrapper);
        metrics = collector.collect();
        assertEquals(1, metrics.size());

        MetricSnapshot snapshot = metrics.get(0);
        assertGaugeSnapshot(snapshot, 1.0, labels);

        // Update the value of the metric
        collector.addMetric(metricName, newMetric(metricName, 3.0));
        metrics = collector.collect();
        assertEquals(1, metrics.size());

        MetricSnapshot updatedSnapshot = metrics.get(0);
        assertGaugeSnapshot(updatedSnapshot, 3.0, labels);

        // Removing the metric
        collector.removeMetric(metricName);
        metrics = collector.collect();
        assertEquals(0, metrics.size());
    }

    @Test
    public void testCollectNonNumericMetric() {
        KafkaMetricsCollector collector = new KafkaMetricsCollector();

        MetricSnapshots metrics = collector.collect();
        assertEquals(0, metrics.size());

        // Adding a non-numeric metric converted
        String nonNumericValue = "myValue";
        MetricName metricName = new MetricName("name", "group", "description", tagsMap);
        MetricWrapper metricWrapper = newNonNumericMetric(metricName, nonNumericValue);
        collector.addMetric(metricName, metricWrapper);
        metrics = collector.collect();

        assertEquals(1, metrics.size());
        MetricSnapshot snapshot = metrics.get(0);
        assertInstanceOf(InfoSnapshot.class, snapshot);
        assertEquals(1, snapshot.getDataPoints().size());
        Labels expectedLabels = labels.add("name", nonNumericValue);
        assertEquals(expectedLabels, snapshot.getDataPoints().get(0).getLabels());
    }

    private void assertGaugeSnapshot(MetricSnapshot snapshot, double expectedValue, Labels expectedLabels) {
        assertInstanceOf(GaugeSnapshot.class, snapshot);
        GaugeSnapshot gaugeSnapshot = (GaugeSnapshot) snapshot;
        assertEquals(1, gaugeSnapshot.getDataPoints().size());
        GaugeSnapshot.GaugeDataPointSnapshot datapoint = gaugeSnapshot.getDataPoints().get(0);
        assertEquals(expectedValue, datapoint.getValue());
        assertEquals(expectedLabels, datapoint.getLabels());
    }

    private MetricWrapper newMetric(MetricName metricName, double value) {
        Measurable measurable = (config, now) -> value;
        KafkaMetric metric = new KafkaMetric(
                new Object(),
                new MetricName(metricName.name(), metricName.group(), "", tagsMap),
                measurable,
                metricConfig,
                time);
        String prometheusName = MetricWrapper.prometheusName(METRIC_PREFIX, metricName);
        return new MetricWrapper(prometheusName, metric, metricName.name());
    }

    private MetricWrapper newNonNumericMetric(MetricName metricName, String value) {
        Gauge<String> gauge = (config, now) -> value;
        KafkaMetric kafkaMetric = new KafkaMetric(
                new Object(),
                metricName,
                gauge,
                metricConfig,
                time);
        String prometheusName = MetricWrapper.prometheusName(METRIC_PREFIX, metricName);
        return new MetricWrapper(prometheusName, kafkaMetric, metricName.name());
    }

}
