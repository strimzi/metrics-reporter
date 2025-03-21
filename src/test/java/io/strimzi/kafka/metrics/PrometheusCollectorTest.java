/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.strimzi.kafka.metrics.TestUtils.assertCounterSnapshot;
import static io.strimzi.kafka.metrics.TestUtils.assertGaugeSnapshot;
import static io.strimzi.kafka.metrics.TestUtils.assertInfoSnapshot;
import static io.strimzi.kafka.metrics.TestUtils.assertSummarySnapshot;
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
        prometheusCollector.addCollector(() -> {
            List<MetricSnapshot> snapshots = new ArrayList<>();
            snapshots.add(GaugeSnapshot.builder()
                    .name("gauge")
                    .dataPoint(DataPointSnapshotBuilder.gaugeDataPoint(labels, value))
                    .build());
            snapshots.add(CounterSnapshot.builder()
                    .name("counter")
                    .dataPoint(DataPointSnapshotBuilder.counterDataPoint(labels, value))
                    .build());
            return snapshots;
        });
        int count = 1;
        Quantiles quantiles = Quantiles.of(new Quantile(0.9, value));
        String metricName = "name";
        prometheusCollector.addCollector(() -> {
            List<MetricSnapshot> snapshots = new ArrayList<>();
            snapshots.add(InfoSnapshot.builder()
                    .name("info")
                    .dataPoint(DataPointSnapshotBuilder.infoDataPoint(labels, value, metricName))
                    .build());
            snapshots.add(SummarySnapshot.builder()
                    .name("summary")
                    .dataPoint(DataPointSnapshotBuilder.summaryDataPoint(labels, count, value, quantiles))
                    .build());
            return snapshots;
        });
        MetricSnapshots snapshots = prometheusCollector.collect();
        assertEquals(4, snapshots.size());
        assertGaugeSnapshot(findSnapshot(snapshots, GaugeSnapshot.class), value, labels);
        assertCounterSnapshot(findSnapshot(snapshots, CounterSnapshot.class), value, labels);
        assertInfoSnapshot(findSnapshot(snapshots, InfoSnapshot.class), labels, metricName, String.valueOf(value));
        assertSummarySnapshot(findSnapshot(snapshots, SummarySnapshot.class), count, value, labels, quantiles);
    }

    private MetricSnapshot findSnapshot(MetricSnapshots snapshots, Class<?> clazz) {
        for (MetricSnapshot snapshot : snapshots) {
            if (clazz.isInstance(snapshot)) {
                return snapshot;
            }
        }
        throw new IllegalStateException("unable to find snapshot of type " + clazz.getName());
    }

}
