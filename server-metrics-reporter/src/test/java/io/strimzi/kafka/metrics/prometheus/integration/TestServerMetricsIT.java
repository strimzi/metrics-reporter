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
import io.strimzi.test.container.StrimziKafkaCluster;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.errors.InvalidRequestException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.MountableFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static io.strimzi.kafka.metrics.prometheus.MetricsUtils.VERSION;
import static io.strimzi.kafka.metrics.prometheus.ServerMetricsReporterConfig.ALLOWLIST_CONFIG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestServerMetricsIT {

    private static final String REPORTER_JARS = "../target/metrics-reporter-" + VERSION + "/metrics-reporter-" + VERSION + "/libs/";
    private static final int PORT = Listener.parseListener(ServerMetricsReporterConfig.LISTENER_CONFIG_DEFAULT).port;

    private StrimziKafkaCluster cluster;

    @AfterEach
    public void tearDown() {
        if (cluster != null) {
            cluster.stop();
        }
    }

    private void setupCluster(Map<String, String> overrides) {
        Map<String, String> configs = new HashMap<>(overrides);
        configs.put("metric.reporters", ServerKafkaMetricsReporter.class.getName());
        configs.put("kafka.metrics.reporters", ServerYammerMetricsReporter.class.getName());

        cluster = new StrimziKafkaCluster.StrimziKafkaClusterBuilder()
                .withAdditionalKafkaConfiguration(configs)
                .withNumberOfBrokers(1)
                .withSharedNetwork()
                .build();
        for (GenericContainer<?> broker : cluster.getNodes()) {
            broker.withCopyFileToContainer(MountableFile.forHostPath(MetricsUtils.REPORTER_JARS), MetricsUtils.MOUNT_PATH)
                    .withCopyFileToContainer(MountableFile.forHostPath(REPORTER_JARS), MetricsUtils.MOUNT_PATH)
                    .withExposedPorts(9092, PORT)
                    .withEnv(Map.of("CLASSPATH", MetricsUtils.MOUNT_PATH + "*"));
        }
        cluster.start();
    }

    @Test
    public void testBrokerMetrics() {
        setupCluster(Map.of());

        List<String> patterns = List.of(
                "jvm_.*",
                "process_.*",
                "kafka_controller_.*",
                "kafka_coordinator_.*",
                "kafka_log_.*",
                "kafka_network_.*",
                "kafka_server_.*");
        for (GenericContainer<?> broker : cluster.getNodes()) {
            MetricsUtils.verify(broker, patterns, PORT, metrics -> assertFalse(metrics.isEmpty()));
        }
    }

    @Test
    public void testBrokerMetricsWithAllowlist() {
        setupCluster(Map.of(ALLOWLIST_CONFIG, "kafka_controller.*,kafka_server.*"));

        List<String> allowedPatterns = List.of(
            "jvm_.*",
            "process_.*",
            "kafka_controller_.*",
            "kafka_server_.*");
        for (GenericContainer<?> broker : cluster.getNodes()) {
            MetricsUtils.verify(broker, allowedPatterns, PORT, metrics -> assertFalse(metrics.isEmpty()));
        }

        List<String> disallowPatterns = List.of(
            "kafka_coordinator_.*",
            "kafka_log_.*",
            "kafka_network_.*");
        for (GenericContainer<?> broker : cluster.getNodes()) {
            MetricsUtils.verify(broker, disallowPatterns, PORT, metrics -> assertTrue(metrics.isEmpty()));
        }
    }

    @Test
    public void testReconfigureAllowlist() throws Exception {
        setupCluster(Map.of(ALLOWLIST_CONFIG, "kafka_controller.*,kafka_server.*"));

        List<String> allowedPatterns = List.of(
                "jvm_.*",
                "process_.*",
                "kafka_controller_.*",
                "kafka_server_.*");
        for (GenericContainer<?> broker : cluster.getNodes()) {
            MetricsUtils.verify(broker, allowedPatterns, PORT, metrics -> assertFalse(metrics.isEmpty()));
        }

        List<String> disallowPatterns = List.of(
                "kafka_coordinator_.*",
                "kafka_log_.*",
                "kafka_network_.*");
        for (GenericContainer<?> broker : cluster.getNodes()) {
            MetricsUtils.verify(broker, disallowPatterns, PORT, metrics -> assertTrue(metrics.isEmpty()));
        }

        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, cluster.getBootstrapServers()))) {
            admin.incrementalAlterConfigs(Map.of(
                    new ConfigResource(ConfigResource.Type.BROKER, ""),
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
        for (GenericContainer<?> broker : cluster.getNodes()) {
            MetricsUtils.verify(broker, allowedPatterns, PORT, metrics -> assertFalse(metrics.isEmpty()));
        }

        disallowPatterns = List.of(
                "kafka_controller_.*",
                "kafka_server_.*");
        for (GenericContainer<?> broker : cluster.getNodes()) {
            MetricsUtils.verify(broker, disallowPatterns, PORT, metrics -> assertTrue(metrics.isEmpty()));
        }
    }

    @Test
    public void testReconfigureValidatesAllowlist() throws Exception {
        setupCluster(Map.of(ALLOWLIST_CONFIG, "kafka_controller.*,kafka_server.*"));

        for (int brokerId = 0; brokerId < cluster.getNodes().size(); brokerId++) {
            try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, cluster.getBootstrapServers()))) {
                ConfigResource cr = new ConfigResource(ConfigResource.Type.BROKER, String.valueOf(brokerId));
                try {
                    admin.incrementalAlterConfigs(Map.of(
                            cr,
                            List.of(new AlterConfigOp(
                                    new ConfigEntry(ALLOWLIST_CONFIG, "not_a_pattern[[("),
                                    AlterConfigOp.OpType.SET))
                    )).all().get();
                } catch (ExecutionException ee) {
                    assertInstanceOf(InvalidRequestException.class, ee.getCause());
                    assertTrue(ee.getCause().getMessage().contains("ConfigException"));
                    assertTrue(ee.getCause().getMessage().contains("Invalid regex pattern found"));
                }

                Config config = admin.describeConfigs(List.of(cr)).all().get().get(cr);
                assertEquals(ConfigEntry.ConfigSource.STATIC_BROKER_CONFIG, config.get(ALLOWLIST_CONFIG).source());
            }
        }
    }
}
