/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import io.prometheus.metrics.instrumentation.jvm.JvmMetrics;
import io.prometheus.metrics.model.registry.MultiCollector;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Prometheus Collector to store and export metrics retrieved by {@link KafkaPrometheusMetricsReporter}
 * and {@link YammerPrometheusMetricsReporter}.
 */
public class PrometheusCollector implements MultiCollector {

    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);
    private static final PrometheusCollector INSTANCE = new PrometheusCollector();

    // At runtime this should contain at most one instance of KafkaCollector and one instance of YammerCollector
    private final List<MetricsCollector> collectors = new ArrayList<>();

    /* for testing */ PrometheusCollector() { }

    /**
     * Register this Collector with the provided Prometheus registry
     * @param registry The Prometheus registry
     * @return The PrometheusCollector instance
     */
    public static PrometheusCollector register(PrometheusRegistry registry) {
        if (REGISTERED.compareAndSet(false, true)) {
            // Add JVM metrics
            JvmMetrics.builder().register(registry);
            registry.register(INSTANCE);
        }
        return INSTANCE;
    }

    /**
     * Add a collector that provides metrics
     * @param collector The collector to add
     */
    public void addCollector(MetricsCollector collector) {
        collectors.add(collector);
    }

    /**
     * Called when the Prometheus server scrapes metrics.
     * @return MetricSnapshots that contains the metrics
     */
    @Override
    public MetricSnapshots collect() {
        List<MetricSnapshot> snapshots = new ArrayList<>();
        for (MetricsCollector collector : collectors) {
            snapshots.addAll(collector.collect());
        }
        return new MetricSnapshots(snapshots);
    }
}
