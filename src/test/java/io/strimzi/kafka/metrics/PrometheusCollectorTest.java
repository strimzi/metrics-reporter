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
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.metrics.Gauge;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static io.strimzi.kafka.metrics.MetricsUtils.newKafkaMetric;
import static io.strimzi.kafka.metrics.MetricsUtils.newYammerMetric;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("ClassFanOutComplexity")
public class PrometheusCollectorTest {

    private static final String METRIC_PREFIX = "kafka.server";
    private Map<String, String> tagsMap;
    private Labels labels;
    private String scope;

    @BeforeEach
    public void setup() {
        tagsMap = new LinkedHashMap<>();
        Labels.Builder labelsBuilder = Labels.builder();
        scope = "";
        for (int i = 0; i < 2; i++) {
            labelsBuilder.label("k" + i, "v" + i);
            tagsMap.put("k" + i, "v" + i);
            scope += "k" + i + ".v" + i + ".";
        }
        labels = labelsBuilder.build();
    }

    @Test
    public void testRegister() {
        PrometheusRegistry registry = new PrometheusRegistry();
        PrometheusCollector pc1 = PrometheusCollector.register(registry);
        PrometheusCollector pc2 = PrometheusCollector.register(registry);
        assertSame(pc1, pc2);
        assertTrue(registry.scrape().size() > 0);
    }

    @Test
    public void testCollectKafkaMetrics() {
        PrometheusCollector collector = new PrometheusCollector();

        MetricSnapshots metrics = collector.collect();
        assertEquals(0, metrics.size());

        // Adding a metric
        AtomicInteger value = new AtomicInteger(1);
        MetricName metricName = new MetricName("name", "group", "description", tagsMap);
        MetricWrapper metricWrapper = newKafkaMetricWrapper(metricName, (config, now) -> value.get());
        collector.addKafkaMetric(metricName, metricWrapper);

        metrics = collector.collect();
        assertEquals(1, metrics.size());
        MetricSnapshot snapshot = metrics.get(0);
        assertGaugeSnapshot(snapshot, value.get(), labels);

        // Updating the value of the metric
        value.set(3);
        metrics = collector.collect();
        assertEquals(1, metrics.size());
        MetricSnapshot updatedSnapshot = metrics.get(0);
        assertGaugeSnapshot(updatedSnapshot, 3, labels);

        // Removing a metric
        collector.removeKafkaMetric(metricName);
        metrics = collector.collect();
        assertEquals(0, metrics.size());
    }

    @Test
    public void testCollectNonNumericKafkaMetric() {
        PrometheusCollector collector = new PrometheusCollector();

        MetricSnapshots metrics = collector.collect();
        assertEquals(0, metrics.size());

        // Adding a non-numeric metric converted
        String nonNumericValue = "myValue";
        MetricName metricName = new MetricName("name", "group", "description", tagsMap);
        MetricWrapper metricWrapper = newKafkaMetricWrapper(metricName, (config, now) -> nonNumericValue);
        collector.addKafkaMetric(metricName, metricWrapper);
        metrics = collector.collect();

        assertEquals(1, metrics.size());
        MetricSnapshot snapshot = metrics.get(0);
        assertEquals(metricWrapper.prometheusName(), snapshot.getMetadata().getName());
        assertInfoSnapshot(snapshot, "name", nonNumericValue);
    }

    @Test
    public void testCollectYammerMetrics() {
        PrometheusCollector collector = new PrometheusCollector();

        MetricSnapshots metrics = collector.collect();
        assertEquals(0, metrics.size());

        // Adding a metric
        AtomicInteger value = new AtomicInteger(1);
        com.yammer.metrics.core.MetricName metricName = new com.yammer.metrics.core.MetricName("group", "type", "name", scope);
        MetricWrapper metricWrapper = newYammerMetricWrapper(metricName, value::get);
        collector.addYammerMetric(metricName, metricWrapper);

        metrics = collector.collect();
        assertEquals(1, metrics.size());
        MetricSnapshot snapshot = metrics.get(0);
        assertGaugeSnapshot(snapshot, value.get(), labels);

        // Updating the value of the metric
        value.set(3);
        metrics = collector.collect();
        assertEquals(1, metrics.size());
        MetricSnapshot updatedSnapshot = metrics.get(0);
        assertGaugeSnapshot(updatedSnapshot, 3, labels);

        // Removing the metric
        collector.removeYammerMetric(metricName);
        metrics = collector.collect();
        assertEquals(0, metrics.size());
    }

    @Test
    public void testCollectNonNumericYammerMetrics() {
        PrometheusCollector collector = new PrometheusCollector();

        MetricSnapshots metrics = collector.collect();
        assertEquals(0, metrics.size());

        String nonNumericValue = "value";
        com.yammer.metrics.core.MetricName metricName = new com.yammer.metrics.core.MetricName("group", "type", "name", scope);
        MetricWrapper metricWrapper = newYammerMetricWrapper(metricName, () -> nonNumericValue);
        collector.addYammerMetric(metricName, metricWrapper);
        metrics = collector.collect();

        assertEquals(1, metrics.size());
        MetricSnapshot snapshot = metrics.get(0);
        assertEquals(metricWrapper.prometheusName(), snapshot.getMetadata().getName());
        assertInfoSnapshot(snapshot, "name", nonNumericValue);
    }

    private <T> MetricWrapper newYammerMetricWrapper(com.yammer.metrics.core.MetricName metricName, Supplier<T> valueSupplier) {
        com.yammer.metrics.core.Gauge<T> gauge = newYammerMetric(valueSupplier);
        String prometheusName = MetricWrapper.prometheusName(metricName);
        return new MetricWrapper(prometheusName, metricName.getScope(), gauge, metricName.getName());
    }

    private MetricWrapper newKafkaMetricWrapper(MetricName metricName, Gauge<?> gauge) {
        KafkaMetric kafkaMetric = newKafkaMetric(metricName.name(), metricName.group(), gauge, metricName.tags());
        String prometheusName = MetricWrapper.prometheusName(METRIC_PREFIX, metricName);
        return new MetricWrapper(prometheusName, kafkaMetric, metricName.name());
    }

    private void assertGaugeSnapshot(MetricSnapshot snapshot, double expectedValue, Labels expectedLabels) {
        assertInstanceOf(GaugeSnapshot.class, snapshot);
        GaugeSnapshot gaugeSnapshot = (GaugeSnapshot) snapshot;
        assertEquals(1, gaugeSnapshot.getDataPoints().size());
        GaugeSnapshot.GaugeDataPointSnapshot datapoint = gaugeSnapshot.getDataPoints().get(0);
        assertEquals(expectedValue, datapoint.getValue());
        assertEquals(expectedLabels, datapoint.getLabels());
    }

    private void assertInfoSnapshot(MetricSnapshot snapshot, String newLabelName, String newLabelValue) {
        assertInstanceOf(InfoSnapshot.class, snapshot);
        assertEquals(1, snapshot.getDataPoints().size());
        Labels expectedLabels = labels.add(newLabelName, newLabelValue);
        assertEquals(expectedLabels, snapshot.getDataPoints().get(0).getLabels());
    }

}
