/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.prometheus.yammer;

import com.yammer.metrics.core.Metric;
import com.yammer.metrics.core.MetricName;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.PrometheusNaming;
import io.strimzi.kafka.metrics.prometheus.common.MetricWrapper;
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
     * @param metricName The Yammer MetricName
     * @param metric The Yammer metric
     * @param attribute The attribute of the Yammer metric
     */
    public YammerMetricWrapper(String prometheusName, MetricName metricName, Metric metric, String attribute) {
        super(prometheusName, labelsFromScopeAndMBeanName(metricName.getScope(), metricName.getMBeanName(), prometheusName), metric, attribute);
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

    static Labels labelsFromScopeAndMBeanName(String scope, String mbeanName, String metricName) {
        // Example scope: "type.kafka.name.BytesInPerSec"
        Set<String> labelKeys = new HashSet<>();
        if (scope != null) {
            String[] parts = scope.split("\\.");
            for (int i = 0; i < parts.length - 1; i += 2) {
                // Example labelKeys = {"type", "name"}
                labelKeys.add(parts[i]);
            }
        }
        if (labelKeys.isEmpty() || mbeanName == null) return Labels.EMPTY;
        Labels.Builder builder = Labels.builder();
        Set<String> labelNames = new HashSet<>();
        // Example mbeanName: "kafka.server:type=BrokerTopicMetrics,name=BytesInPerSec,topic=my-topic"
        int colonIdx = mbeanName.indexOf(':');
        if (colonIdx >= 0) {
            for (String property : mbeanName.substring(colonIdx + 1).split(",")) {
                int eqIdx = property.indexOf('=');
                if (eqIdx < 0) continue;
                String key = property.substring(0, eqIdx);
                String value = property.substring(eqIdx + 1);
                // Example labels: {type="BrokerTopicMetrics", name="BytesInPerSec"} - topic label is ignored as not in scope
                if (!labelKeys.contains(key)) continue;
                String labelName = PrometheusNaming.sanitizeLabelName(key);
                if (labelNames.add(labelName)) {
                    builder.label(labelName, value);
                } else {
                    LOG.warn("Ignoring duplicate label key: {} with value: {} from metric: {} ", labelName, value, metricName);
                }
            }
        }
        return builder.build();
    }
}
