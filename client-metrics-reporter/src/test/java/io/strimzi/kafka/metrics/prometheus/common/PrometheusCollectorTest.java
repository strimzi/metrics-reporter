/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.prometheus.common;

import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot;
import io.prometheus.metrics.model.snapshots.InfoSnapshot;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.strimzi.kafka.metrics.prometheus.MetricsUtils.assertGaugeSnapshot;
import static io.strimzi.kafka.metrics.prometheus.MetricsUtils.assertInfoSnapshot;
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
            return snapshots;
        });

        String metricName = "name";
        prometheusCollector.addCollector(() -> {
            List<MetricSnapshot> snapshots = new ArrayList<>();
            snapshots.add(InfoSnapshot.builder()
                    .name("info")
                    .dataPoint(DataPointSnapshotBuilder.infoDataPoint(labels, value, metricName))
                    .build());
            return snapshots;
        });
        MetricSnapshots snapshots = prometheusCollector.collect();
        assertEquals(2, snapshots.size());
        assertGaugeSnapshot(findSnapshot(snapshots, GaugeSnapshot.class), value, labels);
        assertInfoSnapshot(findSnapshot(snapshots, InfoSnapshot.class), labels, metricName, String.valueOf(value));
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
