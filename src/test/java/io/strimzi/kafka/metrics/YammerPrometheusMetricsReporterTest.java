/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import io.prometheus.metrics.model.registry.PrometheusRegistry;
import kafka.utils.VerifiableProperties;
import org.junit.jupiter.api.Test;

import java.util.Properties;

public class YammerPrometheusMetricsReporterTest {

    @Test
    public void testLifeCycle() {
        YammerPrometheusMetricsReporter reporter = new YammerPrometheusMetricsReporter(new PrometheusRegistry());
        Properties configs = new Properties();
        configs.put("broker.id", "0");
        configs.put(PrometheusMetricsReporterConfig.LISTENER_CONFIG, "http://:0");
        reporter.init(new VerifiableProperties(configs));
    }
}
