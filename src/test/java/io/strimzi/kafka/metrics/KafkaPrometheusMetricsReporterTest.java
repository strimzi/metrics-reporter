/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import io.prometheus.metrics.model.registry.PrometheusRegistry;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.metrics.Gauge;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.KafkaMetricsContext;
import org.apache.kafka.common.metrics.Measurable;
import org.apache.kafka.common.metrics.MetricConfig;
import org.apache.kafka.common.utils.Time;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KafkaPrometheusMetricsReporterTest {
    private final MetricConfig metricConfig = new MetricConfig();
    private final Time time = Time.SYSTEM;
    private final Map<String, String> labels = Collections.singletonMap("key", "value");

    @Test
    public void testLifeCycle() throws Exception {
        KafkaPrometheusMetricsReporter reporter = new KafkaPrometheusMetricsReporter(new PrometheusRegistry());
        Map<String, String> configs = new HashMap<>();
        configs.put(PrometheusMetricsReporterConfig.LISTENER_CONFIG, "http://:0");
        configs.put(PrometheusMetricsReporterConfig.ALLOWLIST_CONFIG, "kafka_server_group_name.*");
        reporter.configure(configs);
        reporter.contextChange(new KafkaMetricsContext("kafka.server"));

        Optional<Integer> port = reporter.getPort();
        assertTrue(port.isPresent());

        int initialMetrics = getMetrics(port.get()).size();
        KafkaMetric metric1 = buildMetric("name1", "group", 0);
        reporter.init(Collections.singletonList(metric1));

        List<String> metrics = getMetrics(port.get());
        assertEquals(initialMetrics + 1, metrics.size());

        KafkaMetric metric2 = buildMetric("name2", "group", 0);
        reporter.metricChange(metric2);
        metrics = getMetrics(port.get());
        assertEquals(initialMetrics + 2, metrics.size());

        KafkaMetric metric3 = buildNonNumericMetric("name3", "group");
        reporter.metricChange(metric3);
        metrics = getMetrics(port.get());
        assertEquals(initialMetrics + 3, metrics.size());

        reporter.metricRemoval(metric1);
        metrics = getMetrics(port.get());
        assertEquals(initialMetrics + 2, metrics.size());

        reporter.close();
    }

    @Test
    public void testMultipleReporters() throws Exception {
        Map<String, String> configs = new HashMap<>();
        configs.put(PrometheusMetricsReporterConfig.LISTENER_CONFIG, "http://:0");
        configs.put(PrometheusMetricsReporterConfig.ALLOWLIST_CONFIG, "kafka_server_group_name.*");

        KafkaPrometheusMetricsReporter reporter1 = new KafkaPrometheusMetricsReporter(new PrometheusRegistry());
        reporter1.configure(configs);
        reporter1.contextChange(new KafkaMetricsContext("kafka.server"));
        Optional<Integer> port = reporter1.getPort();
        assertTrue(port.isPresent());
        int initialMetrics = getMetrics(port.get()).size();

        KafkaPrometheusMetricsReporter reporter2 = new KafkaPrometheusMetricsReporter(new PrometheusRegistry());
        configs.put(PrometheusMetricsReporterConfig.LISTENER_CONFIG, "http://:0");
        reporter2.configure(configs);
        reporter2.contextChange(new KafkaMetricsContext("kafka.server"));

        KafkaMetric metric1 = buildMetric("name1", "group", 0);
        reporter1.init(Collections.singletonList(metric1));

        KafkaMetric metric2 = buildMetric("name2", "group", 0);
        reporter2.init(Collections.singletonList(metric2));

        int endMetrics = getMetrics(port.get()).size();
        assertTrue(initialMetrics < endMetrics);

        reporter1.close();
        reporter2.close();
    }

    private KafkaMetric buildMetric(String name, String group, double value) {
        Measurable measurable = (config, now) -> value;
        return new KafkaMetric(
                new Object(),
                new MetricName(name, group, "", labels),
                measurable,
                metricConfig,
                time);
    }

    private KafkaMetric buildNonNumericMetric(String name, String group) {
        Gauge<String> measurable = (config, now) -> "hello";
        return new KafkaMetric(
                new Object(),
                new MetricName(name, group, "", labels),
                measurable,
                metricConfig,
                time);
    }

    private List<String> getMetrics(int port) throws Exception {
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

}
