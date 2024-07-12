/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import io.prometheus.metrics.model.snapshots.InfoSnapshot;
import io.prometheus.metrics.model.snapshots.Labels;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DataPointSnapshotBuilderTest {

    @Test
    public void testCollidingNewLabelIsIgnored() {
        Labels labels = Labels.builder().label("k_1", "v1").label("k2", "v2").build();
        InfoSnapshot.InfoDataPointSnapshot snapshot = DataPointSnapshotBuilder.infoDataPoint(labels, "value", "k-1");
        assertEquals("v1", snapshot.getLabels().get("k_1"));
    }

}
