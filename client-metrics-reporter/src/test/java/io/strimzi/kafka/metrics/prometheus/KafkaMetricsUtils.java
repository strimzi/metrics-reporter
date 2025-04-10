/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.prometheus;

import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.metrics.Gauge;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.MetricConfig;
import org.apache.kafka.common.utils.Time;

import java.util.Map;


public class KafkaMetricsUtils {

    /**
     * Create a new Kafka metric
     * @param name The name of the metric
     * @param group The group of the metric
     * @param gauge The gauge providing the value of the metric
     * @param labels The labels of the metric
     * @return The Kafka metric
     */
    public static KafkaMetric newKafkaMetric(String name, String group, Gauge<?> gauge, Map<String, String> labels) {
        return new KafkaMetric(
                new Object(),
                new MetricName(name, group, "", labels),
                gauge,
                new MetricConfig(),
                Time.SYSTEM);
    }

}
