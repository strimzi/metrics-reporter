/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Counter;
import com.yammer.metrics.core.Gauge;
import com.yammer.metrics.core.Histogram;
import com.yammer.metrics.core.Meter;
import com.yammer.metrics.core.Metric;
import com.yammer.metrics.core.MetricName;
import com.yammer.metrics.core.MetricsRegistry;
import com.yammer.metrics.core.Timer;
import io.prometheus.client.Collector;
import org.apache.kafka.server.metrics.KafkaYammerMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * MetricsReporter implementation that expose Kafka metrics in the Prometheus format.
 *
 * This can be used by Kafka brokers and clients.
 */
public class YammerMetricsCollector extends Collector {

    private static final Logger LOG = LoggerFactory.getLogger(YammerMetricsCollector.class.getName());

    private final List<MetricsRegistry> registries;
    private final PrometheusMetricsReporterConfig config;

    /**
     * Constructs a new YammerMetricsCollector with the provided configuration.
     *
     * @param config The configuration for the YammerMetricsCollector.
     */
    public YammerMetricsCollector(PrometheusMetricsReporterConfig config) {
        this.config = config;
        this.registries = Arrays.asList(KafkaYammerMetrics.defaultRegistry(), Metrics.defaultRegistry());
    }

    @Override
    public List<MetricFamilySamples> collect() {
        List<MetricFamilySamples> samples = new ArrayList<>();

        for (MetricsRegistry registry : registries) {
            for (Map.Entry<MetricName, Metric> entry : registry.allMetrics().entrySet()) {
                MetricName metricName = entry.getKey();
                Metric metric = entry.getValue();
                LOG.trace("Collecting Yammer metric {}", metricName);

                String prometheusMetricName = metricName(metricName);
                // TODO Filtering should take labels into account
                if (!config.isAllowed(prometheusMetricName)) {
                    LOG.info("Yammer metric {} is not allowed", prometheusMetricName);
                    continue;
                }
                LOG.info("Yammer metric {} is allowed", prometheusMetricName);
                Map<String, String> labels = labelsFromScope(metricName.getScope());
                LOG.info("labels {} ", labels);

                MetricFamilySamples sample = null;
                if (metric instanceof Counter) {
                    sample = convert(prometheusMetricName, (Counter) metric, labels);
                } else if (metric instanceof Gauge) {
                    sample = convert(prometheusMetricName, (Gauge<?>) metric, labels, metricName);
                } else if (metric instanceof Histogram) {
                    sample = convert(prometheusMetricName, (Histogram) metric, labels);
                } else if (metric instanceof Meter) {
                    sample = convert(prometheusMetricName, (Meter) metric, labels);
                } else if (metric instanceof Timer) {
                    sample = convert(prometheusMetricName, (Timer) metric, labels);
                } else {
                    LOG.error("The metric " + metric.getClass().getName() + " has an unexpected type.");
                }
                if (sample != null) {
                    samples.add(sample);
                }
            }
        }
        return samples;
    }

    static String metricName(MetricName metricName) {
        String metricNameStr = Collector.sanitizeMetricName(
                "kafka_server_" +
                metricName.getGroup() + '_' +
                metricName.getType() + '_' +
                metricName.getName()).toLowerCase(Locale.ROOT);
        LOG.info("metricName group {}, type {}, name {} converted into {}", metricName.getGroup(), metricName.getType(), metricName.getName(), metricNameStr);
        return metricNameStr;
    }

    static Map<String, String> labelsFromScope(String scope) {
        if (scope != null) {
            String[] parts = scope.split("\\.");
            if (parts.length % 2 == 0) {
                Map<String, String> labels = new LinkedHashMap<>();
                for (int i = 0; i < parts.length; i += 2) {
                    labels.put(Collector.sanitizeMetricName(parts[i]), parts[i + 1]);
                }
                return labels;
            }
        }
        return Collections.emptyMap();
    }

    static MetricFamilySamples convert(String prometheusMetricName, Counter counter, Map<String, String> labels) {
        return new MetricFamilySamplesBuilder(Type.GAUGE, "")
                .addSample(prometheusMetricName + "_count", counter.count(), labels)
                .build();
    }

    private static MetricFamilySamples convert(String prometheusMetricName, Gauge<?> gauge, Map<String, String> labels, MetricName metricName) {
        Map<String, String> sanitizedLabels = MetricFamilySamplesBuilder.sanitizeLabels(labels);
        Object valueObj = gauge.value();
        double value;
        if (valueObj instanceof Number) {
            value = ((Number) valueObj).doubleValue();
        } else {
            value = 1.0;
            String attributeName = metricName.getName();
            sanitizedLabels.put(Collector.sanitizeMetricName(attributeName), String.valueOf(valueObj));
        }

        return new MetricFamilySamplesBuilder(Type.GAUGE, "")
                .addSample(prometheusMetricName, value, sanitizedLabels)
                .build();
    }

    static MetricFamilySamples convert(String prometheusMetricName, Meter meter, Map<String, String> labels) {
        return new MetricFamilySamplesBuilder(Type.COUNTER, "")
                .addSample(prometheusMetricName + "_count", meter.count(), labels)
                .build();
    }

    static MetricFamilySamples convert(String prometheusMetricName, Histogram histogram, Map<String, String> labels) {
        return new MetricFamilySamplesBuilder(Type.SUMMARY, "")
                .addSample(prometheusMetricName + "_count", histogram.count(), labels)
                .addQuantileSamples(prometheusMetricName, histogram.getSnapshot(), labels)
                .build();
    }

    static MetricFamilySamples convert(String prometheusMetricName, Timer metric, Map<String, String> labels) {
        return new MetricFamilySamplesBuilder(Type.SUMMARY, "")
                .addSample(prometheusMetricName + "_count", metric.count(), labels)
                .addQuantileSamples(prometheusMetricName, metric.getSnapshot(), labels)
                .build();
    }
}
