/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import com.yammer.metrics.stats.Snapshot;
import io.prometheus.client.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Helper class to convert Kafka metrics into the Prometheus format.
 */
public class MetricFamilySamplesBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaMetricsCollector.class.getName());
    private final Collector.Type type;
    private final String help;
    private final List<Collector.MetricFamilySamples.Sample> samples;

    /**
     * Constructor for MetricFamilySamplesBuilder.
     *
     * @param type The type of the metric family.
     * @param help The help string for the metric family.
     */
    public MetricFamilySamplesBuilder(Collector.Type type, String help) {
        this.type = type;
        this.help = help;
        this.samples = new ArrayList<>();
    }

    MetricFamilySamplesBuilder addSample(String name, double value, Map<String, String> labels) {
        samples.add(new Collector.MetricFamilySamples.Sample(
                        Collector.sanitizeMetricName(name),
                        new ArrayList<>(labels.keySet()),
                        new ArrayList<>(labels.values()),
                        value));
        return this;
    }

    MetricFamilySamplesBuilder addQuantileSamples(String name, Snapshot snapshot, Map<String, String> labels) {
        for (String quantile : Arrays.asList("0.50", "0.75", "0.95", "0.98", "0.99", "0.999")) {
            Map<String, String> newLabels = new HashMap<>(labels);
            newLabels.put("quantile", quantile);
            addSample(name, snapshot.getValue(Double.parseDouble(quantile)), newLabels);
        }
        return this;
    }

    Collector.MetricFamilySamples build() {
        if (samples.isEmpty()) {
            throw new IllegalStateException("There are no samples");
        }
        return new Collector.MetricFamilySamples(samples.get(0).name, type, help, samples);
    }

    /**
     * Sanitizes the given map of labels by replacing any characters in the label keys
     * that are not allowed in Prometheus metric names with an underscore ('_').
     * If there are duplicate keys after sanitization, a warning is logged, and the first value is retained.
     *
     * @param labels The map of labels to be sanitized. The keys of this map are label names,
     *               and the values are label values.
     * @return A new map with sanitized label names and the same label values.
     */
    public static Map<String, String> sanitizeLabels(Map<String, String> labels) {
        return labels.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> Collector.sanitizeMetricName(e.getKey()),
                        Map.Entry::getValue,
                        (v1, v2) -> {
                            LOG.warn("Ignoring metric value duplicate key {}", v1);
                            return v1;
                        },
                        LinkedHashMap::new));
    }
}
