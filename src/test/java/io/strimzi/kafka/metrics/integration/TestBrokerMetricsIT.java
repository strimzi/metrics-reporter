/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.integration;

import io.strimzi.kafka.metrics.KafkaPrometheusMetricsReporter;
import io.strimzi.kafka.metrics.TestUtils;
import io.strimzi.kafka.metrics.YammerPrometheusMetricsReporter;
import io.strimzi.test.container.StrimziKafkaContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.utility.MountableFile;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestBrokerMetricsIT {

    private Map<String, String> configs;
    private StrimziKafkaContainer broker;

    @BeforeEach
    public void setUp() {
        configs = new HashMap<>();
        configs.put("metric.reporters", KafkaPrometheusMetricsReporter.class.getName());
        configs.put("kafka.metrics.reporters", YammerPrometheusMetricsReporter.class.getName());

        broker = new StrimziKafkaContainer()
                .withNodeId(0)
                .withKraft()
                .withCopyFileToContainer(MountableFile.forHostPath(TestUtils.REPORTER_JARS), TestUtils.MOUNT_PATH)
                .withExposedPorts(9092, TestUtils.PORT)
                .withKafkaConfigurationMap(configs)
                .withEnv(Collections.singletonMap("CLASSPATH", TestUtils.MOUNT_PATH + "*"));
    }

    @AfterEach
    public void tearDown() {
        broker.stop();
    }

    @Test
    public void testBrokerMetrics() throws Exception {
        broker.start();

        List<String> prefixes = List.of(
            "jvm_",
            "process_",
            "kafka_controller_",
            "kafka_coordinator_",
            "kafka_log_",
            "kafka_network_",
            "kafka_server_");
        List<String> metrics = TestUtils.getMetrics(broker.getHost(), broker.getMappedPort(TestUtils.PORT));
        for (String prefix : prefixes) {
            assertFalse(TestUtils.filterMetrics(metrics, prefix).isEmpty());
        }
    }

    @Test
    public void testBrokerMetricsWithAllowlist() throws Exception {
        configs.put("prometheus.metrics.reporter.allowlist", "kafka_controller.*,kafka_server.*");
        broker.withKafkaConfigurationMap(configs);
        broker.start();

        List<String> metrics = TestUtils.getMetrics(broker.getHost(), broker.getMappedPort(TestUtils.PORT));
        List<String> allowedPrefixes = List.of(
            "jvm_",
            "process_",
            "kafka_controller_",
            "kafka_server_");
        for (String prefix : allowedPrefixes) {
            assertFalse(TestUtils.filterMetrics(metrics, prefix).isEmpty());
        }
        List<String> disallowPrefixes = List.of(
            "kafka_coordinator_",
            "kafka_log_",
            "kafka_network_");
        for (String prefix : disallowPrefixes) {
            assertTrue(TestUtils.filterMetrics(metrics, prefix).isEmpty());
        }
    }
}
