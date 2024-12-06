/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.kafka;

import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.PrometheusNaming;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static io.strimzi.kafka.metrics.TestUtils.newKafkaMetric;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class KafkaMetricWrapperTest {

    @Test
    public void testKafkaMetricName() {
        String metricName = KafkaMetricWrapper.prometheusName("kafka_server", new MetricName("NaMe", "KafKa.neTwork", "", Collections.emptyMap()));
        assertEquals("kafka_server_kafka_network_name", metricName);
    }

    @Test
    public void testKafkaMetric() {
        AtomicInteger value = new AtomicInteger(0);
        KafkaMetric metric = newKafkaMetric("name", "group", (config, now) -> value.get(), Collections.emptyMap());
        KafkaMetricWrapper wrapper = new KafkaMetricWrapper(KafkaMetricWrapper.prometheusName("kafka_server", metric.metricName()), metric, "name");
        assertEquals(value.get(), ((KafkaMetric) wrapper.metric()).metricValue());
        value.incrementAndGet();
        assertEquals(value.get(), ((KafkaMetric) wrapper.metric()).metricValue());
    }

    @Test
    public void testLabelsFromTags() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("k1", "v1");
        tags.put("k2", "v2");
        Labels labels = KafkaMetricWrapper.labelsFromTags(tags, "");
        assertEquals(Labels.of("k1", "v1", "k2", "v2"), labels);

        tags = new LinkedHashMap<>();
        tags.put("k-1", "v1");
        tags.put("k_1", "v2");
        labels = KafkaMetricWrapper.labelsFromTags(tags, "");
        assertEquals("k_1", PrometheusNaming.sanitizeLabelName("k-1"));
        assertEquals("v1", labels.get("k_1"));
        assertEquals(1, labels.size());
    }
}
