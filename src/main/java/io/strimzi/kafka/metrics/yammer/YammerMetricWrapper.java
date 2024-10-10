/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.yammer;

import com.yammer.metrics.core.Metric;
import com.yammer.metrics.core.MetricName;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.PrometheusNaming;
import io.strimzi.kafka.metrics.MetricWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Wrapper for Yammer metrics
 */
public class YammerMetricWrapper extends MetricWrapper {

    private static final Logger LOG = LoggerFactory.getLogger(YammerMetricWrapper.class);

    /**
     * Constructor from Yammer Metrics
     * @param prometheusName The name of the metric in the prometheus format
     * @param scope The scope of the Yammer metric
     * @param metric The Yammer metric
     * @param attribute The attribute of the Yammer metric
     */
    public YammerMetricWrapper(String prometheusName, String scope, Metric metric, String attribute) {
        super(prometheusName, labelsFromScope(scope, prometheusName), metric, attribute);
    }

    /**
     * Compute the Prometheus name from a Yammer MetricName
     * @param metricName The Yammer metric name
     * @return The prometheus metric name
     */
    public static String prometheusName(MetricName metricName) {
        return PrometheusNaming.prometheusName(
                PrometheusNaming.sanitizeMetricName(
                        metricName.getGroup() + '_' +
                        metricName.getType() + '_' +
                        metricName.getName()).toLowerCase(Locale.ROOT));
    }

    static Labels labelsFromScope(String scope, String metricName) {
        Labels.Builder builder = Labels.builder();
        Set<String> labelNames = new HashSet<>();
        if (scope != null) {
            String[] parts = scope.split("\\.");
            if (parts.length % 2 == 0) {
                for (int i = 0; i < parts.length; i += 2) {
                    String newLabelName = PrometheusNaming.sanitizeLabelName(parts[i]);
                    if (labelNames.add(newLabelName)) {
                        builder.label(newLabelName, parts[i + 1]);
                    } else {
                        LOG.warn("Ignoring duplicate label key: {} with value: {} from metric: {} ", newLabelName, parts[i + 1], metricName);
                    }
                }
            }
        }
        return builder.build();
    }
}
