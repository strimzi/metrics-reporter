/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.prometheus.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Common reporter logic to track metrics that match an allowlist pattern. This filters the metrics as they are added
 * and removed so when Prometheus scrapes the /metrics endpoint, we just have to convert them to the Prometheus format.
 */
public abstract class AbstractReporter {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractReporter.class);

    // Metrics that match the allowlist
    private final Map<Object, MetricWrapper> allowedMetrics = new ConcurrentHashMap<>();
    // Metrics that don't match the allowlist. This is only used by reporters that are reconfigurable so if the
    // allowlist is updated we can update the matching metrics.
    private final Map<Object, MetricWrapper> disallowedMetrics = new ConcurrentHashMap<>();

    /**
     * The current allowlist
     * @return A {@link Pattern} representing the allowlist
     */
    protected abstract Pattern allowlist();

    /**
     * Whether the reporter is reconfigurable.
     * @return true for server side reporters, otherwise false
     */
    protected boolean isReconfigurable() {
        return false;
    }

    private boolean matches(String name) {
        return allowlist().matcher(name).matches();
    }

    /**
     * Add a metric to be collected.
     * @param name The name of the metric to add.
     * @param metric The metric to add.
     */
    public void addMetric(Object name, MetricWrapper metric) {
        if (matches(metric.prometheusName())) {
            allowedMetrics.put(name, metric);
        } else {
            LOG.trace("Ignoring metric {} as it does not match the allowlist", metric.prometheusName());
            if (isReconfigurable()) {
                disallowedMetrics.put(name, metric);
            }
        }
    }

    /**
     * Remove a metric from collection.
     * @param name The name of metric to remove.
     */
    public void removeMetric(Object name) {
        allowedMetrics.remove(name);
        if (isReconfigurable()) {
            disallowedMetrics.remove(name);
        }
    }

    /**
     * Retrieve the allowed metrics.
     * @return A collection of MetricWrapper
     */
    public Collection<MetricWrapper> allowedMetrics() {
        return allowedMetrics.values();
    }

    /**
     * Update the allowed metrics based on the current allowlist pattern.
     */
    public void updateAllowedMetrics() {
        if (!isReconfigurable()) return;
        Map<Object, MetricWrapper> newAllowedMetrics = new HashMap<>();
        for (Map.Entry<Object, MetricWrapper> entry : disallowedMetrics.entrySet()) {
            String name = entry.getValue().prometheusName();
            if (matches(name)) {
                newAllowedMetrics.put(entry.getKey(), entry.getValue());
                disallowedMetrics.remove(entry.getKey());
            }
        }
        for (Map.Entry<Object, MetricWrapper> entry : allowedMetrics.entrySet()) {
            String name = entry.getValue().prometheusName();
            if (!matches(name)) {
                disallowedMetrics.put(entry.getKey(), entry.getValue());
                allowedMetrics.remove(entry.getKey());
            }
        }
        allowedMetrics.putAll(newAllowedMetrics);
    }
}
