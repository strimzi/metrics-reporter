/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.prometheus.yammer;

import com.yammer.metrics.core.Gauge;
import com.yammer.metrics.core.MetricName;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;
import io.strimzi.kafka.metrics.prometheus.common.AbstractReporter;
import io.strimzi.kafka.metrics.prometheus.common.MetricWrapper;
import io.strimzi.kafka.metrics.prometheus.common.PrometheusCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static io.strimzi.kafka.metrics.prometheus.MetricsUtils.assertGaugeSnapshot;
import static io.strimzi.kafka.metrics.prometheus.MetricsUtils.assertInfoSnapshot;
import static io.strimzi.kafka.metrics.prometheus.YammerTestUtils.newYammerMetric;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class YammerCollectorTest {

    private Labels labels;
    private String scope;
    private String mbeanLabels;
    private YammerCollector collector;

    @BeforeEach
    public void setup() {
        Labels.Builder labelsBuilder = Labels.builder();
        scope = "";
        StringBuilder mbeanBuilder = new StringBuilder();
        for (int i = 0; i < 2; i++) {
            labelsBuilder.label("k" + i, "v" + i);
            scope += "k" + i + ".v" + i + ".";
            mbeanBuilder.append(",k").append(i).append("=v").append(i);
        }
        labels = labelsBuilder.build();
        mbeanLabels = mbeanBuilder.toString();
        collector = new YammerCollector(new PrometheusCollector());
    }

    private MetricName createMetricName(String name) {
        String mBeanName = "group:type=type,name=" + name + mbeanLabels;
        return new MetricName("group", "type", name, scope, mBeanName);
    }

    @Test
    public void testCollectYammerMetrics() {
        AbstractReporter reporter = new AbstractReporter() {
            @Override
            protected Pattern allowlist() {
                return Pattern.compile(".*");
            }
        };
        collector.addReporter(reporter);

        List<? extends MetricSnapshot> metrics = collector.collect();
        assertEquals(0, metrics.size());

        // Adding a metric
        AtomicInteger value = new AtomicInteger(1);
        MetricName metricName = createMetricName("name");
        MetricWrapper metricWrapper = newYammerMetricWrapper(metricName, value::get);
        reporter.addMetric(metricName, metricWrapper);

        metrics = collector.collect();
        assertEquals(1, metrics.size());
        assertGaugeSnapshot(metrics.get(0), value.get(), labels);

        // Updating the value of the metric
        value.set(3);
        metrics = collector.collect();
        assertEquals(1, metrics.size());
        assertGaugeSnapshot(metrics.get(0), 3, labels);

        // Removing the metric
        reporter.removeMetric(metricName);
        metrics = collector.collect();
        assertEquals(0, metrics.size());
    }

    @Test
    public void testCollectNonNumericYammerMetrics() {
        AbstractReporter reporter = new AbstractReporter() {
            @Override
            protected Pattern allowlist() {
                return Pattern.compile(".*");
            }
        };
        collector.addReporter(reporter);

        List<? extends MetricSnapshot> metrics = collector.collect();
        assertEquals(0, metrics.size());

        String nonNumericValue = "value";
        MetricName metricName = createMetricName("name");
        MetricWrapper metricWrapper = newYammerMetricWrapper(metricName, () -> nonNumericValue);
        reporter.addMetric(metricName, metricWrapper);
        metrics = collector.collect();

        assertEquals(1, metrics.size());
        MetricSnapshot snapshot = metrics.get(0);
        assertEquals(metricWrapper.prometheusName(), snapshot.getMetadata().getName());
        assertInfoSnapshot(snapshot, labels, "name", nonNumericValue);
    }

    @Test
    public void testHelpMessage() {
        AbstractReporter reporter = new AbstractReporter() {
            @Override
            protected Pattern allowlist() {
                return Pattern.compile(".*");
            }
        };
        collector.addReporter(reporter);

        // Test numeric metric
        MetricName numericMetricName = createMetricName("testMetric");
        MetricWrapper numericMetricWrapper = newYammerMetricWrapper(numericMetricName, () -> 1);
        reporter.addMetric(numericMetricName, numericMetricWrapper);

        List<? extends MetricSnapshot> metrics = collector.collect();
        assertEquals(1, metrics.size());

        String expectedHelpMessage = "Use " + numericMetricWrapper.prometheusName() + " in allowlist";
        assertEquals(expectedHelpMessage, metrics.get(0).getMetadata().getHelp());

        reporter.removeMetric(numericMetricName);

        // Test non-numeric metric
        MetricName stringMetricName = createMetricName("stringMetric");
        MetricWrapper stringMetricWrapper = newYammerMetricWrapper(stringMetricName, () -> "testValue");
        reporter.addMetric(stringMetricName, stringMetricWrapper);

        metrics = collector.collect();
        assertEquals(1, metrics.size());

        expectedHelpMessage = "Use " + stringMetricWrapper.prometheusName() + " in allowlist";
        assertEquals(expectedHelpMessage, metrics.get(0).getMetadata().getHelp());
    }

    private <T> MetricWrapper newYammerMetricWrapper(MetricName metricName, Supplier<T> valueSupplier) {
        Gauge<T> gauge = newYammerMetric(valueSupplier);
        String prometheusName = YammerMetricWrapper.prometheusName(metricName);
        return new YammerMetricWrapper(prometheusName, metricName, gauge, metricName.getName());
    }
}
