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
import static io.strimzi.kafka.metrics.prometheus.http.SslTestUtils.CERTIFICATE;
import static io.strimzi.kafka.metrics.prometheus.http.SslTestUtils.PRIVATE_KEY;
import static io.strimzi.kafka.metrics.prometheus.http.SslTestUtils.trustingClientSslContext;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class ClientMetricsReporterHttpsIT {

    private static final int PORT = Listener.parseListener(ClientMetricsReporterConfig.LISTENER_CONFIG_DEFAULT).port;
    private static final String CONTAINER_CERTIFICATE = "/tmp/tls.crt";
    private static final String CONTAINER_KEY = "/tmp/tls.key";

    @TempDir
    private Path tempDir;

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
        env.put("CLASSPATH", MetricsUtils.MOUNT_PATH + "*");
        env.put("MESSAGE_COUNT", "1000");
        env.put("DELAY_MS", "100");
    }

    @AfterEach
    public void tearDown() {
        cluster.stop();
    }

    @Test
    public void testHttpsMetricsWithCertificateAndKeyLocations() throws Exception {
        Path certificate = tempDir.resolve("tls.crt");
        Path key = tempDir.resolve("tls.key");
        Files.writeString(certificate, CERTIFICATE);
        Files.writeString(key, PRIVATE_KEY);

        env.put("ADDITIONAL_CONFIG", additionalConfig(Map.of(
                LISTENER_CONFIG, "https://:" + PORT,
                LISTENER_SSL_CERTIFICATE_LOCATION_CONFIG, CONTAINER_CERTIFICATE,
                LISTENER_SSL_KEY_LOCATION_CONFIG, CONTAINER_KEY)));

        try (GenericContainer<?> producer = MetricsUtils.clientContainer(env, PORT, true)
                .withCopyFileToContainer(MountableFile.forHostPath(certificate), CONTAINER_CERTIFICATE)
                .withCopyFileToContainer(MountableFile.forHostPath(key), CONTAINER_KEY)) {
            producer.start();

            List<String> patterns = List.of(
                    "jvm_.*",
                    "process_.*",
                    "kafka_producer_app_info_.*",
                    "kafka_producer_kafka_metrics_.*",
                    "kafka_producer_producer_metrics_.*",
                    "kafka_producer_producer_node_metrics_.*",
                    "kafka_producer_producer_topic_metrics_.*");
            MetricsUtils.verify(producer, patterns, PORT, metrics -> assertFalse(metrics.isEmpty()),
                    trustingClientSslContext());
        }
    }

    private String additionalConfig(Map<String, String> configs) {
        StringBuilder config = new StringBuilder("metric.reporters=" + ClientMetricsReporter.class.getName());
        for (Map.Entry<String, String> entry : configs.entrySet()) {
            config.append("\n").append(entry.getKey()).append("=").append(entry.getValue());
        }
        return config.toString();
    }
}
