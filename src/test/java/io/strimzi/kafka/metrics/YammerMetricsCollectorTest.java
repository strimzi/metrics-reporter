/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Counter;
import com.yammer.metrics.core.Gauge;
import com.yammer.metrics.core.Metric;
import com.yammer.metrics.core.MetricName;
import com.yammer.metrics.core.MetricsRegistry;
import io.prometheus.metrics.model.snapshots.CounterSnapshot;
import io.prometheus.metrics.model.snapshots.InfoSnapshot;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class YammerMetricsCollectorTest {

    private final MetricsRegistry registry = Metrics.defaultRegistry();
    private String scope;
    private Labels labels;

    @BeforeEach
    public void setup() {
        Labels.Builder labelsBuilder = Labels.builder();
        scope = "";
        for (int i = 0; i < 2; i++) {
            labelsBuilder.label("k" + i, "v" + i);
            scope += "k" + i + ".v" + i + ".";
        }
        labels = labelsBuilder.build();
        for (Map.Entry<MetricName, Metric> entry : registry.allMetrics().entrySet()) {
            registry.removeMetric(entry.getKey());
        }
    }

    @Test
    public void testCollect() {
        YammerMetricsCollector collector = new YammerMetricsCollector();

        MetricSnapshots metrics = collector.collect();
        assertEquals(0, metrics.size());

        // Add a metric
        MetricName metricName = new MetricName("group", "type", "name", scope);
        MetricWrapper metricWrapper = newMetric(metricName);
        collector.addMetric(metricName, metricWrapper);
        metrics = collector.collect();
        assertEquals(1, metrics.size());

        MetricSnapshot snapshot = metrics.get(0);
        assertEquals(metricWrapper.prometheusName(), snapshot.getMetadata().getName());
        assertInstanceOf(CounterSnapshot.class, snapshot);
        CounterSnapshot counterSnapshot = (CounterSnapshot) snapshot;

        assertEquals(1, counterSnapshot.getDataPoints().size());
        CounterSnapshot.CounterDataPointSnapshot datapoint = counterSnapshot.getDataPoints().get(0);
        assertEquals(0.0, datapoint.getValue(), 0.1);
        assertEquals(labels, datapoint.getLabels());

        // Update the value of the metric
        ((Counter) metricWrapper.metric()).inc(10);
        metrics = collector.collect();

        assertEquals(1, metrics.size());
        snapshot = metrics.get(0);
        assertEquals(metricWrapper.prometheusName(), snapshot.getMetadata().getName());
        assertInstanceOf(CounterSnapshot.class, snapshot);
        counterSnapshot = (CounterSnapshot) snapshot;
        assertEquals(1, counterSnapshot.getDataPoints().size());
        datapoint = counterSnapshot.getDataPoints().get(0);
        assertEquals(10.0, datapoint.getValue(), 0.1);

        // Remove the metric
        collector.removeMetric(metricName);
        metrics = collector.collect();
        assertEquals(0, metrics.size());
    }

    @Test
    public void testCollectNonNumericMetric() {
        YammerMetricsCollector collector = new YammerMetricsCollector();

        MetricSnapshots metrics = collector.collect();
        assertEquals(0, metrics.size());

        String nonNumericValue = "value";
        MetricName metricName = new MetricName("group", "type", "name", scope);
        MetricWrapper metricWrapper = newNonNumericMetric(metricName, nonNumericValue);
        collector.addMetric(metricName, metricWrapper);
        metrics = collector.collect();

        assertEquals(1, metrics.size());
        MetricSnapshot snapshot = metrics.get(0);
        assertEquals(metricWrapper.prometheusName(), snapshot.getMetadata().getName());
        assertInstanceOf(InfoSnapshot.class, snapshot);
        Labels expectedLabels = labels.add("name", nonNumericValue);
        assertEquals(1, snapshot.getDataPoints().size());
        assertEquals(expectedLabels, snapshot.getDataPoints().get(0).getLabels());
    }

    private MetricWrapper newMetric(MetricName metricName) {
        Counter counter = registry.newCounter(metricName);
        String prometheusName = MetricWrapper.prometheusName(metricName);
        return new MetricWrapper(prometheusName, metricName.getScope(), counter, metricName.getName());
    }

    private MetricWrapper newNonNumericMetric(MetricName metricName, String value) {
        Gauge<String> gauge = registry.newGauge(metricName, new Gauge<>() {
            @Override
            public String value() {
                return value;
            }
        });
        String prometheusName = MetricWrapper.prometheusName(metricName);
        return new MetricWrapper(prometheusName, metricName.getScope(), gauge, metricName.getName());
    }

}
