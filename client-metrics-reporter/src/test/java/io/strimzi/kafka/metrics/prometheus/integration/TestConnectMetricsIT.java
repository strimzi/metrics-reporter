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
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.MountableFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.strimzi.kafka.metrics.prometheus.ClientMetricsReporterConfig.ALLOWLIST_CONFIG;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestConnectMetricsIT {

    private static final int PORT = Listener.parseListener(ClientMetricsReporterConfig.LISTENER_CONFIG_DEFAULT).port;
    private static final String GROUP_ID = "my-cluster";
    private static final String CLIENT_IDS_PATTERN = "client_id=\"" + GROUP_ID + "-(configs|offsets|statuses)\".*";
    private static final String ADMIN_ID_PATTERN = "client_id=\"" + GROUP_ID + "-shared-admin\".*";
    private static final String CONNECT_ID_PATTERN = "connect.*";
    private static final String TOPIC = "topic-to-export";
    private static final String FILE = "/tmp/file";
    private static final String SINK_CONNECTOR = "file-sink";
    private static final String SINK_CONNECTOR_PATTERN = "connector=\"" + SINK_CONNECTOR + "\".*";
    private static final String SINK_CONSUMER_ID = "client_id=\"connector-consumer-" + SINK_CONNECTOR + ".*";
    private static final String SOURCE_CONNECTOR = "file-source";
    private static final String SOURCE_CONNECTOR_PATTERN = "connector=\"" + SOURCE_CONNECTOR + "\".*";
    private static final String SOURCE_PRODUCER_ID = "client_id=\"connector-producer-" + SOURCE_CONNECTOR + ".*";

    private static final List<String> CONNECT_PATTERNS = List.of(
            "jvm_.*",
            "process_.*",
            "kafka_admin_client_app_info_.*" + ADMIN_ID_PATTERN,
            "kafka_admin_client_kafka_metrics_.*" + ADMIN_ID_PATTERN,
            "kafka_admin_client_admin_client_metrics_.*" + ADMIN_ID_PATTERN,
            "kafka_admin_client_admin_client_node_metrics_.*" + ADMIN_ID_PATTERN,
            "kafka_consumer_app_info_.*" + CLIENT_IDS_PATTERN,
            "kafka_consumer_kafka_metrics_.*" + CLIENT_IDS_PATTERN,
            "kafka_consumer_consumer_metrics_.*" + CLIENT_IDS_PATTERN,
            "kafka_consumer_consumer_node_metrics_.*" + CLIENT_IDS_PATTERN,
            "kafka_consumer_consumer_coordinator_metrics_.*" + CLIENT_IDS_PATTERN,
            "kafka_consumer_consumer_fetch_manager_metrics_.*" + CLIENT_IDS_PATTERN,
            "kafka_producer_app_info_.*" + CLIENT_IDS_PATTERN,
            "kafka_producer_kafka_metrics_.*" + CLIENT_IDS_PATTERN,
            "kafka_producer_producer_metrics_.*" + CLIENT_IDS_PATTERN,
            "kafka_producer_producer_node_metrics_.*" + CLIENT_IDS_PATTERN,
            "kafka_producer_producer_topic_metrics_.*" + CLIENT_IDS_PATTERN,
            "kafka_connect_app_info_.*" + CONNECT_ID_PATTERN,
            "kafka_connect_connect_coordinator_metrics_.*" + CONNECT_ID_PATTERN,
            "kafka_connect_connect_metrics_.*" + CONNECT_ID_PATTERN,
            "kafka_connect_connect_node_metrics_.*" + CONNECT_ID_PATTERN,
            "kafka_connect_connect_worker_metrics_.*" + CONNECT_ID_PATTERN,
            "kafka_connect_connect_worker_rebalance_metrics_.*" + CONNECT_ID_PATTERN,
            "kafka_connect_kafka_metrics_.*" + CONNECT_ID_PATTERN);

    private static final List<String> SINK_PATTERNS = List.of(
            "kafka_connect_connector_metrics_.*" + SINK_CONNECTOR_PATTERN,
            "kafka_connect_connector_task_metrics_.*" + SINK_CONNECTOR_PATTERN,
            "kafka_connect_sink_task_metrics_.*" + SINK_CONNECTOR_PATTERN,
            "kafka_connect_task_error_metrics_.*" + SINK_CONNECTOR_PATTERN,
            "kafka_connect_connect_worker_metrics_connector_count 1.0",
            "kafka_consumer_app_info_.*" + SINK_CONSUMER_ID,
            "kafka_consumer_kafka_metrics_.*" + SINK_CONSUMER_ID,
            "kafka_consumer_consumer_metrics_.*" + SINK_CONSUMER_ID,
            "kafka_consumer_consumer_node_metrics_.*" + SINK_CONSUMER_ID,
            "kafka_consumer_consumer_coordinator_metrics_.*" + SINK_CONSUMER_ID,
            "kafka_consumer_consumer_fetch_manager_metrics_.*" + SINK_CONSUMER_ID);

    private static final List<String> SOURCE_PATTERNS = List.of(
            "kafka_connect_connector_metrics_.*" + SOURCE_CONNECTOR_PATTERN,
            "kafka_connect_connector_task_metrics_.*" + SOURCE_CONNECTOR_PATTERN,
            "kafka_connect_source_task_metrics_.*" + SOURCE_CONNECTOR_PATTERN,
            "kafka_connect_task_error_metrics_.*" + SOURCE_CONNECTOR_PATTERN,
            "kafka_connect_connect_worker_metrics_connector_count 2.0",
            "kafka_producer_app_info_.*" + SOURCE_PRODUCER_ID,
            "kafka_producer_kafka_metrics_.*" + SOURCE_PRODUCER_ID,
            "kafka_producer_producer_metrics_.*" + SOURCE_PRODUCER_ID,
            "kafka_producer_producer_node_metrics_.*" + SOURCE_PRODUCER_ID,
            "kafka_producer_producer_topic_metrics_.*" + SOURCE_PRODUCER_ID);

    private StrimziKafkaCluster kafka;
    private StrimziConnectCluster connect;

    @BeforeEach
    public void setUp() {
        kafka = new StrimziKafkaCluster.StrimziKafkaClusterBuilder()
                .withNumberOfBrokers(1)
                .withSharedNetwork()
                .build();
        kafka.start();

        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(TOPIC, 1, (short) -1)));
        }
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName()
        ))) {
            for (int i = 0; i < 5; i++) {
                producer.send(new ProducerRecord<>(TOPIC, "record" + i));
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

    private void setupConnect(Map<String, String> overrides) {
        Map<String, String> configs = new HashMap<>(overrides);
        configs.put(CommonClientConfigs.METRIC_REPORTER_CLASSES_CONFIG, ClientMetricsReporter.class.getName());
        connect = new StrimziConnectCluster.StrimziConnectClusterBuilder()
                .withGroupId(GROUP_ID)
                .withKafkaCluster(kafka)
                .withAdditionalConnectConfiguration(configs)
                .build();
        for (GenericContainer<?> worker : connect.getWorkers()) {
            worker.withCopyFileToContainer(MountableFile.forHostPath(MetricsUtils.REPORTER_JARS), MetricsUtils.MOUNT_PATH)
                    .withExposedPorts(8083, PORT)
                .withEnv(Map.of("CLASSPATH", MetricsUtils.MOUNT_PATH + "*"));
        }
        connect.start();
    }

    @Test
    public void testConnectMetrics() {
        setupConnect(Map.of(
                "consumer.metric.reporters", ClientMetricsReporter.class.getName(),
                "producer.metric.reporters", ClientMetricsReporter.class.getName(),
                "admin.metric.reporters", ClientMetricsReporter.class.getName()
        ));

        // Check the global Connect metrics
        checkMetricsExist(CONNECT_PATTERNS);

        // Start a sink connector metrics and check its metrics
        String connectorConfig =
                "{\n" +
                "  \"connector.class\":\"org.apache.kafka.connect.file.FileStreamSinkConnector\",\n" +
                "  \"topics\": \"" + TOPIC + "\",\n" +
                "  \"file\": \"" + FILE + "\"\n" +
                "}";
        MetricsUtils.startConnector(connect, SINK_CONNECTOR, connectorConfig, 1);
        checkMetricsExist(SINK_PATTERNS);

        // Start a source connector metrics and check its metrics
        connectorConfig =
                "{\n" +
                "  \"connector.class\":\"org.apache.kafka.connect.file.FileStreamSourceConnector\",\n" +
                "  \"topic\": \"" + TOPIC + "\",\n" +
                "  \"file\": \"" + FILE + "\"\n" +
                "}";
        MetricsUtils.startConnector(connect, SOURCE_CONNECTOR, connectorConfig, 1);
        checkMetricsExist(SOURCE_PATTERNS);
    }

    @Test
    public void testConnectMetricsWithAllowlist() {
        setupConnect(Map.of(
                "consumer.metric.reporters", ClientMetricsReporter.class.getName(),
                "producer.metric.reporters", ClientMetricsReporter.class.getName(),
                "admin.metric.reporters", ClientMetricsReporter.class.getName(),
                ALLOWLIST_CONFIG, "kafka_connect_connect_worker_.*,kafka_connect_connector_metrics_.*,kafka_admin_client_admin_client_metrics_.*,kafka_consumer_kafka_metrics_.*,kafka_producer_producer_node_metrics_.*",
                "admin." + ALLOWLIST_CONFIG, "kafka_admin_client_admin_client_metrics.*",
                "consumer." + ALLOWLIST_CONFIG, "kafka_consumer_app_info_.*",
                "producer." + ALLOWLIST_CONFIG, "kafka_producer_producer_metrics_.*"
        ));

        // Check the Connect metrics
        List<String> allowedPatterns = List.of(
                "jvm_.*",
                "process_.*",
                "kafka_admin_client_admin_client_metrics_.*" + ADMIN_ID_PATTERN,
                "kafka_consumer_kafka_metrics_.*" + CLIENT_IDS_PATTERN,
                "kafka_producer_producer_node_metrics_.*" + CLIENT_IDS_PATTERN,
                "kafka_connect_connect_worker_metrics_.*" + CONNECT_ID_PATTERN,
                "kafka_connect_connect_worker_rebalance_metrics_.*" + CONNECT_ID_PATTERN);
        checkMetricsExist(allowedPatterns);

        List<String> disallowedPatterns = new ArrayList<>(CONNECT_PATTERNS);
        disallowedPatterns.removeAll(allowedPatterns);
        checkMetricsDontExist(disallowedPatterns);

        // Start a sink connector metrics and check its metrics
        String connectorConfig =
                "{\n" +
                "  \"connector.class\":\"org.apache.kafka.connect.file.FileStreamSinkConnector\",\n" +
                "  \"topics\": \"" + TOPIC + "\",\n" +
                "  \"file\": \"" + FILE + "\"\n" +
                "}";
        MetricsUtils.startConnector(connect, SINK_CONNECTOR, connectorConfig, 1);
        List<String> allowedSinkPatterns = List.of(
                "kafka_connect_connector_metrics_.*" + SINK_CONNECTOR_PATTERN,
                "kafka_connect_connect_worker_metrics_connector_count 1.0",
                "kafka_consumer_app_info_.*" + SINK_CONSUMER_ID);
        checkMetricsExist(allowedSinkPatterns);

        disallowedPatterns = new ArrayList<>(SINK_PATTERNS);
        disallowedPatterns.removeAll(allowedSinkPatterns);
        checkMetricsDontExist(disallowedPatterns);

        // Start a source connector metrics and check its metrics
        connectorConfig =
                "{\n" +
                "  \"connector.class\":\"org.apache.kafka.connect.file.FileStreamSourceConnector\",\n" +
                "  \"topic\": \"" + TOPIC + "\",\n" +
                "  \"file\": \"" + FILE + "\"\n" +
                "}";
        MetricsUtils.startConnector(connect, SOURCE_CONNECTOR, connectorConfig, 1);
        List<String> allowedSourcePatterns = List.of(
                "kafka_connect_connector_metrics_.*" + SOURCE_CONNECTOR_PATTERN,
                "kafka_connect_connect_worker_metrics_connector_count 2.0",
                "kafka_producer_producer_metrics_.*" + SOURCE_PRODUCER_ID);
        checkMetricsExist(allowedSourcePatterns);

        disallowedPatterns = new ArrayList<>(allowedSourcePatterns);
        disallowedPatterns.removeAll(allowedSourcePatterns);
        checkMetricsDontExist(disallowedPatterns);
    }

    private void checkMetricsExist(List<String> patterns) {
        for (GenericContainer<?> worker : connect.getWorkers()) {
            MetricsUtils.verify(worker, patterns, PORT, metrics -> assertFalse(metrics.isEmpty()));
        }
    }

    private void checkMetricsDontExist(List<String> patterns) {
        for (GenericContainer<?> worker : connect.getWorkers()) {
            MetricsUtils.verify(worker, patterns, PORT, metrics -> assertTrue(metrics.isEmpty()));
        }
    }
}
