/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.prometheus.metrics.model.registry.MultiCollector;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot;
import io.prometheus.metrics.model.snapshots.InfoSnapshot;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;
import io.prometheus.metrics.model.snapshots.PrometheusNaming;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prometheus Collector to store and export metrics retrieved by {@link KafkaPrometheusMetricsReporter}.
 */
public class KafkaMetricsCollector implements MultiCollector {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaMetricsCollector.class.getName());

    private final Map<MetricName, KafkaMetric> metrics;
    private final PrometheusMetricsReporterConfig config;
    @SuppressFBWarnings({"UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR"}) // Should be investigated as part of https://github.com/strimzi/metrics-reporter/issues/12
    private String prefix;

    /**
     * Constructs a new KafkaMetricsCollector with provided configuration.
     *
     * @param config The configuration for the PrometheusMetricsReporter.
     */
    public KafkaMetricsCollector(PrometheusMetricsReporterConfig config) {
        this.config = config;
        this.metrics = new ConcurrentHashMap<>();
    }

    /**
     * Sets the prefix to be used for metric names.
     *
     * @param prefix The prefix to set.
     */
    public void setPrefix(String prefix) {
        this.prefix = PrometheusNaming.prometheusName(prefix);
    }

    /**
     * Adds a Kafka metric to be collected.
     *
     * @param metric The Kafka metric to add.
     */
    public void addMetric(KafkaMetric metric) {
        metrics.put(metric.metricName(), metric);
    }

    /**
     * Removes a Kafka metric from collection.
     *
     * @param metric The Kafka metric to remove.
     */
    public void removeMetric(KafkaMetric metric) {
        metrics.remove(metric.metricName());
    }

    /**
     * Called when the Prometheus server scrapes metrics.
     * @return metrics that match the configured allowlist
     */
    @Override
    public MetricSnapshots collect() {
        Map<String, GaugeSnapshot.Builder> gaugeBuilders = new HashMap<>();
        Map<String, InfoSnapshot.Builder> infoBuilders = new HashMap<>();

        for (Map.Entry<MetricName, KafkaMetric> entry : metrics.entrySet()) {
            MetricName metricName = entry.getKey();
            KafkaMetric kafkaMetric = entry.getValue();
            LOG.trace("Collecting Kafka metric {}", metricName);

            String prometheusMetricName = metricName(metricName);
            // TODO Filtering should take labels into account
            if (!config.isAllowed(prometheusMetricName)) {
                LOG.info("Kafka metric {} is not allowed", prometheusMetricName);
                continue;
            }
            LOG.info("Kafka metric {} is allowed", prometheusMetricName);
            Labels labels = labelsFromTags(metricName.tags(), metricName.name());

            Object valueObj = kafkaMetric.metricValue();
            if (valueObj instanceof Number) {
                double value = ((Number) valueObj).doubleValue();
                GaugeSnapshot.Builder builder = gaugeBuilders.computeIfAbsent(prometheusMetricName, k -> GaugeSnapshot.builder().name(prometheusMetricName));
                builder.dataPoint(DataPointSnapshotBuilder.gaugeDataPoint(labels, value));
            } else {
                InfoSnapshot.Builder builder = infoBuilders.computeIfAbsent(prometheusMetricName, k -> InfoSnapshot.builder().name(prometheusMetricName));
                builder.dataPoint(DataPointSnapshotBuilder.infoDataPoint(labels, valueObj, metricName.name()));
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

    private String metricName(MetricName metricName) {
        return PrometheusNaming
                .sanitizeMetricName(prefix + '_' + metricName.group() + '_' + metricName.name())
                .toLowerCase(Locale.ROOT);
    }

    static Labels labelsFromTags(Map<String, String> tags, String metricName) {
        Labels.Builder builder = Labels.builder();
        Set<String> labelNames = new HashSet<>();
        for (Map.Entry<String, String> label : tags.entrySet()) {
            String newLabelName = PrometheusNaming.sanitizeLabelName(label.getKey());
            if (labelNames.add(newLabelName)) {
                builder.label(newLabelName, label.getValue());
            } else {
                LOG.warn("Ignoring duplicate label key: {} with value: {} from metric: {} ", newLabelName, label.getValue(), metricName);
            }
        }
        return builder.build();
    }
}
