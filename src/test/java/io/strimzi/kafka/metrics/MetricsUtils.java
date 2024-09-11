/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.metrics.Gauge;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.MetricConfig;
import org.apache.kafka.common.utils.Time;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Utility class to create and retrieve metrics
 */
public class MetricsUtils {

    /**
     * Query the HTTP endpoint and returns the output
     * @param port The port to query
     * @return The lines from the output
     * @throws Exception If any error occurs
     */
    public static List<String> getMetrics(int port) throws Exception {
        List<String> metrics = new ArrayList<>();
        URL url = new URL("http://localhost:" + port + "/metrics");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");

        try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                if (!inputLine.startsWith("#")) {
                    metrics.add(inputLine);
                }
            }
        }
        return metrics;
    }

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

    /**
     * Create a new Yammer metric
     * @param valueSupplier The supplier providing the value of the metric
     * @return The Yammer metric
     */
    public static <T> com.yammer.metrics.core.Gauge<T> newYammerMetric(Supplier<T> valueSupplier) {
        return new com.yammer.metrics.core.Gauge<>() {
            @Override
            public T value() {
                return valueSupplier.get();
            }
        };
    }

}
