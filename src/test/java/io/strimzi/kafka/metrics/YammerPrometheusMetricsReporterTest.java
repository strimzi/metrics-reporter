/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Counter;
import com.yammer.metrics.core.Metric;
import com.yammer.metrics.core.MetricName;
import com.yammer.metrics.core.MetricsRegistry;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import kafka.utils.VerifiableProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class YammerPrometheusMetricsReporterTest {

    private final MetricsRegistry registry = Metrics.defaultRegistry();

    @BeforeEach
    public void setup() {
        for (Map.Entry<MetricName, Metric> entry : registry.allMetrics().entrySet()) {
            registry.removeMetric(entry.getKey());
        }
    }

    @Test
    public void testLifeCycle() throws Exception {
        YammerPrometheusMetricsReporter reporter = new YammerPrometheusMetricsReporter(new PrometheusRegistry());
        Properties configs = new Properties();
        configs.put(PrometheusMetricsReporterConfig.LISTENER_CONFIG, "http://:0");
        configs.put(PrometheusMetricsReporterConfig.ALLOWLIST_CONFIG, "kafka_server_group_type.*");
        reporter.init(new VerifiableProperties(configs));

        HttpServers.ServerCounter httpServer = null;
        try {
            httpServer = reporter.config.startHttpServer().orElseThrow();
            int port = httpServer.port();
            assertEquals(0, getMetrics(port).size());

            // Adding a metric not matching the allowlist does nothing
            newCounter("other", "type", "name");
            List<String> metrics = getMetrics(port);
            assertEquals(0, metrics.size());

            // Adding a metric that matches the allowlist
            newCounter("group", "type", "name");
            metrics = getMetrics(port);
            assertEquals(1, metrics.size());
            assertEquals("kafka_server_group_type_name_total 0.0", metrics.get(0));

            // Removing the metric
            removeMetric("group", "type", "name");
            metrics = getMetrics(port);
            assertEquals(0, metrics.size());
        } finally {
            if (httpServer != null) HttpServers.release(httpServer);
        }
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

    private Counter newCounter(String group, String type, String name) {
        MetricName metricName = new MetricName(group, type, name, "");
        return registry.newCounter(metricName);
    }

    private void removeMetric(String group, String type, String name) {
        MetricName metricName = new MetricName(group, type, name, "");
        registry.removeMetric(metricName);
    }
}
