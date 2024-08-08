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

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MetricWrapperTest {

    @Test
    public void testLabelsFromScope() {
        MetricWrapper mw = new MetricWrapper("", "k1.v1.k2.v2", null, "");
        assertEquals(Labels.of("k1", "v1", "k2", "v2"), mw.labels());
        mw = new MetricWrapper("", "k1.v1.k2.v2.", null, "");
        assertEquals(Labels.of("k1", "v1", "k2", "v2"), mw.labels());
        mw = new MetricWrapper("", null, null, "");
        assertEquals(Labels.EMPTY, mw.labels());
        mw = new MetricWrapper("", "k1", null, "");
        assertEquals(Labels.EMPTY, mw.labels());
        mw = new MetricWrapper("", "k1.", null, "");
        assertEquals(Labels.EMPTY, mw.labels());
        mw = new MetricWrapper("", "k1.v1.k", null, "");
        assertEquals(Labels.EMPTY, mw.labels());

        mw = new MetricWrapper("", "k-1.v1.k_1.v2", null, "");
        Labels labels = mw.labels();
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
