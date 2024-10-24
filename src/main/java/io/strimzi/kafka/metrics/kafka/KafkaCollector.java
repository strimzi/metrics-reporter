/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.kafka;

import io.prometheus.metrics.model.snapshots.GaugeSnapshot;
import io.prometheus.metrics.model.snapshots.InfoSnapshot;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;
import io.strimzi.kafka.metrics.DataPointSnapshotBuilder;
import io.strimzi.kafka.metrics.MetricWrapper;
import io.strimzi.kafka.metrics.MetricsCollector;
import io.strimzi.kafka.metrics.PrometheusCollector;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Collector for Kafka metrics
 */
public class KafkaCollector implements MetricsCollector {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaCollector.class);
    private static final KafkaCollector INSTANCE = new KafkaCollector();
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private final Map<MetricName, MetricWrapper> kafkaMetrics = new ConcurrentHashMap<>();

    /* for testing */ KafkaCollector() { }

    /**
     * Constructor only used for testing
     * @param prometheusCollector the PrometheusCollector that will collect metrics
     */
    public KafkaCollector(PrometheusCollector prometheusCollector) {
        prometheusCollector.addCollector(this);
    }

    /**
     * Retrieve the KafkaCollector instance
     *
     * @param prometheusCollector the PrometheusCollector that will collect metrics
     * @return the KafkaCollector singleton
     */
    public static KafkaCollector getCollector(PrometheusCollector prometheusCollector) {
        if (REGISTERED.compareAndSet(false, true)) {
            prometheusCollector.addCollector(INSTANCE);
        }
        return INSTANCE;
    }

    /**
     * Add a Kafka metric to be collected.
     *
     * @param name The name of the Kafka metric to add.
     * @param metric The Kafka metric to add.
     */
    public void addMetric(MetricName name, MetricWrapper metric) {
        kafkaMetrics.put(name, metric);
    }

    /**
     * Remove a Kafka metric from collection.
     *
     * @param name The name of Kafka metric to remove.
     */
    public void removeMetric(MetricName name) {
        kafkaMetrics.remove(name);
    }

    /**
     * Collect all the metrics added to this Collector
     *
     * @return the list of metrics of this collector
     */
    @Override
    public List<MetricSnapshot<?>> collect() {
        Map<String, MetricSnapshot.Builder<?>> builders = new HashMap<>();
        for (MetricWrapper metricWrapper : kafkaMetrics.values()) {
            String prometheusMetricName = metricWrapper.prometheusName();
            Object metricValue = ((KafkaMetric) metricWrapper.metric()).metricValue();
            Labels labels = metricWrapper.labels();
            LOG.debug("Collecting Kafka metric {} with the following labels: {}", prometheusMetricName, labels);

            if (metricValue instanceof Number) {
                double value = ((Number) metricValue).doubleValue();
                GaugeSnapshot.Builder builder = (GaugeSnapshot.Builder) builders.computeIfAbsent(prometheusMetricName, k -> GaugeSnapshot.builder().name(prometheusMetricName));
                builder.dataPoint(DataPointSnapshotBuilder.gaugeDataPoint(labels, value));
            } else {
                InfoSnapshot.Builder builder = (InfoSnapshot.Builder) builders.computeIfAbsent(prometheusMetricName, k -> InfoSnapshot.builder().name(prometheusMetricName));
                builder.dataPoint(DataPointSnapshotBuilder.infoDataPoint(labels, metricValue, metricWrapper.attribute()));
            }
        }
        List<MetricSnapshot<?>> snapshots = new ArrayList<>();
        for (MetricSnapshot.Builder<?> builder : builders.values()) {
            snapshots.add(builder.build());
        }
        return snapshots;
    }
}
