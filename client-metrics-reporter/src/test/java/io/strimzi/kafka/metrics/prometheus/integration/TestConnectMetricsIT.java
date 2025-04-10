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
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class TestConnectMetricsIT {

    private static final int PORT = Listener.parseListener(ClientMetricsReporterConfig.LISTENER_CONFIG_DEFAULT).port;
    private static final String TOPIC = "topic-to-export";
    private static final String FILE = "/tmp/sink.out";
    private static final String SINK_CONNECTOR = "file-sink";
    private static final String SOURCE_CONNECTOR = "file-source";

    private StrimziKafkaCluster kafka;
    private StrimziConnectCluster connect;

    @BeforeEach
    public void setUp() {
        kafka = new StrimziKafkaCluster.StrimziKafkaClusterBuilder()
                .withNumberOfBrokers(1)
                .withSharedNetwork()
                .build();
        kafka.start();

        connect = new StrimziConnectCluster.StrimziConnectClusterBuilder()
                .withGroupId("my-cluster")
                .withKafkaCluster(kafka)
                .withAdditionalConnectConfiguration(Map.of("metric.reporters", ClientMetricsReporter.class.getName()))
                .build();
        for (GenericContainer<?> worker : connect.getWorkers()) {
            worker.withCopyFileToContainer(MountableFile.forHostPath(MetricsUtils.REPORTER_JARS), MetricsUtils.MOUNT_PATH)
                  .withExposedPorts(8083, PORT)
                  .withEnv(Map.of("CLASSPATH", MetricsUtils.MOUNT_PATH + "*"));
        }
        connect.start();

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
        connect.stop();
        kafka.stop();
    }

    @Test
    public void testConnectMetrics() throws Exception {
        // Check the global Connect metrics
        List<String> prefixes = List.of(
                "jvm_",
                "process_",
                "kafka_admin_client_app_info_",
                "kafka_admin_client_kafka_metrics_",
                "kafka_admin_client_admin_client_metrics_",
                "kafka_admin_client_admin_client_node_metrics_",
                "kafka_consumer_app_info_",
                "kafka_consumer_kafka_metrics_",
                "kafka_consumer_consumer_metrics_",
                "kafka_consumer_consumer_node_metrics_",
                "kafka_consumer_consumer_coordinator_metrics_",
                "kafka_consumer_consumer_fetch_manager_metrics_",
                "kafka_producer_app_info_",
                "kafka_producer_kafka_metrics_",
                "kafka_producer_producer_metrics_",
                "kafka_producer_producer_node_metrics_",
                "kafka_producer_producer_topic_metrics_",
                "kafka_connect_app_info_",
                "kafka_connect_connect_coordinator_metrics_",
                "kafka_connect_connect_metrics_",
                "kafka_connect_connect_node_metrics_",
                "kafka_connect_connect_worker_metrics_",
                "kafka_connect_connect_worker_rebalance_metrics_",
                "kafka_connect_kafka_metrics_",
                "kafka_connect_connect_worker_metrics_connector_count 0.0");
        checkMetrics(prefixes);

        // Start a sink connector metrics and check its metrics
        String connectorConfig =
                "{\n" +
                "  \"connector.class\":\"org.apache.kafka.connect.file.FileStreamSinkConnector\",\n" +
                "  \"topics\": \"" + TOPIC + "\",\n" +
                "  \"file\": \"" + FILE + "\"\n" +
                "}";
        httpPut("/connectors/" + SINK_CONNECTOR + "/config", connectorConfig);
        prefixes = List.of(
                "kafka_connect_connector_metrics",
                "kafka_connect_connector_task_metrics_",
                "kafka_connect_sink_task_metrics_",
                "kafka_connect_connect_worker_metrics_connector_count 1.0");
        checkMetrics(prefixes);

        // Start a source connector metrics and check its metrics
        connectorConfig =
                "{\n" +
                "  \"connector.class\":\"org.apache.kafka.connect.file.FileStreamSourceConnector\",\n" +
                "  \"topic\": \"" + TOPIC + "\",\n" +
                "  \"file\": \"" + FILE + "\"\n" +
                "}";
        httpPut("/connectors/" + SOURCE_CONNECTOR + "/config", connectorConfig);
        prefixes = List.of(
                "kafka_connect_connector_metrics",
                "kafka_connect_connector_task_metrics_",
                "kafka_connect_source_task_metrics_",
                "kafka_connect_connect_worker_metrics_connector_count 2.0");
        checkMetrics(prefixes);
    }

    private void checkMetrics(List<String> prefixes) {
        for (GenericContainer<?> worker : connect.getWorkers()) {
            worker.waitingFor(Wait.forHttp("/metrics").forStatusCode(200));
            for (String prefix : prefixes) {
                MetricsUtils.verify(worker, prefix, PORT, metrics -> assertFalse(metrics.isEmpty()));
            }
        }
    }

    private void httpPut(String path, String body) throws Exception {
        HttpClient httpClient = HttpClient.newHttpClient();
        URI uri = new URI(connect.getRestEndpoint() + path);
        HttpRequest request = HttpRequest.newBuilder()
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .setHeader("Content-Type", "application/json")
                .uri(uri)
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(HttpURLConnection.HTTP_CREATED, response.statusCode());
    }
}
