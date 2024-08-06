/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import com.yammer.metrics.core.Metric;
import com.yammer.metrics.core.MetricName;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.PrometheusNaming;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Wrapper for both Kafka and Yammer metrics to unify logic in the Collectors
 */
public class MetricWrapper {

    private static final Logger LOG = LoggerFactory.getLogger(MetricWrapper.class);

    private final String prometheusName;
    private final Labels labels;
    private final Object value;
    private final String attribute;

    // Will be used when implementing https://github.com/strimzi/metrics-reporter/issues/9
    /**
     * Constructor from Kafka Metrics
     * @param prometheusName The name of the metric in the prometheus format
     * @param metric The Kafka metric
     * @param attribute The attribute of the Kafka metric
     */
    public MetricWrapper(String prometheusName, KafkaMetric metric, String attribute) {
        this.prometheusName = prometheusName;
        this.labels = labelsFromTags(metric.metricName().tags(), prometheusName);
        this.value = metric.metricValue();
        this.attribute = attribute;
    }

    /**
     * Constructor from Yammer Metrics
     * @param prometheusName The name of the metric in the prometheus format
     * @param scope The scope of the Yammer metric
     * @param metric The Yammer metric
     * @param attribute The attribute of the Yammer metric
     */
    public MetricWrapper(String prometheusName, String scope, Metric metric, String attribute) {
        this.prometheusName = prometheusName;
        this.labels = labelsFromScope(scope, prometheusName);
        this.value = metric;
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
     * The metric value
     * @return The value
     */
    public Object value() {
        return value;
    }

    /**
     * The metric attribute
     * @return The attribute
     */
    public String attribute() {
        return attribute;
    }

    private static Labels labelsFromScope(String scope, String metricName) {
        Labels.Builder builder = Labels.builder();
        Set<String> labelNames = new HashSet<>();
        if (scope != null) {
            String[] parts = scope.split("\\.");
            if (parts.length % 2 == 0) {
                for (int i = 0; i < parts.length; i += 2) {
                    String newLabelName = PrometheusNaming.sanitizeLabelName(parts[i]);
                    if (labelNames.add(newLabelName)) {
                        builder.label(newLabelName, parts[i + 1]);
                    } else {
                        LOG.warn("Ignoring duplicate label key: {} with value: {} from metric: {} ", newLabelName, parts[i + 1], metricName);
                    }
                }
            }
        }
        return builder.build();
    }

    // Will be used when implementing https://github.com/strimzi/metrics-reporter/issues/9
    private static Labels labelsFromTags(Map<String, String> tags, String metricName) {
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

    /**
     * Compute the Prometheus name from a Yammer MetricName
     * @param metricName The Yammer metric name
     * @return The prometheus metric name
     */
    public static String prometheusName(MetricName metricName) {
        return PrometheusNaming.prometheusName(
                PrometheusNaming.sanitizeMetricName(
                        "kafka_server_" +
                        metricName.getGroup() + '_' +
                        metricName.getType() + '_' +
                        metricName.getName()).toLowerCase(Locale.ROOT));
    }

    // Will be used when implementing https://github.com/strimzi/metrics-reporter/issues/9
    /**
     * Compute the Prometheus name from a Kafka MetricName
     * @param prefix The prefix to add to the metric name
     * @param metricName The Kafka metric name
     * @return The prometheus metric name
     */
    public static String prometheusName(String prefix, org.apache.kafka.common.MetricName metricName) {
        return PrometheusNaming.prometheusName(
                PrometheusNaming.sanitizeMetricName(
                        prefix + '_' + metricName.group() + '_' + metricName.name()).toLowerCase(Locale.ROOT));
    }
}
