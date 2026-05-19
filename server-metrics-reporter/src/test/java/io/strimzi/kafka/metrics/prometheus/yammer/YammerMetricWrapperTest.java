/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.prometheus.yammer;

import com.yammer.metrics.core.Gauge;
import com.yammer.metrics.core.MetricName;
import io.prometheus.metrics.model.snapshots.Labels;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static io.strimzi.kafka.metrics.prometheus.YammerTestUtils.newYammerMetric;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class YammerMetricWrapperTest {

    @Test
    public void testLabelsFromScopeAndMBeanName() {
        assertEquals(Labels.of("k1", "v1", "k2", "v2"), YammerMetricWrapper.labelsFromScopeAndMBeanName("k1.v1.k2.v2", "group:type=T,name=N,k1=v1,k2=v2", "name"));
        // Dots in topic names are preserved: scope determines which keys are labels, MBean name provides the original (unsanitized) values
        assertEquals(Labels.of("topic", "env.topicname.version"),
                YammerMetricWrapper.labelsFromScopeAndMBeanName(
                        "topic.env_topicname_version",
                        "kafka.server:type=BrokerTopicMetrics,name=MessagesInPerSec,topic=env.topicname.version",
                        "name"));
        // Hyphenated key in scope is sanitized for Prometheus
        assertEquals(Labels.of("client_id", "my-producer"),
                YammerMetricWrapper.labelsFromScopeAndMBeanName(
                        "client-id.my-producer",
                        "kafka.server:type=T,name=N,client-id=my-producer",
                        "name"));
        assertEquals(Labels.EMPTY, YammerMetricWrapper.labelsFromScopeAndMBeanName(null, "group:type=T,name=N,k1=v1", "name"));
        assertEquals(Labels.EMPTY, YammerMetricWrapper.labelsFromScopeAndMBeanName("", "group:type=T,name=N,k1=v1", "name"));
        assertEquals(Labels.EMPTY, YammerMetricWrapper.labelsFromScopeAndMBeanName("k1.v1", null, "name"));
    }

    @Test
    public void testYammerMetricName() {
        String metricName = YammerMetricWrapper.prometheusName(new MetricName("Kafka.Server", "Log", "NumLogSegments"));
        assertEquals("kafka_server_log_numlogsegments", metricName);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testYammerMetric() {
        AtomicInteger value = new AtomicInteger(0);
        MetricName name = new MetricName("group", "type", "name");
        Gauge<Integer> metric = newYammerMetric(value::get);
        YammerMetricWrapper wrapper = new YammerMetricWrapper(YammerMetricWrapper.prometheusName(name), name, metric, "name");
        assertEquals(value.get(), ((Gauge<Integer>) wrapper.metric()).value());
        value.incrementAndGet();
        assertEquals(value.get(), ((Gauge<Integer>) wrapper.metric()).value());
    }
}
