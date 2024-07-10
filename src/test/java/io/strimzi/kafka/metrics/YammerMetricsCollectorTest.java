/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import com.yammer.metrics.core.Counter;
import com.yammer.metrics.core.Gauge;
import com.yammer.metrics.core.MetricName;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.CounterSnapshot;
import io.prometheus.metrics.model.snapshots.InfoSnapshot;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;
import io.prometheus.metrics.model.snapshots.PrometheusNaming;
import org.apache.kafka.server.metrics.KafkaYammerMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class YammerMetricsCollectorTest {

    private LinkedHashMap<String, String> tagsMap;
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
    public void testMetricLifeCycle() {
        Map<String, String> props = new HashMap<>();
        props.put(PrometheusMetricsReporterConfig.ALLOWLIST_CONFIG, "kafka_server_group_type.*");
        PrometheusMetricsReporterConfig config = new PrometheusMetricsReporterConfig(props, new PrometheusRegistry());
        YammerMetricsCollector collector = new YammerMetricsCollector(config);

        MetricSnapshots metrics = collector.collect();
        assertEquals(0, metrics.size());

        // Adding a metric not matching the allowlist does nothing
        newCounter("other", "type", "name");
        metrics = collector.collect();
        assertEquals(0, metrics.size());

        // Adding a metric that matches the allowlist
        Counter counter = newCounter("group", "type", "name");
        metrics = collector.collect();
        assertEquals(1, metrics.size());

        MetricSnapshot snapshot = metrics.get(0);

        //assertEquals("kafka_server_group_name_type_count", snapshot.getMetadata().getName());
        assertInstanceOf(CounterSnapshot.class, snapshot);
        CounterSnapshot counterSnapshot = (CounterSnapshot) snapshot;

        assertEquals(1, counterSnapshot.getDataPoints().size());
        CounterSnapshot.CounterDataPointSnapshot datapoint = counterSnapshot.getDataPoints().get(0);
        assertEquals(0.0, datapoint.getValue(), 0.1);
        assertEquals(labels, datapoint.getLabels());

        // Updating the value of the metric
        counter.inc(10);
        metrics = collector.collect();

        assertEquals(1, metrics.size());
        snapshot = metrics.get(0);
        //assertEquals("kafka_server_group_name_type_count", snapshot.getMetadata().getName());
        assertInstanceOf(CounterSnapshot.class, snapshot);
        counterSnapshot = (CounterSnapshot) snapshot;
        assertEquals(1, counterSnapshot.getDataPoints().size());
        datapoint = counterSnapshot.getDataPoints().get(0);
        assertEquals(10.0, datapoint.getValue(), 0.1);

        // Removing the metric
        removeMetric("group", "type", "name");
        metrics = collector.collect();
        assertEquals(0, metrics.size());
    }

    @Test
    public void testCollectNonNumericMetric() {
        Map<String, String> props = new HashMap<>();
        props.put(PrometheusMetricsReporterConfig.ALLOWLIST_CONFIG, "kafka_server_group_type.*");
        PrometheusMetricsReporterConfig config = new PrometheusMetricsReporterConfig(props, new PrometheusRegistry());
        YammerMetricsCollector collector = new YammerMetricsCollector(config);

        MetricSnapshots metrics = collector.collect();
        assertEquals(0, metrics.size());

        String nonNumericValue = "value";
        newNonNumericGauge("group", "type", "name", nonNumericValue);
        metrics = collector.collect();

        assertEquals(1, metrics.size());
        MetricSnapshot snapshot = metrics.get(0);
        assertInstanceOf(InfoSnapshot.class, snapshot);
        Labels expectedLabels = labels.add("name", nonNumericValue);
        assertEquals(1, snapshot.getDataPoints().size());
        assertEquals(expectedLabels, snapshot.getDataPoints().get(0).getLabels());
    }

    @Test
    public void testLabelsFromScope() {
        assertEquals(Labels.of("k1", "v1", "k2", "v2"), YammerMetricsCollector.labelsFromScope("k1.v1.k2.v2"));
        assertEquals(Labels.EMPTY, YammerMetricsCollector.labelsFromScope(null));
        assertEquals(Labels.EMPTY, YammerMetricsCollector.labelsFromScope("k1"));
        assertEquals(Labels.EMPTY, YammerMetricsCollector.labelsFromScope("k1."));
        assertEquals(Labels.EMPTY, YammerMetricsCollector.labelsFromScope("k1.v1.k"));

        Labels labels = YammerMetricsCollector.labelsFromScope("k-1.v1.k_1.v2");
        assertEquals("k_1", PrometheusNaming.sanitizeLabelName("k-1"));
        assertEquals("v1", labels.get("k_1"));
        assertEquals(1, labels.size());
    }

    public Counter newCounter(String group, String type, String name) {
        MetricName metricName = KafkaYammerMetrics.getMetricName(group, type, name, tagsMap);
        return KafkaYammerMetrics.defaultRegistry().newCounter(metricName);
    }

    public void newNonNumericGauge(String group, String type, String name, String value) {
        MetricName metricName = KafkaYammerMetrics.getMetricName(group, type, name, tagsMap);
        KafkaYammerMetrics.defaultRegistry().newGauge(metricName, new Gauge<String>() {
            @Override
            public String value() {
                return value;
            }
        });
    }

    public void removeMetric(String group, String type, String name) {
        MetricName metricName = KafkaYammerMetrics.getMetricName(group, type, name, tagsMap);
        KafkaYammerMetrics.defaultRegistry().removeMetric(metricName);
    }

}
