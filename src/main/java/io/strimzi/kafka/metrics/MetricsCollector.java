/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import io.prometheus.metrics.model.snapshots.MetricSnapshot;

import java.util.List;

/**
 * Interface for both Kafka and Yammer collectors
 */
public interface MetricsCollector {

    /**
     * Collect all the metrics added to this Collector
     * @return the list of metrics of this collector
     */
    List<MetricSnapshot> collect();
}
