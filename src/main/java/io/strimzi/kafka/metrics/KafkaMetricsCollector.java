/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import io.prometheus.metrics.model.registry.MultiCollector;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot;
import io.prometheus.metrics.model.snapshots.InfoSnapshot;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prometheus Collector to store and export metrics retrieved by {@link KafkaPrometheusMetricsReporter}.
 */
public class KafkaMetricsCollector implements MultiCollector {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaMetricsCollector.class);
    private final Map<MetricName, MetricWrapper> metrics;

    /**
     * Constructs a new KafkaMetricsCollector with provided configuration.
     */
    public KafkaMetricsCollector() {
        this.metrics = new ConcurrentHashMap<>();
    }

    /**
     * This method is used to add a Kafka metric to the collection for reporting.
     * The metric is wrapped in a MetricWrapper object which contains additional information
     * such as the prometheus name of the metric.
     *
     * @param name The name of the metric in the Kafka system. This is used as the key in the metrics map.
     * @param metric The Kafka metric to add. This is wrapped in a MetricWrapper object.
     */
    public void addMetric(MetricName name, MetricWrapper metric) {
        metrics.put(name, metric);
    }

    /**
     * Removes a Kafka metric from collection.
     *
     * @param name The Kafka metric to remove.
     */
    public void removeMetric(MetricName name) {
        metrics.remove(name);
    }

    /**
     * Called when the Prometheus server scrapes metrics.
     * @return MetricSnapshots object that contains snapshots of metrics
     */
    @Override
    public MetricSnapshots collect() {
        Map<String, GaugeSnapshot.Builder> gaugeBuilders = new HashMap<>();
        Map<String, InfoSnapshot.Builder> infoBuilders = new HashMap<>();

        for (Map.Entry<MetricName, MetricWrapper> entry : metrics.entrySet()) {
            MetricWrapper metricWrapper = entry.getValue();
            String prometheusMetricName = metricWrapper.prometheusName();
            Object metricValue = ((KafkaMetric) metricWrapper.metric()).metricValue();
            Labels labels = metricWrapper.labels();
            LOG.debug("Collecting metric {} with the following labels: {}", prometheusMetricName, labels);

            if (metricValue instanceof Number) {
                double value = ((Number) metricValue).doubleValue();
                GaugeSnapshot.Builder builder = gaugeBuilders.computeIfAbsent(prometheusMetricName, k -> GaugeSnapshot.builder().name(prometheusMetricName));
                builder.dataPoint(DataPointSnapshotBuilder.gaugeDataPoint(labels, value));
            } else {
                InfoSnapshot.Builder builder = infoBuilders.computeIfAbsent(prometheusMetricName, k -> InfoSnapshot.builder().name(prometheusMetricName));
                builder.dataPoint(DataPointSnapshotBuilder.infoDataPoint(labels, metricValue, metricWrapper.attribute()));
            }
        }
        List<MetricSnapshot> snapshots = new ArrayList<>();
        for (GaugeSnapshot.Builder builder : gaugeBuilders.values()) {
            snapshots.add(builder.build());
        }
        for (InfoSnapshot.Builder builder : infoBuilders.values()) {
            snapshots.add(builder.build());
        }
        return new MetricSnapshots(snapshots);
    }
}
