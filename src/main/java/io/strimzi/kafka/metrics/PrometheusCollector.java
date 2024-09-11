/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import com.yammer.metrics.core.Counter;
import com.yammer.metrics.core.Gauge;
import com.yammer.metrics.core.Histogram;
import com.yammer.metrics.core.Meter;
import com.yammer.metrics.core.Sampling;
import com.yammer.metrics.core.Timer;
import io.prometheus.metrics.instrumentation.jvm.JvmMetrics;
import io.prometheus.metrics.model.registry.MultiCollector;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.CounterSnapshot;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot;
import io.prometheus.metrics.model.snapshots.InfoSnapshot;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;
import io.prometheus.metrics.model.snapshots.Quantile;
import io.prometheus.metrics.model.snapshots.Quantiles;
import io.prometheus.metrics.model.snapshots.SummarySnapshot;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Prometheus Collector to store and export metrics retrieved by {@link KafkaPrometheusMetricsReporter}
 * and {@link YammerPrometheusMetricsReporter}.
 */
@SuppressWarnings("ClassFanOutComplexity")
public class PrometheusCollector implements MultiCollector {

    private static final Logger LOG = LoggerFactory.getLogger(PrometheusCollector.class);
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);
    private static final PrometheusCollector INSTANCE = new PrometheusCollector();
    private static final List<Double> QUANTILES = Arrays.asList(0.50, 0.75, 0.95, 0.98, 0.99, 0.999);

    private final Map<MetricName, MetricWrapper> kafkaMetrics = new ConcurrentHashMap<>();
    private final Map<com.yammer.metrics.core.MetricName, MetricWrapper> yammerMetrics = new ConcurrentHashMap<>();

    /**
     * Add a Kafka metric to be collected.
     *
     * @param name The name of the Kafka metric to add.
     * @param metric The Kafka metric to add.
     */
    public void addKafkaMetric(MetricName name, MetricWrapper metric) {
        kafkaMetrics.put(name, metric);
    }

    /**
     * Remove a Kafka metric from collection.
     *
     * @param name The name of Kafka metric to remove.
     */
    public void removeKafkaMetric(MetricName name) {
        kafkaMetrics.remove(name);
    }

    /**
     * Add a Yammer metric to be collected.
     *
     * @param name The name of the Yammer metric to add.
     * @param metric The Yammer metric to add.
     */
    public void addYammerMetric(com.yammer.metrics.core.MetricName name, MetricWrapper metric) {
        yammerMetrics.put(name, metric);
    }

    /**
     * Remove a Yammer metric from collection.
     *
     * @param name The name of the Yammer metric to remove.
     */
    public void removeYammerMetric(com.yammer.metrics.core.MetricName name) {
        yammerMetrics.remove(name);
    }

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
     * Called when the Prometheus server scrapes metrics.
     * @return MetricSnapshots that contains the metrics
     */
    @Override
    public MetricSnapshots collect() {
        Map<String, CounterSnapshot.Builder> counterBuilders = new HashMap<>();
        Map<String, GaugeSnapshot.Builder> gaugeBuilders = new HashMap<>();
        Map<String, InfoSnapshot.Builder> infoBuilders = new HashMap<>();
        Map<String, SummarySnapshot.Builder> summaryBuilders = new HashMap<>();

        collectKafkaMetrics(gaugeBuilders, infoBuilders);
        collectYammerMetrics(counterBuilders, gaugeBuilders, infoBuilders, summaryBuilders);

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

    private void collectKafkaMetrics(Map<String, GaugeSnapshot.Builder> gaugeBuilders,
                                     Map<String, InfoSnapshot.Builder> infoBuilders) {
        for (MetricWrapper metricWrapper : kafkaMetrics.values()) {
            String prometheusMetricName = metricWrapper.prometheusName();
            Object metricValue = ((KafkaMetric) metricWrapper.metric()).metricValue();
            Labels labels = metricWrapper.labels();
            LOG.debug("Collecting Kafka metric {} with the following labels: {}", prometheusMetricName, labels);

            if (metricValue instanceof Number) {
                double value = ((Number) metricValue).doubleValue();
                GaugeSnapshot.Builder builder = gaugeBuilders.computeIfAbsent(prometheusMetricName, k -> GaugeSnapshot.builder().name(prometheusMetricName));
                builder.dataPoint(DataPointSnapshotBuilder.gaugeDataPoint(labels, value));
            } else {
                InfoSnapshot.Builder builder = infoBuilders.computeIfAbsent(prometheusMetricName, k -> InfoSnapshot.builder().name(prometheusMetricName));
                builder.dataPoint(DataPointSnapshotBuilder.infoDataPoint(labels, metricValue, metricWrapper.attribute()));
            }
        }
    }

    private void collectYammerMetrics(Map<String, CounterSnapshot.Builder> counterBuilders,
                                      Map<String, GaugeSnapshot.Builder> gaugeBuilders,
                                      Map<String, InfoSnapshot.Builder> infoBuilders,
                                      Map<String, SummarySnapshot.Builder> summaryBuilders) {
        for (MetricWrapper metricWrapper : yammerMetrics.values()) {
            String prometheusMetricName = metricWrapper.prometheusName();
            Object metric = metricWrapper.metric();
            Labels labels = metricWrapper.labels();
            LOG.debug("Collecting Yammer metric {} with the following labels: {}", prometheusMetricName, labels);

            if (metric instanceof Counter) {
                Counter counter = (Counter) metric;
                CounterSnapshot.Builder builder = counterBuilders.computeIfAbsent(prometheusMetricName, k -> CounterSnapshot.builder().name(prometheusMetricName));
                builder.dataPoint(DataPointSnapshotBuilder.counterDataPoint(labels, counter.count()));
            } else if (metric instanceof Gauge) {
                Object valueObj = ((Gauge<?>) metric).value();
                if (valueObj instanceof Number) {
                    double value = ((Number) valueObj).doubleValue();
                    GaugeSnapshot.Builder builder = gaugeBuilders.computeIfAbsent(prometheusMetricName, k -> GaugeSnapshot.builder().name(prometheusMetricName));
                    builder.dataPoint(DataPointSnapshotBuilder.gaugeDataPoint(labels, value));
                } else {
                    InfoSnapshot.Builder builder = infoBuilders.computeIfAbsent(prometheusMetricName, k -> InfoSnapshot.builder().name(prometheusMetricName));
                    builder.dataPoint(DataPointSnapshotBuilder.infoDataPoint(labels, valueObj, metricWrapper.attribute()));
                }
            } else if (metric instanceof Timer) {
                Timer timer = (Timer) metric;
                SummarySnapshot.Builder builder = summaryBuilders.computeIfAbsent(prometheusMetricName, k -> SummarySnapshot.builder().name(prometheusMetricName));
                builder.dataPoint(DataPointSnapshotBuilder.summaryDataPoint(labels, timer.count(), timer.sum(), quantiles(timer)));
            } else if (metric instanceof Histogram) {
                Histogram histogram = (Histogram) metric;
                SummarySnapshot.Builder builder = summaryBuilders.computeIfAbsent(prometheusMetricName, k -> SummarySnapshot.builder().name(prometheusMetricName));
                builder.dataPoint(DataPointSnapshotBuilder.summaryDataPoint(labels, histogram.count(), histogram.sum(), quantiles(histogram)));
            } else if (metric instanceof Meter) {
                Meter meter = (Meter) metric;
                CounterSnapshot.Builder builder = counterBuilders.computeIfAbsent(prometheusMetricName, k -> CounterSnapshot.builder().name(prometheusMetricName));
                builder.dataPoint(DataPointSnapshotBuilder.counterDataPoint(labels, meter.count()));
            } else {
                LOG.error("The metric {} has an unexpected type: {}", prometheusMetricName, metric.getClass().getName());
            }
        }
    }

    private static Quantiles quantiles(Sampling sampling) {
        Quantiles.Builder quantilesBuilder = Quantiles.builder();
        for (double quantile : QUANTILES) {
            quantilesBuilder.quantile(new Quantile(quantile, sampling.getSnapshot().getValue(quantile)));
        }
        return quantilesBuilder.build();
    }
}
