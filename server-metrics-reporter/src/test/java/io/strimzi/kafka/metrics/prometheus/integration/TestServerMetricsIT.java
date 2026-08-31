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
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.errors.InvalidRequestException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static io.strimzi.kafka.metrics.prometheus.MetricsUtils.VERSION;
import static io.strimzi.kafka.metrics.prometheus.ServerMetricsReporterConfig.ALLOWLIST_CONFIG;
import static io.strimzi.kafka.metrics.prometheus.ServerMetricsReporterConfig.CLIENT_ADDRESS_LABEL;
import static io.strimzi.kafka.metrics.prometheus.ServerMetricsReporterConfig.CLIENT_ID_LABEL;
import static io.strimzi.kafka.metrics.prometheus.ServerMetricsReporterConfig.CLIENT_TELEMETRY_LABELS_CONFIG;
import static io.strimzi.kafka.metrics.prometheus.ServerMetricsReporterConfig.LISTENER_NAME_LABEL;
import static io.strimzi.kafka.metrics.prometheus.ServerMetricsReporterConfig.PRINCIPAL_LABEL;
import static io.strimzi.kafka.metrics.prometheus.ServerMetricsReporterConfig.SECURITY_PROTOCOL_LABEL;
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
        assertNoClientMetrics();
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

        assertNoClientMetrics();
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
        assertNoClientMetrics();

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
        assertNoClientMetrics();
    }

    @Test
    public void testTopicWithDotsInName() throws Exception {
        setupCluster(Map.of());
        String topic = "env.topicname.version";

        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, cluster.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(topic, 1, (short) -1))).all().get();
        }

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, cluster.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName()))) {
            producer.send(new ProducerRecord<>(topic, "key", "value")).get();
        }

        List<String> patterns = List.of("kafka_server_brokertopicmetrics_.*topic=\"" + topic + "\".*");
        for (GenericContainer<?> broker : cluster.getNodes()) {
            MetricsUtils.verify(broker, patterns, PORT, metrics -> assertFalse(metrics.isEmpty()));
        }
        assertNoClientMetrics();
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

    @Test
    public void testClientTelemetryMetrics() throws Exception {
        int interval = 5000;
        setupCluster(Map.of(CLIENT_TELEMETRY_LABELS_CONFIG, CLIENT_ID_LABEL));
        assertNoClientMetrics();

        createSubscription("producer-metrics", Map.of(
                "interval.ms", String.valueOf(interval),
                "metrics", "org.apache.kafka.producer"));

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, cluster.getBootstrapServers(),
                ProducerConfig.CLIENT_ID_CONFIG, "test-producer",
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName()))) {
            for (int i = 0; i < 10; i++) {
                producer.send(new ProducerRecord<>("test-topic", "key", "value" + i)).get();
            }
            producer.flush();

            List<String> patterns = List.of("clients_org_apache_kafka_producer.*");
            for (GenericContainer<?> broker : cluster.getNodes()) {
                MetricsUtils.verify(broker, patterns, PORT, metrics -> {
                    assertFalse(metrics.isEmpty(), "Expected client telemetry metrics with clients_ prefix");
                    assertTrue(metrics.stream().anyMatch(m -> m.contains("client_instance_id")),
                            "Expected client_instance_id label");
                    assertTrue(metrics.stream().anyMatch(m -> m.contains("client_id=\"test-producer\"")),
                            "Expected client_id label");
                });
            }
        }

        Thread.sleep(interval * 2);
        for (GenericContainer<?> broker : cluster.getNodes()) {
            MetricsUtils.verify(broker, List.of("clients_.*"), PORT, metrics ->
                assertTrue(metrics.isEmpty(), "Expected no client telemetry metrics"));
        }
    }

    @Test
    public void testReconfigureTelemetryLabels() throws Exception {
        int interval = 1000;
        List<String> labels = List.of(CLIENT_ID_LABEL, LISTENER_NAME_LABEL, SECURITY_PROTOCOL_LABEL, PRINCIPAL_LABEL, CLIENT_ADDRESS_LABEL);
        setupCluster(Map.of(CLIENT_TELEMETRY_LABELS_CONFIG, String.join(",", labels)));
        assertNoClientMetrics();

        createSubscription("consumer-metrics", Map.of(
                "interval.ms", String.valueOf(interval),
                "metrics", "org.apache.kafka.consumer"));

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, cluster.getBootstrapServers(),
                ConsumerConfig.CLIENT_ID_CONFIG, "test-consumer",
                ConsumerConfig.GROUP_ID_CONFIG, "test-group",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()))) {
            consumer.subscribe(List.of("test-topic"));
            for (int i = 0; i < 10; i++) {
                consumer.poll(Duration.ofMillis(500L));
            }

            List<String> patterns = List.of("clients_org_apache_kafka_consumer.*");
            for (GenericContainer<?> broker : cluster.getNodes()) {
                MetricsUtils.verify(broker, patterns, PORT, metrics -> {
                    assertFalse(metrics.isEmpty(), "Expected client telemetry metrics with clients_ prefix");
                    assertTrue(metrics.stream().allMatch(m -> m.contains("client_instance_id")),
                            "Expected client_instance_id label");
                    for (String label : labels) {
                        assertTrue(metrics.stream().allMatch(m -> m.contains(label + "=")), "Expected " + label + " label");
                    }
                });
            }

            List<String> newLabels = List.of(CLIENT_ID_LABEL, CLIENT_ADDRESS_LABEL);
            try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, cluster.getBootstrapServers()))) {
                admin.incrementalAlterConfigs(Map.of(
                        new ConfigResource(ConfigResource.Type.BROKER, ""),
                        List.of(new AlterConfigOp(
                                new ConfigEntry(CLIENT_TELEMETRY_LABELS_CONFIG, String.join(",", newLabels)),
                                AlterConfigOp.OpType.SET))
                )).all().get();
            }

            List<String> oldLabels = new ArrayList<>(labels);
            oldLabels.removeAll(newLabels);
            for (GenericContainer<?> broker : cluster.getNodes()) {
                MetricsUtils.verify(broker, patterns, PORT, metrics -> {
                    assertFalse(metrics.isEmpty(), "Expected client telemetry metrics with clients_ prefix");
                    assertTrue(metrics.stream().allMatch(m -> m.contains("client_instance_id")),
                            "Expected client_instance_id label");
                    for (String label : newLabels) {
                        assertTrue(metrics.stream().allMatch(m -> m.contains(label + "=")), "Expected " + label + " label");
                    }
                    for (String label : oldLabels) {
                        assertFalse(metrics.stream().anyMatch(m -> m.contains(label + "=")), "Did not expect " + label + " label");
                    }
                });
            }
        }
    }

    private void createSubscription(String name, Map<String, String> subscriptionConfigs) throws Exception {
        ConfigResource resource = new ConfigResource(ConfigResource.Type.CLIENT_METRICS, name);
        List<AlterConfigOp> alterEntries = subscriptionConfigs.entrySet().stream()
                .map(entry -> new AlterConfigOp(
                        new ConfigEntry(entry.getKey(), entry.getValue()),
                        AlterConfigOp.OpType.SET))
                .toList();
        try (Admin admin = Admin.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, cluster.getBootstrapServers()))) {
            admin.incrementalAlterConfigs(Map.of(resource, alterEntries)).all().get();
        }
    }

    private void assertNoClientMetrics() {
        List<String> patterns = List.of("clients_.*");
        for (GenericContainer<?> broker : cluster.getNodes()) {
            MetricsUtils.verify(broker, patterns, PORT, metrics -> assertTrue(metrics.isEmpty(),
                    "Expected no client telemetry metrics without subscription"));
        }
    }
}
