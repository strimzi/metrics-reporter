/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.yammer;

import com.yammer.metrics.core.Gauge;
import com.yammer.metrics.core.MetricName;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;
import io.strimzi.kafka.metrics.MetricWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static io.strimzi.kafka.metrics.MetricsUtils.assertGaugeSnapshot;
import static io.strimzi.kafka.metrics.MetricsUtils.assertInfoSnapshot;
import static io.strimzi.kafka.metrics.MetricsUtils.newYammerMetric;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class YammerCollectorTest {

    private Labels labels;
    private String scope;

    @BeforeEach
    public void setup() {
        Labels.Builder labelsBuilder = Labels.builder();
        scope = "";
        for (int i = 0; i < 2; i++) {
            labelsBuilder.label("k" + i, "v" + i);
            scope += "k" + i + ".v" + i + ".";
        }
        labels = labelsBuilder.build();
    }

    @Test
    public void testCollectYammerMetrics() {
        YammerCollector collector = new YammerCollector();

        List<? extends MetricSnapshot<?>> metrics = collector.collect();
        assertEquals(0, metrics.size());

        // Adding a metric
        AtomicInteger value = new AtomicInteger(1);
        MetricName metricName = new MetricName("group", "type", "name", scope);
        MetricWrapper metricWrapper = newYammerMetricWrapper(metricName, value::get);
        collector.addMetric(metricName, metricWrapper);

        metrics = collector.collect();
        assertEquals(1, metrics.size());
        assertGaugeSnapshot(metrics.get(0), value.get(), labels);

        // Updating the value of the metric
        value.set(3);
        metrics = collector.collect();
        assertEquals(1, metrics.size());
        assertGaugeSnapshot(metrics.get(0), 3, labels);

        // Removing the metric
        collector.removeMetric(metricName);
        metrics = collector.collect();
        assertEquals(0, metrics.size());
    }

    @Test
    public void testCollectNonNumericYammerMetrics() {
        YammerCollector collector = new YammerCollector();

        List<? extends MetricSnapshot<?>> metrics = collector.collect();
        assertEquals(0, metrics.size());

        String nonNumericValue = "value";
        MetricName metricName = new MetricName("group", "type", "name", scope);
        MetricWrapper metricWrapper = newYammerMetricWrapper(metricName, () -> nonNumericValue);
        collector.addMetric(metricName, metricWrapper);
        metrics = collector.collect();

        assertEquals(1, metrics.size());
        MetricSnapshot<?> snapshot = metrics.get(0);
        assertEquals(metricWrapper.prometheusName(), snapshot.getMetadata().getName());
        assertInfoSnapshot(snapshot, labels, "name", nonNumericValue);
    }

    private <T> MetricWrapper newYammerMetricWrapper(MetricName metricName, Supplier<T> valueSupplier) {
        Gauge<T> gauge = newYammerMetric(valueSupplier);
        String prometheusName = YammerMetricWrapper.prometheusName(metricName);
        return new YammerMetricWrapper(prometheusName, metricName.getScope(), gauge, metricName.getName());
    }
}
