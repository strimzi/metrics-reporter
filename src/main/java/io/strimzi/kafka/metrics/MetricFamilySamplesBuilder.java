/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import com.yammer.metrics.stats.Snapshot;
import io.prometheus.client.Collector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MetricFamilySamplesBuilder {

    private final Collector.Type type;
    private final String help;
    private final List<Collector.MetricFamilySamples.Sample> samples;

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
}
