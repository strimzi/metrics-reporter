/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.kafka;

import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.PrometheusNaming;
import io.strimzi.kafka.metrics.MetricWrapper;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Wrapper for Kafka metrics
 */
public class KafkaMetricWrapper extends MetricWrapper {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaMetricWrapper.class);

    /**
     * Constructor from Kafka Metrics
     * @param prometheusName The name of the metric in the prometheus format
     * @param metric The Kafka metric
     * @param attribute The attribute of the Kafka metric
     */
    public KafkaMetricWrapper(String prometheusName, KafkaMetric metric, String attribute) {
        super(prometheusName, labelsFromTags(metric.metricName().tags(), prometheusName), metric, attribute);
    }

    /**
     * Compute the Prometheus name from a Kafka MetricName
     * @param prefix The prefix to add to the metric name
     * @param metricName The Kafka metric name
     * @return The prometheus metric name
     */
    public static String prometheusName(String prefix, MetricName metricName) {
        return PrometheusNaming.prometheusName(
                PrometheusNaming.sanitizeMetricName(
                        prefix + '_' + metricName.group() + '_' + metricName.name()).toLowerCase(Locale.ROOT));
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
