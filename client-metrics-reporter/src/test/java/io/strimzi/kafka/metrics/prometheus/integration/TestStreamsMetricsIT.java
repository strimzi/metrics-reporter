/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.prometheus.integration;

import io.strimzi.kafka.metrics.prometheus.ClientMetricsReporter;
import io.strimzi.kafka.metrics.prometheus.ClientMetricsReporterConfig;
import io.strimzi.kafka.metrics.prometheus.MetricsUtils;
import io.strimzi.kafka.metrics.prometheus.http.Listener;
import io.strimzi.test.container.StrimziKafkaContainer;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestStreamsMetricsIT {

    private static final int PORT = Listener.parseListener(ClientMetricsReporterConfig.LISTENER_CONFIG_DEFAULT).port;
    private StrimziKafkaContainer broker;
    private Map<String, String> env;

    @BeforeEach
    public void setUp() throws Exception {
        broker = new StrimziKafkaContainer()
                .withNetworkAliases("kafka");
        broker.start();

        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBootstrapServers()))) {
            admin.createTopics(List.of(
                    new NewTopic("source-topic", 1, (short) -1),
                    new NewTopic("target-topic", 1, (short) -1))
            ).all().get();
        }

        Map<String, Object> producerConfigs = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerConfigs)) {
            for (int i = 0; i < 10; i++) {
                producer.send(new ProducerRecord<>("source-topic", "record" + i));
            }
            producer.flush();
        }

        env = new HashMap<>();
        env.put("CLIENT_TYPE", "KafkaStreams");
        env.put("BOOTSTRAP_SERVERS", "kafka:9091");
        env.put("APPLICATION_ID", "my-app-id");
        env.put("SOURCE_TOPIC", "source-topic");
        env.put("TARGET_TOPIC", "target-topic");
        env.put("ADDITIONAL_CONFIG", "metric.reporters=" + ClientMetricsReporter.class.getName());
        env.put("CLASSPATH", MetricsUtils.MOUNT_PATH + "*");
    }

    @AfterEach
    public void tearDown() {
        broker.stop();
    }

    @Test
    public void testStreamsMetrics() {
        try (GenericContainer<?> streams = MetricsUtils.clientContainer(env, PORT)) {
            streams.start();

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
                    "kafka_streams_stream_metrics_",
                    "kafka_streams_stream_processor_node_metrics_",
                    "kafka_streams_stream_state_updater_metrics_",
                    "kafka_streams_stream_task_metrics_",
                    "kafka_streams_stream_thread_metrics_",
                    "kafka_streams_stream_topic_metrics_");
            for (String prefix : prefixes) {
                MetricsUtils.verify(streams, prefix, PORT, metrics -> assertFalse(metrics.isEmpty()));
            }
        }
    }

    @Test
    public void testStreamsMetricsWithAllowlist() {
        env.put("ADDITIONAL_CONFIG",
                "metric.reporters=" + ClientMetricsReporter.class.getName() + "\n" +
                        "prometheus.metrics.reporter.allowlist=kafka_consumer_.*,kafka_streams_stream_metrics_.*");
        try (GenericContainer<?> streams = MetricsUtils.clientContainer(env, PORT)) {
            streams.start();

            List<String> allowedPrefixes = List.of(
                    "jvm_",
                    "process_",
                    "kafka_consumer_app_info_",
                    "kafka_consumer_kafka_metrics_",
                    "kafka_consumer_consumer_metrics_",
                    "kafka_consumer_consumer_node_metrics_",
                    "kafka_consumer_consumer_coordinator_metrics_",
                    "kafka_consumer_consumer_fetch_manager_metrics_",
                    "kafka_streams_stream_metrics_");
            for (String prefix : allowedPrefixes) {
                MetricsUtils.verify(streams, prefix, PORT, metrics -> assertFalse(metrics.isEmpty()));
            }
            List<String> disallowedPrefixes = List.of(
                    "kafka_admin_client_app_info_",
                    "kafka_admin_client_kafka_metrics_",
                    "kafka_admin_client_admin_client_metrics_",
                    "kafka_admin_client_admin_client_node_metrics_",
                    "kafka_producer_app_info_",
                    "kafka_producer_kafka_metrics_",
                    "kafka_producer_producer_metrics_",
                    "kafka_producer_producer_node_metrics_",
                    "kafka_producer_producer_topic_metrics_",
                    "kafka_streams_stream_processor_node_metrics_",
                    "kafka_streams_stream_state_updater_metrics_",
                    "kafka_streams_stream_task_metrics_",
                    "kafka_streams_stream_thread_metrics_",
                    "kafka_streams_stream_topic_metrics_");
            for (String prefix : disallowedPrefixes) {
                MetricsUtils.verify(streams, prefix, PORT, metrics -> assertTrue(metrics.isEmpty()));
            }
        }
    }
}
