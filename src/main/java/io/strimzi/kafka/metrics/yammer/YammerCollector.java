/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.yammer;

import com.yammer.metrics.core.Counter;
import com.yammer.metrics.core.Gauge;
import com.yammer.metrics.core.Histogram;
import com.yammer.metrics.core.Meter;
import com.yammer.metrics.core.MetricName;
import com.yammer.metrics.core.Sampling;
import com.yammer.metrics.core.Timer;
import io.prometheus.metrics.model.snapshots.CounterSnapshot;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot;
import io.prometheus.metrics.model.snapshots.InfoSnapshot;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;
import io.prometheus.metrics.model.snapshots.Quantile;
import io.prometheus.metrics.model.snapshots.Quantiles;
import io.prometheus.metrics.model.snapshots.SummarySnapshot;
import io.strimzi.kafka.metrics.DataPointSnapshotBuilder;
import io.strimzi.kafka.metrics.MetricWrapper;
import io.strimzi.kafka.metrics.MetricsCollector;
import io.strimzi.kafka.metrics.PrometheusCollector;
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
 * Collector for Yammer metrics
 */
@SuppressWarnings("ClassFanOutComplexity")
public class YammerCollector implements MetricsCollector {

    private static final Logger LOG = LoggerFactory.getLogger(YammerCollector.class);
    private static final YammerCollector INSTANCE = new YammerCollector();
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);
    private static final List<Double> QUANTILES = Arrays.asList(0.50, 0.75, 0.95, 0.98, 0.99, 0.999);

    private final Map<MetricName, MetricWrapper> yammerMetrics = new ConcurrentHashMap<>();

    /* for testing */ YammerCollector() {}

    /**
     * Retrieve the YammerCollector instance
     *
     * @param prometheusCollector the PrometheusCollector that will collect metrics
     * @return the YammerCollector singleton
     */
    public static YammerCollector getCollector(PrometheusCollector prometheusCollector) {
        if (REGISTERED.compareAndSet(false, true)) {
            prometheusCollector.addCollector(INSTANCE);
        }
        return INSTANCE;
    }

    /**
     * Add a Yammer metric to be collected.
     *
     * @param name The name of the Yammer metric to add.
     * @param metric The Yammer metric to add.
     */
    public void addMetric(MetricName name, MetricWrapper metric) {
        yammerMetrics.put(name, metric);
    }

    /**
     * Remove a Yammer metric from collection.
     *
     * @param name The name of the Yammer metric to remove.
     */
    public void removeMetric(MetricName name) {
        yammerMetrics.remove(name);
    }

    /**
     * Collect all the metrics added to this Collector
     *
     * @return the list of metrics of this collector
     */
    @SuppressWarnings({"CyclomaticComplexity", "JavaNCSS"})
    @Override
    public List<MetricSnapshot<?>> collect() {
        Map<String, MetricSnapshot.Builder<?>> builders = new HashMap<>();
        for (MetricWrapper metricWrapper : yammerMetrics.values()) {
            String prometheusMetricName = metricWrapper.prometheusName();
            Object metric = metricWrapper.metric();
            Labels labels = metricWrapper.labels();
            LOG.debug("Collecting Yammer metric {} with the following labels: {}", prometheusMetricName, labels);

            if (metric instanceof Counter) {
                Counter counter = (Counter) metric;
                CounterSnapshot.Builder builder = (CounterSnapshot.Builder) builders.computeIfAbsent(prometheusMetricName, k -> CounterSnapshot.builder().name(prometheusMetricName));
                builder.dataPoint(DataPointSnapshotBuilder.counterDataPoint(labels, counter.count()));
            } else if (metric instanceof Gauge) {
                Object valueObj = ((Gauge<?>) metric).value();
                if (valueObj instanceof Number) {
                    double value = ((Number) valueObj).doubleValue();
                    GaugeSnapshot.Builder builder = (GaugeSnapshot.Builder) builders.computeIfAbsent(prometheusMetricName, k -> GaugeSnapshot.builder().name(prometheusMetricName));
                    builder.dataPoint(DataPointSnapshotBuilder.gaugeDataPoint(labels, value));
                } else {
                    InfoSnapshot.Builder builder = (InfoSnapshot.Builder) builders.computeIfAbsent(prometheusMetricName, k -> InfoSnapshot.builder().name(prometheusMetricName));
                    builder.dataPoint(DataPointSnapshotBuilder.infoDataPoint(labels, valueObj, metricWrapper.attribute()));
                }
            } else if (metric instanceof Timer) {
                Timer timer = (Timer) metric;
                SummarySnapshot.Builder builder = (SummarySnapshot.Builder) builders.computeIfAbsent(prometheusMetricName, k -> SummarySnapshot.builder().name(prometheusMetricName));
                builder.dataPoint(DataPointSnapshotBuilder.summaryDataPoint(labels, timer.count(), timer.sum(), quantiles(timer)));
            } else if (metric instanceof Histogram) {
                Histogram histogram = (Histogram) metric;
                SummarySnapshot.Builder builder = (SummarySnapshot.Builder) builders.computeIfAbsent(prometheusMetricName, k -> SummarySnapshot.builder().name(prometheusMetricName));
                builder.dataPoint(DataPointSnapshotBuilder.summaryDataPoint(labels, histogram.count(), histogram.sum(), quantiles(histogram)));
            } else if (metric instanceof Meter) {
                Meter meter = (Meter) metric;
                CounterSnapshot.Builder builder = (CounterSnapshot.Builder) builders.computeIfAbsent(prometheusMetricName, k -> CounterSnapshot.builder().name(prometheusMetricName));
                builder.dataPoint(DataPointSnapshotBuilder.counterDataPoint(labels, meter.count()));
            } else {
                LOG.error("The metric {} has an unexpected type: {}", prometheusMetricName, metric.getClass().getName());
            }
        }
        List<MetricSnapshot<?>> snapshots = new ArrayList<>();
        for (MetricSnapshot.Builder<?> builder : builders.values()) {
            snapshots.add(builder.build());
        }
        return snapshots;
    }

    private static Quantiles quantiles(Sampling sampling) {
        Quantiles.Builder quantilesBuilder = Quantiles.builder();
        for (double quantile : QUANTILES) {
            quantilesBuilder.quantile(new Quantile(quantile, sampling.getSnapshot().getValue(quantile)));
        }
        return quantilesBuilder.build();
    }
}
