/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import com.yammer.metrics.core.Counter;
import com.yammer.metrics.core.Gauge;
import com.yammer.metrics.core.Histogram;
import com.yammer.metrics.core.Meter;
import com.yammer.metrics.core.MetricName;
import com.yammer.metrics.core.Sampling;
import com.yammer.metrics.core.Timer;
import io.prometheus.metrics.model.registry.MultiCollector;
import io.prometheus.metrics.model.snapshots.CounterSnapshot;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot;
import io.prometheus.metrics.model.snapshots.InfoSnapshot;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;
import io.prometheus.metrics.model.snapshots.Quantile;
import io.prometheus.metrics.model.snapshots.Quantiles;
import io.prometheus.metrics.model.snapshots.SummarySnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prometheus Collector to store and export metrics retrieved by {@link YammerPrometheusMetricsReporter}.
 */
@SuppressWarnings("ClassFanOutComplexity")
public class YammerMetricsCollector implements MultiCollector {

    private static final Logger LOG = LoggerFactory.getLogger(YammerMetricsCollector.class);
    private static final List<Double> QUANTILES = Arrays.asList(0.50, 0.75, 0.95, 0.98, 0.99, 0.999);

    private final Map<MetricName, MetricWrapper> metrics;

    /**
     * Constructor
     */
    public YammerMetricsCollector() {
        this.metrics = new ConcurrentHashMap<>();
    }

    /**
     * Called when the Prometheus server scrapes metrics.
     * @return metrics that match the configured allowlist
     */
    @Override
    @SuppressWarnings({"CyclomaticComplexity", "NPathComplexity", "JavaNCSS"})
    public MetricSnapshots collect() {
        Map<String, CounterSnapshot.Builder> counterBuilders = new HashMap<>();
        Map<String, GaugeSnapshot.Builder> gaugeBuilders = new HashMap<>();
        Map<String, InfoSnapshot.Builder> infoBuilders = new HashMap<>();
        Map<String, SummarySnapshot.Builder> summaryBuilders = new HashMap<>();

        for (Map.Entry<MetricName, MetricWrapper> entry : metrics.entrySet()) {
            MetricWrapper metricWrapper = entry.getValue();
            String prometheusMetricName = metricWrapper.prometheusName();
            Object metric = metricWrapper.metric();
            Labels labels = metricWrapper.labels();
            LOG.debug("Collecting metric {} with the following labels: {}", prometheusMetricName, labels);

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

    /**
     * Add a Yammer metric to be collected.
     *
     * @param name The name of the Yammer metric to add.
     * @param metric The Yammer metric to add.
     */
    public void addMetric(MetricName name, MetricWrapper metric) {
        metrics.put(name, metric);
    }

    /**
     * Remove a Yammer metric from collection.
     *
     * @param name The name of the Yammer metric to remove.
     */
    public void removeMetric(MetricName name) {
        metrics.remove(name);
    }

    private static Quantiles quantiles(Sampling sampling) {
        Quantiles.Builder quantilesBuilder = Quantiles.builder();
        for (double quantile : QUANTILES) {
            quantilesBuilder.quantile(new Quantile(quantile, sampling.getSnapshot().getValue(quantile)));
        }
        return quantilesBuilder.build();
    }
}
