/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import com.yammer.metrics.core.MetricName;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.PrometheusNaming;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

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
}
