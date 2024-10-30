/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.collector;

import io.prometheus.metrics.model.snapshots.MetricSnapshot;
import io.strimzi.kafka.metrics.PrometheusMetricsReporterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Abstract class for both Kafka and Yammer collectors
 */
public abstract class MetricsCollector {

    private static final Logger LOG = LoggerFactory.getLogger(MetricsCollector.class);

    private final Map<Object, MetricWrapper> allowedMetrics = new ConcurrentHashMap<>();
    private final Map<Object, MetricWrapper> otherMetrics = new ConcurrentHashMap<>();
    /* test */ Pattern allowlist = Pattern.compile(PrometheusMetricsReporterConfig.ALLOWLIST_CONFIG_DEFAULT);

    /**
     * Collect all the metrics added to this Collector
     *
     * @return the list of metrics of this collector
     */
    public abstract List<MetricSnapshot<?>> collect();

    /**
     * Update the allowlist used by the Collector to filter metrics
     * @param allowlist the new allowlist Pattern
     */
    public void updateAllowlist(Pattern allowlist) {
        this.allowlist = allowlist;
        update();
    }

    /**
     * Add a metric to be collected.
     * @param name The name of the metric to add.
     * @param metric The metric to add.
     */
    public void addMetric(Object name, MetricWrapper metric) {
        if (allowlist.matcher(metric.prometheusName()).matches()) {
            allowedMetrics.put(name, metric);
        } else {
            LOG.trace("Ignoring metric {} as it does not match the allowlist", metric.prometheusName());
            otherMetrics.put(name, metric);
        }
    }

    /**
     * Remove a metric from collection.
     * @param name The name of metric to remove.
     */
    public void removeMetric(Object name) {
        allowedMetrics.remove(name);
        otherMetrics.remove(name);
    }

    /**
     * Retrieve the allowed metrics
     * @return the collection of allowed MetricWrapper
     */
    public Collection<MetricWrapper> allowedMetrics() {
        return allowedMetrics.values();
    }

    private void update() {
        Map<Object, MetricWrapper> newAllowedMetrics = new HashMap<>();
        for (Map.Entry<Object, MetricWrapper> entry : otherMetrics.entrySet()) {
            String name = entry.getValue().prometheusName();
            if (allowlist.matcher(name).matches()) {
                newAllowedMetrics.put(entry.getKey(), entry.getValue());
                otherMetrics.remove(entry.getKey());
            }
        }
        for (Map.Entry<Object, MetricWrapper> entry : allowedMetrics.entrySet()) {
            String name = entry.getValue().prometheusName();
            if (!allowlist.matcher(name).matches()) {
                otherMetrics.put(entry.getKey(), entry.getValue());
                allowedMetrics.remove(entry.getKey());
            }
        }
        allowedMetrics.putAll(newAllowedMetrics);
    }
}
