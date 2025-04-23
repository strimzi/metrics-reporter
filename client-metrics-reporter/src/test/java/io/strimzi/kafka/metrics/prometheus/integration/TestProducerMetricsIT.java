/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.prometheus.integration;

import io.strimzi.kafka.metrics.prometheus.ClientMetricsReporter;
import io.strimzi.kafka.metrics.prometheus.ClientMetricsReporterConfig;
import io.strimzi.kafka.metrics.prometheus.MetricsUtils;
import io.strimzi.kafka.metrics.prometheus.http.Listener;
import io.strimzi.test.container.StrimziKafkaCluster;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestProducerMetricsIT {

    private static final int PORT = Listener.parseListener(ClientMetricsReporterConfig.LISTENER_CONFIG_DEFAULT).port;
    private StrimziKafkaCluster cluster;
    private Map<String, String> env;

    @BeforeEach
    public void setUp() {
        cluster = new StrimziKafkaCluster.StrimziKafkaClusterBuilder()
                .withNumberOfBrokers(1)
                .withSharedNetwork()
                .build();
        cluster.start();

        env = new HashMap<>();
        env.put("CLIENT_TYPE", "KafkaProducer");
        env.put("BOOTSTRAP_SERVERS", cluster.getNetworkBootstrapServers());
        env.put("TOPIC", "my-topic");
        env.put("ADDITIONAL_CONFIG", "metric.reporters=" + ClientMetricsReporter.class.getName());
        env.put("CLASSPATH", MetricsUtils.MOUNT_PATH + "*");
        env.put("MESSAGE_COUNT", "1000");
        env.put("DELAY_MS", "100");
    }

    @AfterEach
    public void tearDown() {
        cluster.stop();
    }

    @Test
    public void testProducerMetrics() {
        try (GenericContainer<?> producer = MetricsUtils.clientContainer(env, PORT)) {
            producer.start();

            List<String> patterns = List.of(
                    "jvm_.*",
                    "process_.*",
                    "kafka_producer_app_info_.*",
                    "kafka_producer_kafka_metrics_.*",
                    "kafka_producer_producer_metrics_.*",
                    "kafka_producer_producer_node_metrics_.*",
                    "kafka_producer_producer_topic_metrics_.*");
            MetricsUtils.verify(producer, patterns, PORT, metrics -> assertFalse(metrics.isEmpty()));
        }
    }

    @Test
    public void testProducerMetricsWithAllowlist() {
        env.put("ADDITIONAL_CONFIG",
                "metric.reporters=" + ClientMetricsReporter.class.getName() + "\n" +
                        "prometheus.metrics.reporter.allowlist=kafka_producer_kafka_metrics_.*,kafka_producer_producer_topic_metrics_.*");
        try (GenericContainer<?> producer = MetricsUtils.clientContainer(env, PORT)) {
            producer.start();

            List<String> allowedPatterns = List.of(
                    "jvm_.*",
                    "process_.*",
                    "kafka_producer_kafka_metrics_.*",
                    "kafka_producer_producer_topic_metrics_.*");
            MetricsUtils.verify(producer, allowedPatterns, PORT, metrics -> assertFalse(metrics.isEmpty()));

            List<String> disallowedPatterns = List.of(
                    "kafka_producer_app_info_.*",
                    "kafka_producer_producer_metrics_.*",
                    "kafka_producer_producer_node_metrics_.*");
            MetricsUtils.verify(producer, disallowedPatterns, PORT, metrics -> assertTrue(metrics.isEmpty()));
        }
    }
}
