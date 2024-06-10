/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.prometheus.client.Collector;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Prometheus Collector to store and export metrics retrieved by the reporters.
 */
public class KafkaMetricsCollector extends Collector {

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
        this.prefix = prefix;
    }

    @Override
    public List<MetricFamilySamples> collect() {
        List<MetricFamilySamples> samples = new ArrayList<>();

        for (Map.Entry<MetricName, KafkaMetric> entry : metrics.entrySet()) {
            MetricName metricName = entry.getKey();
            KafkaMetric kafkaMetric = entry.getValue();
            LOG.trace("Collecting Kafka metric {}", metricName);

            String name = metricName(metricName);
            // TODO Filtering should take labels into account
            if (!config.isAllowed(name)) {
                LOG.info("Kafka metric {} is not allowed", name);
                continue;
            }
            LOG.info("Kafka metric {} is allowed", name);
            LOG.info("labels " + metricName.tags());
            MetricFamilySamples sample = convert(name, metricName.description(), kafkaMetric, metricName.tags());
            if (sample != null) {
                samples.add(sample);
            }
        }
        return samples;
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

    String metricName(MetricName metricName) {
        String prefix = this.prefix
                .replace('.', '_')
                .replace('-', '_')
                .toLowerCase(Locale.ROOT);
        String group = metricName.group()
                .replace('.', '_')
                .replace('-', '_')
                .toLowerCase(Locale.ROOT);
        String name = metricName.name()
                .replace('.', '_')
                .replace('-', '_')
                .toLowerCase(Locale.ROOT);
        return prefix + '_' + group + '_' + name;
    }

    static MetricFamilySamples convert(String name, String help, KafkaMetric metric, Map<String, String> labels) {
        Object value = metric.metricValue();
        Map<String, String> sanitizedLabels = labels.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> Collector.sanitizeMetricName(e.getKey()),
                        Map.Entry::getValue,
                        (v1, v2) -> {
                            throw new IllegalStateException("Unexpected duplicate key " + v1);
                        },
                        LinkedHashMap::new));

        if (!(value instanceof Number)) {
            // If the value is non-numeric, add it as a label and set the metric value to 1.0
            LOG.info("*******Converting non-numeric metric {} with value {} to label", name, value);
            sanitizedLabels.put("value", value.toString());
            return new MetricFamilySamplesBuilder(Type.GAUGE, help)
                    .addSample(name, 1.0, sanitizedLabels)
                    .build();
        }

        return new MetricFamilySamplesBuilder(Type.GAUGE, help)
                .addSample(name, ((Number) value).doubleValue(), sanitizedLabels)
                .build();
    }
}
