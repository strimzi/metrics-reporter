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
import io.prometheus.metrics.model.registry.MultiCollector;
import io.prometheus.metrics.model.snapshots.CounterSnapshot;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot;
import io.prometheus.metrics.model.snapshots.InfoSnapshot;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;
import io.prometheus.metrics.model.snapshots.PrometheusNaming;
import io.prometheus.metrics.model.snapshots.SummarySnapshot;
import org.apache.kafka.server.metrics.KafkaYammerMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Prometheus Collector to store and export metrics retrieved by {@link YammerPrometheusMetricsReporter}.
 */
public class YammerMetricsCollector implements MultiCollector {

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

    /**
     * Called when the Prometheus server scrapes metrics.
     * @return metrics that match the configured allowlist
     */
    @Override
    public MetricSnapshots collect() {
        Map<String, CounterSnapshot.Builder> counterBuilders = new HashMap<>();
        Map<String, GaugeSnapshot.Builder> gaugeBuilders = new HashMap<>();
        Map<String, InfoSnapshot.Builder> infoBuilders = new HashMap<>();
        Map<String, SummarySnapshot.Builder> summaryBuilders = new HashMap<>();

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
                Labels labels = labelsFromScope(metricName.getScope());
                LOG.info("labels {}", labels);

                if (metric instanceof Counter) {
                    CounterSnapshot.Builder builder = counterBuilders.computeIfAbsent(prometheusMetricName, k -> CounterSnapshot.builder().name(prometheusMetricName));
                    builder.dataPoint(DataPointSnapshotBuilder.convert((Number) ((Counter) metric).count(), labels));
                } else if (metric instanceof Gauge) {
                    Object valueObj = ((Gauge<?>) metric).value();
                    if (valueObj instanceof Number) {
                        double value = ((Number) valueObj).doubleValue();
                        GaugeSnapshot.Builder builder = gaugeBuilders.computeIfAbsent(prometheusMetricName, k -> GaugeSnapshot.builder().name(prometheusMetricName));
                        builder.dataPoint(DataPointSnapshotBuilder.convert(value, labels));
                    } else {
                        InfoSnapshot.Builder builder = infoBuilders.computeIfAbsent(prometheusMetricName, k -> InfoSnapshot.builder().name(prometheusMetricName));
                        builder.dataPoint(DataPointSnapshotBuilder.convert(valueObj, labels, metricName.getName()));
                    }
                } else if (metric instanceof Timer) {
                    SummarySnapshot.Builder builder = summaryBuilders.computeIfAbsent(prometheusMetricName, k -> SummarySnapshot.builder().name(prometheusMetricName));
                    builder.dataPoint(DataPointSnapshotBuilder.convert((Timer) metric, labels));
                } else if (metric instanceof Histogram) {
                    SummarySnapshot.Builder builder = summaryBuilders.computeIfAbsent(prometheusMetricName, k -> SummarySnapshot.builder().name(prometheusMetricName));
                    builder.dataPoint(DataPointSnapshotBuilder.convert((Histogram) metric, labels));
                } else if (metric instanceof Meter) {
                    CounterSnapshot.Builder builder = counterBuilders.computeIfAbsent(prometheusMetricName, k -> CounterSnapshot.builder().name(prometheusMetricName));
                    builder.dataPoint(DataPointSnapshotBuilder.convert((Number) ((Meter) metric).count(), labels));
                } else {
                    LOG.error("The metric {} has an unexpected type.", metric.getClass().getName());
                }
            }
        }
        List<MetricSnapshot> snapshots = new ArrayList<>();
        for (GaugeSnapshot.Builder builder : gaugeBuilders.values()) {
            snapshots.add(builder.build());
        }
        for (CounterSnapshot.Builder builder : counterBuilders.values()) {
            snapshots.add(builder.build());
        }
        for (InfoSnapshot.Builder builder : infoBuilders.values()) {
            snapshots.add(builder.build());
        }
        for (SummarySnapshot.Builder builder : summaryBuilders.values()) {
            snapshots.add(builder.build());
        }
        return new MetricSnapshots(snapshots);
    }

    private static String metricName(MetricName metricName) {
        String metricNameStr = PrometheusNaming.sanitizeMetricName(
                "kafka_server_" +
                metricName.getGroup() + '_' +
                metricName.getType() + '_' +
                metricName.getName()).toLowerCase(Locale.ROOT);
        LOG.info("metricName group {}, type {}, name {} converted into {}", metricName.getGroup(), metricName.getType(), metricName.getName(), metricNameStr);
        return metricNameStr;
    }

    static Labels labelsFromScope(String scope) {
        Labels.Builder builder = Labels.builder();
        if (scope != null) {
            String[] parts = scope.split("\\.");
            if (parts.length % 2 == 0) {
                for (int i = 0; i < parts.length; i += 2) {
                    builder.label(PrometheusNaming.sanitizeLabelName(parts[i]), parts[i + 1]);
                }
            }
        }
        return builder.build();
    }
}
