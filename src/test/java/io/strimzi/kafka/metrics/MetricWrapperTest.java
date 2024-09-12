/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Counter;
import com.yammer.metrics.core.MetricName;
import com.yammer.metrics.core.MetricsRegistry;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.PrometheusNaming;
import org.apache.kafka.common.metrics.Gauge;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.MetricConfig;
import org.apache.kafka.common.utils.Time;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MetricWrapperTest {

    @Test
    public void testLabelsFromScope() {
        assertEquals(Labels.of("k1", "v1", "k2", "v2"), MetricWrapper.labelsFromScope("k1.v1.k2.v2", "name"));
        assertEquals(Labels.EMPTY, MetricWrapper.labelsFromScope(null, "name"));
        assertEquals(Labels.EMPTY, MetricWrapper.labelsFromScope("k1", "name"));
        assertEquals(Labels.EMPTY, MetricWrapper.labelsFromScope("k1.", "name"));
        assertEquals(Labels.EMPTY, MetricWrapper.labelsFromScope("k1.v1.k", "name"));

        Labels labels = MetricWrapper.labelsFromScope("k-1.v1.k_1.v2", "name");
        assertEquals("k_1", PrometheusNaming.sanitizeLabelName("k-1"));
        assertEquals("v1", labels.get("k_1"));
        assertEquals(1, labels.size());
    }

    @Test
    public void testLabelsFromTags() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("k1", "v1");
        tags.put("k2", "v2");
        Labels labels = MetricWrapper.labelsFromTags(tags, "");
        assertEquals(Labels.of("k1", "v1", "k2", "v2"), labels);

        tags = new LinkedHashMap<>();
        tags.put("k-1", "v1");
        tags.put("k_1", "v2");
        labels = MetricWrapper.labelsFromTags(tags, "");
        assertEquals("k_1", PrometheusNaming.sanitizeLabelName("k-1"));
        assertEquals("v1", labels.get("k_1"));
        assertEquals(1, labels.size());
    }

    @Test
    public void testYammerMetricName() {
        String metricName = MetricWrapper.prometheusName(new MetricName("Kafka.Server", "Log", "NumLogSegments"));
        assertEquals("kafka_server_kafka_server_log_numlogsegments", metricName);
    }

    @Test
    public void testKafkaMetricName() {
        String metricName = MetricWrapper.prometheusName("kafka_server", new org.apache.kafka.common.MetricName("NaMe", "KafKa.neTwork", "", Collections.emptyMap()));
        assertEquals("kafka_server_kafka_network_name", metricName);
    }

    @Test
    public void testKafkaMetric() {
        AtomicInteger value = new AtomicInteger(0);
        org.apache.kafka.common.MetricName name = new org.apache.kafka.common.MetricName("name", "kafka.server", "", Collections.emptyMap());
        KafkaMetric metric = new KafkaMetric(new Object(), name, (Gauge<Integer>) (metricConfig, l) -> value.get(), new MetricConfig(), Time.SYSTEM);
        String prometheusName = MetricWrapper.prometheusName("kafka_server", name);
        MetricWrapper wrapper = new MetricWrapper(prometheusName, metric, "name");
        assertEquals(value.get(), ((KafkaMetric) wrapper.metric()).metricValue());
        value.incrementAndGet();
        assertEquals(value.get(), ((KafkaMetric) wrapper.metric()).metricValue());
    }

    @Test
    public void testYammerMetric() {
        MetricName name = new MetricName("group", "type", "name");
        MetricsRegistry registry = Metrics.defaultRegistry();
        Counter counter = registry.newCounter(name);
        String prometheusName = MetricWrapper.prometheusName(name);
        MetricWrapper wrapper = new MetricWrapper(prometheusName, "", counter, "name");
        assertEquals(counter.count(), ((Counter) wrapper.metric()).count());
        counter.inc();
        assertEquals(counter.count(), ((Counter) wrapper.metric()).count());
    }
}
