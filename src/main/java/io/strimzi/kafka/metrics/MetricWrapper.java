/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import io.prometheus.metrics.model.snapshots.Labels;

/**
 * Wrapper for both Kafka and Yammer metrics to unify logic in the Collectors
 */
public abstract class MetricWrapper {

    private final String prometheusName;
    private final Labels labels;
    private final Object metric;
    private final String attribute;

    protected MetricWrapper(String prometheusName, Labels labels, Object metric, String attribute) {
        this.prometheusName = prometheusName;
        this.labels = labels;
        this.metric = metric;
        this.attribute = attribute;
    }

    /**
     * The Prometheus name of this metric
     * @return The Prometheus name
     */
    public String prometheusName() {
        return prometheusName;
    }

    /**
     * The labels associated with this metric
     * @return The labels
     */
    public Labels labels() {
        return labels;
    }

    /**
     * The underlying metric
     * @return The metric
     */
    public Object metric() {
        return metric;
    }

    /**
     * The metric attribute
     * @return The attribute
     */
    public String attribute() {
        return attribute;
    }

}
