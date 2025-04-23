/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.prometheus.integration;

import io.strimzi.kafka.metrics.prometheus.MetricsUtils;
import io.strimzi.kafka.metrics.prometheus.ServerKafkaMetricsReporter;
import io.strimzi.kafka.metrics.prometheus.ServerMetricsReporterConfig;
import io.strimzi.kafka.metrics.prometheus.ServerYammerMetricsReporter;
import io.strimzi.kafka.metrics.prometheus.http.Listener;
import io.strimzi.test.container.StrimziKafkaContainer;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.common.config.ConfigResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.utility.MountableFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.strimzi.kafka.metrics.prometheus.MetricsUtils.VERSION;
import static io.strimzi.kafka.metrics.prometheus.ServerMetricsReporterConfig.ALLOWLIST_CONFIG;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestServerMetricsIT {

    private static final String REPORTER_JARS = "../target/metrics-reporter-" + VERSION + "/metrics-reporter-" + VERSION + "/libs/";
    private static final int PORT = Listener.parseListener(ServerMetricsReporterConfig.LISTENER_CONFIG_DEFAULT).port;
    private static final int NODE_ID = 0;

    private Map<String, String> configs;
    private StrimziKafkaContainer broker;

    @BeforeEach
    public void setUp() {
        configs = new HashMap<>();
        configs.put("metric.reporters", ServerKafkaMetricsReporter.class.getName());
        configs.put("kafka.metrics.reporters", ServerYammerMetricsReporter.class.getName());

        broker = new StrimziKafkaContainer()
                .withNodeId(NODE_ID)
                .withCopyFileToContainer(MountableFile.forHostPath(REPORTER_JARS), MetricsUtils.MOUNT_PATH)
                .withExposedPorts(9092, PORT)
                .withKafkaConfigurationMap(configs)
                .withEnv(Map.of("CLASSPATH", MetricsUtils.MOUNT_PATH + "*"));
    }

    @AfterEach
    public void tearDown() {
        broker.stop();
    }

    @Test
    public void testBrokerMetrics() {
        broker.start();

        List<String> patterns = List.of(
            "jvm_.*",
            "process_.*",
            "kafka_controller_.*",
            "kafka_coordinator_.*",
            "kafka_log_.*",
            "kafka_network_.*",
            "kafka_server_.*");
        MetricsUtils.verify(broker, patterns, PORT, metrics -> assertFalse(metrics.isEmpty()));
    }

    @Test
    public void testBrokerMetricsWithAllowlist() {
        configs.put(ALLOWLIST_CONFIG, "kafka_controller.*,kafka_server.*");
        broker.withKafkaConfigurationMap(configs);
        broker.start();

        List<String> allowedPatterns = List.of(
            "jvm_.*",
            "process_.*",
            "kafka_controller_.*",
            "kafka_server_.*");
        MetricsUtils.verify(broker, allowedPatterns, PORT, metrics -> assertFalse(metrics.isEmpty()));

        List<String> disallowPatterns = List.of(
            "kafka_coordinator_.*",
            "kafka_log_.*",
            "kafka_network_.*");
        MetricsUtils.verify(broker, disallowPatterns, PORT, metrics -> assertTrue(metrics.isEmpty()));
    }

    @Test
    public void testReconfigureAllowlist() throws Exception {
        configs.put(ALLOWLIST_CONFIG, "kafka_controller.*,kafka_server.*");
        broker.withKafkaConfigurationMap(configs);
        broker.start();

        List<String> allowedPatterns = List.of(
                "jvm_.*",
                "process_.*",
                "kafka_controller_.*",
                "kafka_server_.*");
        MetricsUtils.verify(broker, allowedPatterns, PORT, metrics -> assertFalse(metrics.isEmpty()));

        List<String> disallowPatterns = List.of(
                "kafka_coordinator_.*",
                "kafka_log_.*",
                "kafka_network_.*");
        MetricsUtils.verify(broker, disallowPatterns, PORT, metrics -> assertTrue(metrics.isEmpty()));

        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBootstrapServers()))) {
            admin.incrementalAlterConfigs(Map.of(
                    new ConfigResource(ConfigResource.Type.BROKER, String.valueOf(NODE_ID)),
                    List.of(new AlterConfigOp(
                            new ConfigEntry(ALLOWLIST_CONFIG, "kafka_coordinator.*,kafka_log.*,kafka_network.*"),
                            AlterConfigOp.OpType.SET))
            )).all().get();
        }

        allowedPatterns = List.of(
                "jvm_.*",
                "process_.*",
                "kafka_coordinator_.*",
                "kafka_log_.*",
                "kafka_network_.*");
        MetricsUtils.verify(broker, allowedPatterns, PORT, metrics -> assertFalse(metrics.isEmpty()));

        disallowPatterns = List.of(
                "kafka_controller_.*",
                "kafka_server_.*");
        MetricsUtils.verify(broker, disallowPatterns, PORT, metrics -> assertTrue(metrics.isEmpty()));
    }
}
