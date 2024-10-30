/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.collector;

import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.CounterSnapshot;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot;
import io.prometheus.metrics.model.snapshots.InfoSnapshot;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;
import io.prometheus.metrics.model.snapshots.Quantile;
import io.prometheus.metrics.model.snapshots.Quantiles;
import io.prometheus.metrics.model.snapshots.SummarySnapshot;
import io.strimzi.kafka.metrics.MetricsUtils;
import io.strimzi.kafka.metrics.collector.kafka.KafkaMetricWrapper;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static io.strimzi.kafka.metrics.MetricsUtils.assertCounterSnapshot;
import static io.strimzi.kafka.metrics.MetricsUtils.assertGaugeSnapshot;
import static io.strimzi.kafka.metrics.MetricsUtils.assertInfoSnapshot;
import static io.strimzi.kafka.metrics.MetricsUtils.assertSummarySnapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PrometheusCollectorTest {

    @Test
    public void testRegister() {
        PrometheusRegistry registry = new PrometheusRegistry();
        PrometheusCollector pc1 = PrometheusCollector.register(registry);
        PrometheusCollector pc2 = PrometheusCollector.register(registry);
        assertSame(pc1, pc2);
        assertTrue(registry.scrape().size() > 0);
    }

    @Test
    public void testCollect() {
        PrometheusRegistry registry = new PrometheusRegistry();
        PrometheusCollector prometheusCollector = PrometheusCollector.register(registry);
        Labels labels = Labels.of("l1", "v1", "l2", "v2");
        double value = 2.0;
        prometheusCollector.addCollector(new MetricsCollector() {
            @Override
            public List<MetricSnapshot<?>> collect() {
                List<MetricSnapshot<?>> snapshots = new ArrayList<>();
                snapshots.add(GaugeSnapshot.builder()
                        .name("gauge")
                        .dataPoint(DataPointSnapshotBuilder.gaugeDataPoint(labels, value))
                        .build());
                snapshots.add(CounterSnapshot.builder()
                        .name("counter")
                        .dataPoint(DataPointSnapshotBuilder.counterDataPoint(labels, value))
                        .build());
                return snapshots;
            }
        });
        int count = 1;
        Quantiles quantiles = Quantiles.of(new Quantile(0.9, value));
        String metricName = "name";
        prometheusCollector.addCollector(new MetricsCollector() {
            @Override
            public List<MetricSnapshot<?>> collect() {
                List<MetricSnapshot<?>> snapshots = new ArrayList<>();
                snapshots.add(InfoSnapshot.builder()
                        .name("info")
                        .dataPoint(DataPointSnapshotBuilder.infoDataPoint(labels, value, metricName))
                        .build());
                snapshots.add(SummarySnapshot.builder()
                        .name("summary")
                        .dataPoint(DataPointSnapshotBuilder.summaryDataPoint(labels, count, value, quantiles))
                        .build());
                return snapshots;
            }
        });
        MetricSnapshots snapshots = prometheusCollector.collect();
        assertEquals(4, snapshots.size());
        assertGaugeSnapshot(findSnapshot(snapshots, GaugeSnapshot.class), value, labels);
        assertCounterSnapshot(findSnapshot(snapshots, CounterSnapshot.class), value, labels);
        assertInfoSnapshot(findSnapshot(snapshots, InfoSnapshot.class), labels, metricName, String.valueOf(value));
        assertSummarySnapshot(findSnapshot(snapshots, SummarySnapshot.class), count, value, labels, quantiles);
    }

    @Test
    public void testUpdateAllowlist() {
        PrometheusRegistry registry = new PrometheusRegistry();
        PrometheusCollector prometheusCollector = PrometheusCollector.register(registry);
        MyMetricsCollector myMetricsCollector = new MyMetricsCollector();
        Pattern pattern = Pattern.compile(".*");
        assertEquals(pattern.pattern(), myMetricsCollector.allowlist.pattern());
        prometheusCollector.addCollector(myMetricsCollector);
        pattern = Pattern.compile("prefix_metric1.*");
        prometheusCollector.updateAllowlist(pattern);
        assertEquals(pattern.pattern(), myMetricsCollector.allowlist.pattern());

        KafkaMetric metric1 = MetricsUtils.newKafkaMetric("name", "metric1", (config, now) -> 0, Collections.emptyMap());
        String name1 = KafkaMetricWrapper.prometheusName("prefix", metric1.metricName());
        KafkaMetricWrapper mw1 = new KafkaMetricWrapper(name1, metric1, "attribute1");
        myMetricsCollector.addMetric("metric1", mw1);

        KafkaMetric metric2 = MetricsUtils.newKafkaMetric("name", "metric2", (config, now) -> 0, Collections.emptyMap());
        String name2 = KafkaMetricWrapper.prometheusName("prefix", metric2.metricName());
        KafkaMetricWrapper mw2 = new KafkaMetricWrapper(name2, metric2, "attribute2");
        myMetricsCollector.addMetric("metric2", mw2);

        assertEquals(1, myMetricsCollector.allowedMetrics().size());
        assertTrue(myMetricsCollector.allowedMetrics().contains(mw1));

        pattern = Pattern.compile("(prefix_metric1.*)|(prefix_metric2.*)");
        prometheusCollector.updateAllowlist(pattern);
        assertEquals(2, myMetricsCollector.allowedMetrics().size());
        assertTrue(myMetricsCollector.allowedMetrics().contains(mw1));
        assertTrue(myMetricsCollector.allowedMetrics().contains(mw2));

        pattern = Pattern.compile("(prefix_metric3.*)|(prefix_metric2.*)");
        prometheusCollector.updateAllowlist(pattern);
        assertEquals(1, myMetricsCollector.allowedMetrics().size());
        assertTrue(myMetricsCollector.allowedMetrics().contains(mw2));
    }

    static class MyMetricsCollector extends MetricsCollector {

        @Override
        public List<MetricSnapshot<?>> collect() {
            return Collections.emptyList();
        }

    }

    private MetricSnapshot<?> findSnapshot(MetricSnapshots snapshots, Class<?> clazz) {
        for (MetricSnapshot<?> snapshot : snapshots) {
            if (clazz.isInstance(snapshot)) {
                return snapshot;
            }
        }
        throw new IllegalStateException("unable to find snapshot of type " + clazz.getName());
    }

}
