/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Counter;
import com.yammer.metrics.core.Metric;
import com.yammer.metrics.core.MetricName;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.strimzi.kafka.metrics.http.HttpServers;
import kafka.utils.VerifiableProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import static io.strimzi.kafka.metrics.TestUtils.getMetrics;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class YammerPrometheusMetricsReporterTest {

    private Properties configs;
    private PrometheusRegistry registry;
    private PrometheusCollector collector;

    @BeforeEach
    public void setup() {
        registry = new PrometheusRegistry();
        collector = new PrometheusCollector();
        registry.register(collector);
        configs = new Properties();
        configs.put(PrometheusMetricsReporterConfig.LISTENER_CONFIG, "http://:0");
        for (Map.Entry<MetricName, Metric> entry : Metrics.defaultRegistry().allMetrics().entrySet()) {
            Metrics.defaultRegistry().removeMetric(entry.getKey());
        }
    }

    @Test
    public void testLifeCycle() throws Exception {
        YammerPrometheusMetricsReporter reporter = new YammerPrometheusMetricsReporter(registry, collector);
        configs.put(PrometheusMetricsReporterConfig.ALLOWLIST_CONFIG, "group_type.*");
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
            assertEquals("group_type_name_total 0.0", metrics.get(0));

            // Removing the metric
            removeMetric("group", "type", "name");
            metrics = getMetrics(port);
            assertEquals(0, metrics.size());
        } finally {
            if (httpServer != null) HttpServers.release(httpServer);
        }
    }

    private Counter newCounter(String group, String type, String name) {
        MetricName metricName = new MetricName(group, type, name, "");
        return Metrics.defaultRegistry().newCounter(metricName);
    }

    private void removeMetric(String group, String type, String name) {
        MetricName metricName = new MetricName(group, type, name, "");
        Metrics.defaultRegistry().removeMetric(metricName);
    }
}
