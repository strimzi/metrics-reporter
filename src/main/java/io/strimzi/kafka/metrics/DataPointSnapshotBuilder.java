/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import io.prometheus.metrics.model.snapshots.CounterSnapshot;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot;
import io.prometheus.metrics.model.snapshots.InfoSnapshot;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.PrometheusNaming;
import io.prometheus.metrics.model.snapshots.Quantiles;
import io.prometheus.metrics.model.snapshots.SummarySnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builder methods to convert Kafka metrics into Prometheus metrics
 */
public class DataPointSnapshotBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(DataPointSnapshotBuilder.class);

    /**
     * Create a datapoint for a {@link InfoSnapshot} metric
     * @param labels The labels associated with the datapoint
     * @param value The value to insert as a label
     * @param metricName The name of the new label
     * @return The {@link InfoSnapshot.InfoDataPointSnapshot} datapoint
     */
    public static InfoSnapshot.InfoDataPointSnapshot infoDataPoint(Labels labels, Object value, String metricName) {
        String newLabelName = PrometheusNaming.sanitizeLabelName(metricName);
        Labels newLabels = labels;
        String existingValue = labels.get(newLabelName);
        if (existingValue != null) {
            if (!String.valueOf(value).equals(existingValue)) {
                LOG.warn("Unable to add new label because of duplicate key: {} with value: {} from metric: {}",
                        newLabelName, value, metricName);
            }
        } else {
            newLabels = labels.add(newLabelName, String.valueOf(value));
        }
        return InfoSnapshot.InfoDataPointSnapshot.builder()
                .labels(newLabels)
                .build();
    }

    /**
     * Create a datapoint for a {@link GaugeSnapshot} metric
     * @param labels The labels associated with the datapoint
     * @param value The gauge value
     * @return The {@link GaugeSnapshot.GaugeDataPointSnapshot} datapoint
     */
    public static GaugeSnapshot.GaugeDataPointSnapshot gaugeDataPoint(Labels labels, double value) {
        return GaugeSnapshot.GaugeDataPointSnapshot.builder()
                .value(value)
                .labels(labels)
                .build();
    }

    /**
     * Create a datapoint for a {@link CounterSnapshot} metric
     * @param labels The labels associated with the datapoint
     * @param number The counter value
     * @return The {@link CounterSnapshot.CounterDataPointSnapshot} datapoint
     */
    public static CounterSnapshot.CounterDataPointSnapshot counterDataPoint(Labels labels, Number number) {
        return CounterSnapshot.CounterDataPointSnapshot.builder()
                .labels(labels)
                .value(number.doubleValue())
                .build();
    }

    /**
     * Create a datapoint for a {@link SummarySnapshot} metric
     * @param labels The labels associated with the datapoint
     * @param count The summary count
     * @param sum The summary sum
     * @param quantiles The summary quantiles
     * @return The {@link SummarySnapshot.SummaryDataPointSnapshot} datapoint
     */
    public static SummarySnapshot.SummaryDataPointSnapshot summaryDataPoint(Labels labels, long count, double sum, Quantiles quantiles) {
        return SummarySnapshot.SummaryDataPointSnapshot.builder()
                .labels(labels)
                .count(count)
                .sum(sum)
                .quantiles(quantiles)
                .build();
    }

}
