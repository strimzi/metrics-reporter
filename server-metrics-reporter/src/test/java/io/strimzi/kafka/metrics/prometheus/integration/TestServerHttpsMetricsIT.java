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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.strimzi.kafka.metrics.prometheus.ClientMetricsReporterConfig.LISTENER_CONFIG;
import static io.strimzi.kafka.metrics.prometheus.ClientMetricsReporterConfig.LISTENER_SSL_CERTIFICATE_LOCATION_CONFIG;
import static io.strimzi.kafka.metrics.prometheus.ClientMetricsReporterConfig.LISTENER_SSL_KEY_LOCATION_CONFIG;
import static io.strimzi.kafka.metrics.prometheus.MetricsUtils.VERSION;
import static io.strimzi.kafka.metrics.prometheus.http.SslTestUtils.CERTIFICATE;
import static io.strimzi.kafka.metrics.prometheus.http.SslTestUtils.PRIVATE_KEY;
import static io.strimzi.kafka.metrics.prometheus.http.SslTestUtils.trustingClientSslContext;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class TestServerHttpsMetricsIT {

    private static final String REPORTER_JARS = "../target/metrics-reporter-" + VERSION + "/metrics-reporter-" + VERSION + "/libs/";
    private static final int PORT = Listener.parseListener(ServerMetricsReporterConfig.LISTENER_CONFIG_DEFAULT).port;
    private static final String CONTAINER_CERTIFICATE = "/tmp/tls.crt";
    private static final String CONTAINER_KEY = "/tmp/tls.key";

    @TempDir
    private Path tempDir;

    private StrimziKafkaCluster cluster;

    @AfterEach
    public void tearDown() {
        if (cluster != null) {
            cluster.stop();
        }
    }

    @Test
    public void testBrokerHttpsMetrics() throws Exception {
        Path certificate = tempDir.resolve("tls.crt");
        Path key = tempDir.resolve("tls.key");
        Files.writeString(certificate, CERTIFICATE);
        Files.writeString(key, PRIVATE_KEY);

        Map<String, String> configs = new HashMap<>();
        configs.put("metric.reporters", ServerKafkaMetricsReporter.class.getName());
        configs.put("kafka.metrics.reporters", ServerYammerMetricsReporter.class.getName());
        configs.put(LISTENER_CONFIG, "https://:" + PORT);
        configs.put(LISTENER_SSL_CERTIFICATE_LOCATION_CONFIG, CONTAINER_CERTIFICATE);
        configs.put(LISTENER_SSL_KEY_LOCATION_CONFIG, CONTAINER_KEY);

        cluster = new StrimziKafkaCluster.StrimziKafkaClusterBuilder()
                .withAdditionalKafkaConfiguration(configs)
                .withNumberOfBrokers(1)
                .withSharedNetwork()
                .build();
        for (GenericContainer<?> broker : cluster.getNodes()) {
            broker.withCopyFileToContainer(MountableFile.forHostPath(MetricsUtils.REPORTER_JARS), MetricsUtils.MOUNT_PATH)
                    .withCopyFileToContainer(MountableFile.forHostPath(REPORTER_JARS), MetricsUtils.MOUNT_PATH)
                    .withCopyFileToContainer(MountableFile.forHostPath(certificate), CONTAINER_CERTIFICATE)
                    .withCopyFileToContainer(MountableFile.forHostPath(key), CONTAINER_KEY)
                    .withExposedPorts(9092, PORT)
                    .withEnv(Map.of("CLASSPATH", MetricsUtils.MOUNT_PATH + "*"));
        }
        cluster.start();

        List<String> patterns = List.of(
                "jvm_.*",
                "process_.*",
                "kafka_controller_.*",
                "kafka_coordinator_.*",
                "kafka_log_.*",
                "kafka_network_.*",
                "kafka_server_.*");
        for (GenericContainer<?> broker : cluster.getNodes()) {
            MetricsUtils.verify(broker, patterns, PORT, metrics -> assertFalse(metrics.isEmpty()),
                    trustingClientSslContext());
        }
    }
}
