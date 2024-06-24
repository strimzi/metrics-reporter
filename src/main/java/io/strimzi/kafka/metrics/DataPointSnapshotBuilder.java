/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import com.yammer.metrics.core.Histogram;
import com.yammer.metrics.core.Sampling;
import com.yammer.metrics.core.Timer;
import io.prometheus.metrics.model.snapshots.CounterSnapshot;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot;
import io.prometheus.metrics.model.snapshots.InfoSnapshot;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.PrometheusNaming;
import io.prometheus.metrics.model.snapshots.Quantile;
import io.prometheus.metrics.model.snapshots.Quantiles;
import io.prometheus.metrics.model.snapshots.SummarySnapshot;

import java.util.Arrays;

/**
 * Builder methods to convert Kafka metrics into Prometheus metrics
 */
public class DataPointSnapshotBuilder {

    /**
     * Convert a non numeric metric into a datapoint for a {@link InfoSnapshot} metric
     * @param value The value of the datapoint
     * @param labels Labels associated with the datapoint
     * @param metricName The name of the new label
     * @return The {@link InfoSnapshot.InfoDataPointSnapshot} datapoint
     */
    public static InfoSnapshot.InfoDataPointSnapshot convert(Object value, Labels labels, String metricName) {
        labels = labels.add(PrometheusNaming.sanitizeLabelName(metricName), String.valueOf(value));
        return InfoSnapshot.InfoDataPointSnapshot.builder()
                .labels(labels)
                .build();
    }

    /**
     * Convert a value into a datapoint for a {@link GaugeSnapshot} metric
     * @param value The value
     * @param labels Labels associated with the datapoint
     * @return The {@link GaugeSnapshot.GaugeDataPointSnapshot} datapoint
     */
    public static GaugeSnapshot.GaugeDataPointSnapshot convert(double value, Labels labels) {
        return GaugeSnapshot.GaugeDataPointSnapshot.builder()
                .value(value)
                .labels(labels)
                .build();
    }

    /**
     * Convert a Number into a datapoint for a {@link CounterSnapshot} metric
     * @param number The value
     * @param labels Labels associated with the datapoint
     * @return The {@link CounterSnapshot.CounterDataPointSnapshot} datapoint
     */
    public static CounterSnapshot.CounterDataPointSnapshot convert(Number number, Labels labels) {
        return CounterSnapshot.CounterDataPointSnapshot.builder()
                .labels(labels)
                .value(number.doubleValue())
                .build();
    }

    /**
     * Convert a Timer into a datapoint for a {@link SummarySnapshot} metric
     * @param timer The timer to convert
     * @param labels Labels associated with the datapoint
     * @return The {@link SummarySnapshot.SummaryDataPointSnapshot} datapoint
     */
    public static SummarySnapshot.SummaryDataPointSnapshot convert(Timer timer, Labels labels) {
        return SummarySnapshot.SummaryDataPointSnapshot.builder()
                .labels(labels)
                .quantiles(quantiles(timer))
                .count(timer.count())
                .sum(timer.sum())
                .build();
    }

    /**
     * Convert an Histogram into a datapoint for a {@link SummarySnapshot} metric
     * @param histogram The histogram to convert
     * @param labels Labels associated with the datapoint
     * @return The {@link SummarySnapshot.SummaryDataPointSnapshot} datapoint
     */
    public static SummarySnapshot.SummaryDataPointSnapshot convert(Histogram histogram, Labels labels) {
        return SummarySnapshot.SummaryDataPointSnapshot.builder()
                .labels(labels)
                .quantiles(quantiles(histogram))
                .count(histogram.count())
                .sum(histogram.sum())
                .build();
    }

    private static Quantiles quantiles(Sampling samplingMetric) {
        Quantiles.Builder quantilesBuilder = Quantiles.builder();
        for (double quantile : Arrays.asList(0.50, 0.75, 0.95, 0.98, 0.99, 0.999)) {
            quantilesBuilder.quantile(new Quantile(quantile, samplingMetric.getSnapshot().getValue(quantile)));
        }
        return quantilesBuilder.build();
    }
}
