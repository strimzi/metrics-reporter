/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.prometheus.integration;

import io.strimzi.kafka.metrics.prometheus.ClientMetricsReporter;
import io.strimzi.kafka.metrics.prometheus.ClientMetricsReporterConfig;
import io.strimzi.kafka.metrics.prometheus.MetricsUtils;
import io.strimzi.kafka.metrics.prometheus.http.Listener;
import io.strimzi.test.container.StrimziConnectCluster;
import io.strimzi.test.container.StrimziKafkaCluster;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.utility.MountableFile;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestMirrorMakerMetricsIT {

    private static final int PORT = Listener.parseListener(ClientMetricsReporterConfig.LISTENER_CONFIG_DEFAULT).port;
    private static final String CONNECT_ID = "my-cluster";
    private static final String TOPIC = "input";
    private static final String GROUP = "my-group";
    private static final String SOURCE_CONNECTOR = "source";
    private static final String CHECKPOINT_CONNECTOR = "checkpoint";

    private StrimziKafkaCluster kafka;
    private StrimziConnectCluster connect;

    @BeforeEach
    public void setUp() throws Exception {
        // Use a single cluster as source and target
        // MirrorSourceConnector is configured with a fixed topics configuration to avoid loop
        kafka = new StrimziKafkaCluster.StrimziKafkaClusterBuilder()
                .withNumberOfBrokers(1)
                .withSharedNetwork()
                .build();
        kafka.start();

        connect = new StrimziConnectCluster.StrimziConnectClusterBuilder()
                .withGroupId(CONNECT_ID)
                .withKafkaCluster(kafka)
                .withAdditionalConnectConfiguration(Map.of(
                        CommonClientConfigs.METRIC_REPORTER_CLASSES_CONFIG, ClientMetricsReporter.class.getName()
                ))
                .build();

        for (GenericContainer<?> worker : connect.getWorkers()) {
            worker.withCopyFileToContainer(MountableFile.forHostPath(MetricsUtils.REPORTER_JARS), MetricsUtils.MOUNT_PATH)
                    .withExposedPorts(8083, PORT)
                    .withEnv(Map.of("CLASSPATH", MetricsUtils.MOUNT_PATH + "*"))
                    .waitingFor(new HttpWaitStrategy()
                            .forPath("/health")
                            .forStatusCode(HttpURLConnection.HTTP_OK));
        }
        connect.start();

        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()))) {
            // Create a topic with 2 partitions so we get 2 MirrorSourceConnector tasks
            admin.createTopics(List.of(new NewTopic(TOPIC, 2, (short) -1))).all().get();
            // Wait for the topic to be created
            assertTimeoutPreemptively(Duration.ofSeconds(10L), () -> {
                while (true) {
                    try {
                        assertTrue(admin.listTopics().names().get().contains(TOPIC));
                        break;
                    } catch (Throwable t) {
                        assertInstanceOf(AssertionError.class, t);
                        TimeUnit.MILLISECONDS.sleep(100L);
                    }
                }
            });
            // Create 2 consumer groups so we get 2 MirrorCheckpointConnector tasks
            admin.alterConsumerGroupOffsets(GROUP, Map.of(new TopicPartition(TOPIC, 0), new OffsetAndMetadata(1))).all().get();
            admin.alterConsumerGroupOffsets(GROUP + "-2", Map.of(new TopicPartition(TOPIC, 0), new OffsetAndMetadata(1))).all().get();
        }
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName()
        ))) {
            for (int i = 0; i < 5; i++) {
                producer.send(new ProducerRecord<>(TOPIC, i % 2, null, "record" + i));
            }
        }
    }

    @AfterEach
    public void tearDown() {
        if (connect != null) {
            connect.stop();
        }
        if (kafka != null) {
            kafka.stop();
        }
    }

    @Test
    public void testMirrorMakerConnectorMetrics() {
        // Start MirrorSourceConnector and check its metrics
        String sourceTags = ".*partition=\"\\d+\",source=\"source\",target=\"target\",topic=\"source.input\".*";
        List<String> sourceMetricsPatterns = List.of(
                "kafka_connect_mirror_mirrorsourceconnector_byte_count" + sourceTags,
                "kafka_connect_mirror_mirrorsourceconnector_byte_rate" + sourceTags,
                "kafka_connect_mirror_mirrorsourceconnector_record_age_ms" + sourceTags,
                "kafka_connect_mirror_mirrorsourceconnector_record_age_ms_avg" + sourceTags,
                "kafka_connect_mirror_mirrorsourceconnector_record_age_ms_max" + sourceTags,
                "kafka_connect_mirror_mirrorsourceconnector_record_age_ms_min" + sourceTags,
                "kafka_connect_mirror_mirrorsourceconnector_record_count" + sourceTags,
                "kafka_connect_mirror_mirrorsourceconnector_record_rate" + sourceTags,
                "kafka_connect_mirror_mirrorsourceconnector_replication_latency_ms" + sourceTags,
                "kafka_connect_mirror_mirrorsourceconnector_replication_latency_ms_avg" + sourceTags,
                "kafka_connect_mirror_mirrorsourceconnector_replication_latency_ms_max" + sourceTags,
                "kafka_connect_mirror_mirrorsourceconnector_replication_latency_ms_min" + sourceTags
        );
        String sourceConfig =
                "{\n" +
                "  \"name\": \"" + SOURCE_CONNECTOR + "\",\n" +
                "  \"connector.class\": \"org.apache.kafka.connect.mirror.MirrorSourceConnector\",\n" +
                "  \"tasks.max\": \"2\",\n" +
                "  \"key.converter\": \"org.apache.kafka.connect.converters.ByteArrayConverter\",\n" +
                "  \"value.converter\": \"org.apache.kafka.connect.converters.ByteArrayConverter\",\n" +
                "  \"source.cluster.alias\": \"source\",\n" +
                "  \"target.cluster.alias\": \"target\",\n" +
                "  \"source.cluster.bootstrap.servers\": \"" + kafka.getNetworkBootstrapServers() + "\",\n" +
                "  \"target.cluster.bootstrap.servers\": \"" + kafka.getNetworkBootstrapServers() + "\",\n" +
                "  \"replication.factor\": \"-1\",\n" +
                "  \"offset-syncs.topic.replication.factor\": \"-1\",\n" +
                "  \"refresh.topics.interval.seconds\": \"1\",\n" +
                "  \"topics\": \"" + TOPIC + "\",\n" +
                "  \"metric.reporters\": \"" + ClientMetricsReporter.class.getName() + "\",\n" +
                "  \"prometheus.metrics.reporter.listener.enable\": \"false\"" +
                "}";
        MetricsUtils.startConnector(connect, SOURCE_CONNECTOR, sourceConfig, 2);
        checkMetricsExist(sourceMetricsPatterns);

        // Start MirrorCheckpointConnector and check its metrics
        String checkpointTags = ".*group=\".*\",partition=\"\\d+\",source=\"source\",target=\"target\",topic=\"source.input\".*";
        List<String> checkpointMetricPatterns = List.of(
                "kafka_connect_mirror_mirrorcheckpointconnector_checkpoint_latency_ms" + checkpointTags,
                "kafka_connect_mirror_mirrorcheckpointconnector_checkpoint_latency_ms_avg" + checkpointTags,
                "kafka_connect_mirror_mirrorcheckpointconnector_checkpoint_latency_ms_max" + checkpointTags,
                "kafka_connect_mirror_mirrorcheckpointconnector_checkpoint_latency_ms_min" + checkpointTags
        );
        String checkpointConfig =
                "{\n" +
                "  \"name\": \"" + CHECKPOINT_CONNECTOR + "\",\n" +
                "  \"connector.class\": \"org.apache.kafka.connect.mirror.MirrorCheckpointConnector\",\n" +
                "  \"tasks.max\": \"2\",\n" +
                "  \"key.converter\": \"org.apache.kafka.connect.converters.ByteArrayConverter\",\n" +
                "  \"value.converter\": \"org.apache.kafka.connect.converters.ByteArrayConverter\",\n" +
                "  \"source.cluster.alias\": \"source\",\n" +
                "  \"target.cluster.alias\": \"target\",\n" +
                "  \"source.cluster.bootstrap.servers\": \"" + kafka.getNetworkBootstrapServers() + "\",\n" +
                "  \"target.cluster.bootstrap.servers\": \"" + kafka.getNetworkBootstrapServers() + "\",\n" +
                "  \"checkpoints.topic.replication.factor\": \"-1\",\n" +
                "  \"emit.checkpoints.interval.seconds\": \"1\",\n" +
                "  \"refresh.groups.interval.seconds\": \"1\",\n" +
                "  \"metric.reporters\": \"" + ClientMetricsReporter.class.getName() + "\",\n" +
                "  \"prometheus.metrics.reporter.listener.enable\": \"false\"" +
                "}";
        MetricsUtils.startConnector(connect, CHECKPOINT_CONNECTOR, checkpointConfig, 2);
        checkMetricsExist(checkpointMetricPatterns);
    }

    private void checkMetricsExist(List<String> patterns) {
        for (GenericContainer<?> worker : connect.getWorkers()) {
            MetricsUtils.verify(worker, patterns, PORT, metrics -> assertFalse(metrics.isEmpty()));
        }
    }
}
