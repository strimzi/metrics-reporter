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

                String name = metricName(metricName);
                // TODO Filtering should take labels into account
                if (!config.isAllowed(name)) {
                    LOG.info("Yammer metric {} is not allowed", name);
                    continue;
                }
                LOG.info("Yammer metric {} is allowed", name);
                Map<String, String> labels = labelsFromScope(metricName.getScope());
                LOG.info("labels " + labels);

                MetricFamilySamples sample = null;
                if (metric instanceof Counter) {
                    sample = convert(name, (Counter) metric, labels);
                } else if (metric instanceof Gauge) {
                    sample = convert(name, (Gauge<?>) metric, labels);
                } else if (metric instanceof Histogram) {
                    sample = convert(name, (Histogram) metric, labels);
                } else if (metric instanceof Meter) {
                    sample = convert(name, (Meter) metric, labels);
                } else if (metric instanceof Timer) {
                    sample = convert(name, (Timer) metric, labels);
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

    static MetricFamilySamples convert(String name, Counter counter, Map<String, String> labels) {
        return new MetricFamilySamplesBuilder(Type.GAUGE, "")
                .addSample(name + "_count", counter.count(), labels)
                .build();
    }

    static MetricFamilySamples convert(String name, Gauge<?> gauge, Map<String, String> labels) {
        Object value = gauge.value();
        if (!(value instanceof Number)) {
            // Prometheus only accepts numeric metrics.
            // Some Kafka gauges have string values (for example kafka.server:type=KafkaServer,name=ClusterId), so skip them
            return null;
        }
        return new MetricFamilySamplesBuilder(Type.GAUGE, "")
                .addSample(name, ((Number) value).doubleValue(), labels)
                .build();
    }

    static MetricFamilySamples convert(String name, Meter meter, Map<String, String> labels) {
        return new MetricFamilySamplesBuilder(Type.COUNTER, "")
                .addSample(name + "_count", meter.count(), labels)
                .build();
    }

    static MetricFamilySamples convert(String name, Histogram histogram, Map<String, String> labels) {
        return new MetricFamilySamplesBuilder(Type.SUMMARY, "")
                .addSample(name + "_count", histogram.count(), labels)
                .addQuantileSamples(name, histogram.getSnapshot(), labels)
                .build();
    }

    static MetricFamilySamples convert(String name, Timer metric, Map<String, String> labels) {
        return new MetricFamilySamplesBuilder(Type.SUMMARY, "")
                .addSample(name + "_count", metric.count(), labels)
                .addQuantileSamples(name, metric.getSnapshot(), labels)
                .build();
    }
}
