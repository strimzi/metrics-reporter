/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import com.yammer.metrics.core.Counter;
import com.yammer.metrics.core.Gauge;
import com.yammer.metrics.core.MetricName;
import io.prometheus.client.Collector;
import org.apache.kafka.server.metrics.KafkaYammerMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class YammerMetricsCollectorTest {

    private LinkedHashMap<String, String> tags;

    @BeforeEach
    public void setup() {
        tags = new LinkedHashMap<>();
        tags.put("k1", "v1");
        tags.put("k2", "v2");
    }

    @Test
    public void testCollect() {
        Map<String, String> props = new HashMap<>();
        props.put(PrometheusMetricsReporterConfig.ALLOWLIST_CONFIG, "kafka_server_group_name.*");
        PrometheusMetricsReporterConfig config = new PrometheusMetricsReporterConfig(props);
        YammerMetricsCollector collector = new YammerMetricsCollector(config);

        List<Collector.MetricFamilySamples> metrics = collector.collect();
        assertTrue(metrics.isEmpty());

        // Adding a metric not matching the allowlist does nothing
        newCounter("other", "name", "type");
        metrics = collector.collect();
        assertTrue(metrics.isEmpty());

        // Adding a non-numeric metric does nothing
        newNonNumericGauge("group", "name2", "type");
        metrics = collector.collect();
        assertTrue(metrics.isEmpty());

        // Adding a metric that matches the allowlist
        Counter counter = newCounter("group", "name", "type");
        metrics = collector.collect();
        assertEquals(1, metrics.size());
        assertEquals("kafka_server_group_name_type_count", metrics.get(0).name);
        assertEquals(1, metrics.get(0).samples.size());
        assertEquals(0.0, metrics.get(0).samples.get(0).value, 0.1);
        assertEquals(new ArrayList<>(tags.keySet()), metrics.get(0).samples.get(0).labelNames);
        assertEquals(new ArrayList<>(tags.values()), metrics.get(0).samples.get(0).labelValues);

        // Updating the value of the metric
        counter.inc(10);
        metrics = collector.collect();
        assertEquals(1, metrics.size());
        assertEquals("kafka_server_group_name_type_count", metrics.get(0).name);
        assertEquals(1, metrics.get(0).samples.size());
        assertEquals(10.0, metrics.get(0).samples.get(0).value, 0.1);

        // Removing the metric
        removeMetric("group", "name", "type");
        metrics = collector.collect();
        assertTrue(metrics.isEmpty());
    }

    @Test
    public void testLabelsFromScope() {
        assertEquals(tags, YammerMetricsCollector.labelsFromScope("k1.v1.k2.v2"));
        assertEquals(Collections.emptyMap(), YammerMetricsCollector.labelsFromScope(null));
        assertEquals(Collections.emptyMap(), YammerMetricsCollector.labelsFromScope("k1"));
        assertEquals(Collections.emptyMap(), YammerMetricsCollector.labelsFromScope("k1."));
        assertEquals(Collections.emptyMap(), YammerMetricsCollector.labelsFromScope("k1.v1.k"));
    }

    public Counter newCounter(String group, String name, String type) {
        MetricName metricName = KafkaYammerMetrics.getMetricName(group, name, type, tags);
        return KafkaYammerMetrics.defaultRegistry().newCounter(metricName);
    }

    public void newNonNumericGauge(String group, String name, String type) {
        MetricName metricName = KafkaYammerMetrics.getMetricName(group, name, type, tags);
        KafkaYammerMetrics.defaultRegistry().newGauge(metricName, new Gauge<String>() {
            @Override
            public String value() {
                return "value";
            }
        });
    }

    public void removeMetric(String group, String name, String type) {
        MetricName metricName = KafkaYammerMetrics.getMetricName(group, name, type, tags);
        KafkaYammerMetrics.defaultRegistry().removeMetric(metricName);
    }

}
