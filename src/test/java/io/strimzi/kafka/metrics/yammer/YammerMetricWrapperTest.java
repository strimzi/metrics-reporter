/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.yammer;

import com.yammer.metrics.core.Gauge;
import com.yammer.metrics.core.MetricName;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.PrometheusNaming;
import io.strimzi.kafka.metrics.MetricsUtils;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class YammerMetricWrapperTest {

    @Test
    public void testLabelsFromScope() {
        assertEquals(Labels.of("k1", "v1", "k2", "v2"), YammerMetricWrapper.labelsFromScope("k1.v1.k2.v2", "name"));
        assertEquals(Labels.EMPTY, YammerMetricWrapper.labelsFromScope(null, "name"));
        assertEquals(Labels.EMPTY, YammerMetricWrapper.labelsFromScope("k1", "name"));
        assertEquals(Labels.EMPTY, YammerMetricWrapper.labelsFromScope("k1.", "name"));
        assertEquals(Labels.EMPTY, YammerMetricWrapper.labelsFromScope("k1.v1.k", "name"));

        Labels labels = YammerMetricWrapper.labelsFromScope("k-1.v1.k_1.v2", "name");
        assertEquals("k_1", PrometheusNaming.sanitizeLabelName("k-1"));
        assertEquals("v1", labels.get("k_1"));
        assertEquals(1, labels.size());
    }

    @Test
    public void testYammerMetricName() {
        String metricName = YammerMetricWrapper.prometheusName(new MetricName("Kafka.Server", "Log", "NumLogSegments"));
        assertEquals("kafka_server_kafka_server_log_numlogsegments", metricName);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testYammerMetric() {
        AtomicInteger value = new AtomicInteger(0);
        MetricName name = new MetricName("group", "type", "name");
        Gauge<Integer> metric = MetricsUtils.newYammerMetric(value::get);
        YammerMetricWrapper wrapper = new YammerMetricWrapper(YammerMetricWrapper.prometheusName(name), "", metric, "name");
        assertEquals(value.get(), ((Gauge<Integer>) wrapper.metric()).value());
        value.incrementAndGet();
        assertEquals(value.get(), ((Gauge<Integer>) wrapper.metric()).value());
    }
}
