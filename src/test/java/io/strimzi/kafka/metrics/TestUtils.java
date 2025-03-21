/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import io.prometheus.metrics.model.snapshots.CounterSnapshot;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot;
import io.prometheus.metrics.model.snapshots.InfoSnapshot;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;
import io.prometheus.metrics.model.snapshots.Quantiles;
import io.prometheus.metrics.model.snapshots.SummarySnapshot;
import io.strimzi.kafka.metrics.http.Listener;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.metrics.Gauge;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.MetricConfig;
import org.apache.kafka.common.utils.Time;
import org.junit.jupiter.api.function.ThrowingConsumer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Utility class to create and retrieve metrics
 */
@SuppressWarnings("ClassFanOutComplexity")
public class TestUtils {

    private static final String VERSION = "1.0.0-SNAPSHOT";
    private static final String KAFKA_VERSION = "3.9.0";
    private static final String CLIENTS_IMAGE = "quay.io/strimzi-test-clients/test-clients:latest-kafka-" + KAFKA_VERSION;
    private static final Duration TIMEOUT = Duration.ofSeconds(10L);

    public static final String REPORTER_JARS = "target/metrics-reporter-" + VERSION + "/metrics-reporter-" + VERSION + "/libs/";
    public static final String MOUNT_PATH = "/opt/strimzi/metrics-reporter/";
    public static final int PORT = Listener.parseListener(PrometheusMetricsReporterConfig.LISTENER_CONFIG_DEFAULT).port;
    public static final String KAFKA_NETWORK_ALIAS = "kafka";

    /**
     * Query the HTTP endpoint and returns the output
     * @param port The port to query
     * @return The lines from the output
     * @throws Exception If any error occurs
     */
    public static List<String> getMetrics(int port) throws Exception {
        return getMetrics("localhost", port);
    }

    /**
     * Query the HTTP endpoint and returns the output
     * @param host The host to query
     * @param port The port to query
     * @return The lines from the output
     * @throws Exception If any error occurs
     */
    public static List<String> getMetrics(String host, int port) throws Exception {
        List<String> metrics = new ArrayList<>();
        URL url = new URL("http://" + host + ":" + port + "/metrics");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");

        try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                if (!inputLine.startsWith("#")) {
                    metrics.add(inputLine);
                }
            }
        }
        return metrics;
    }

    /**
     * Create a new Kafka metric
     * @param name The name of the metric
     * @param group The group of the metric
     * @param gauge The gauge providing the value of the metric
     * @param labels The labels of the metric
     * @return The Kafka metric
     */
    public static KafkaMetric newKafkaMetric(String name, String group, Gauge<?> gauge, Map<String, String> labels) {
        return new KafkaMetric(
                new Object(),
                new MetricName(name, group, "", labels),
                gauge,
                new MetricConfig(),
                Time.SYSTEM);
    }

    /**
     * Create a new Yammer metric
     * @param valueSupplier The supplier providing the value of the metric
     * @return The Yammer metric
     */
    public static <T> com.yammer.metrics.core.Gauge<T> newYammerMetric(Supplier<T> valueSupplier) {
        return new com.yammer.metrics.core.Gauge<>() {
            @Override
            public T value() {
                return valueSupplier.get();
            }
        };
    }

    /**
     * Check a Gauge snapshot
     * @param snapshot the gauge snapshot
     * @param expectedValue the expected value
     * @param expectedLabels the expected labels
     */
    public static void assertGaugeSnapshot(MetricSnapshot snapshot, double expectedValue, Labels expectedLabels) {
        assertInstanceOf(GaugeSnapshot.class, snapshot);
        GaugeSnapshot gaugeSnapshot = (GaugeSnapshot) snapshot;
        assertEquals(1, gaugeSnapshot.getDataPoints().size());
        GaugeSnapshot.GaugeDataPointSnapshot datapoint = gaugeSnapshot.getDataPoints().get(0);
        assertEquals(expectedValue, datapoint.getValue());
        assertEquals(expectedLabels, datapoint.getLabels());
    }

    /**
     * Check a Counter snapshot
     * @param snapshot the counter snapshot
     * @param expectedValue the expected value
     * @param expectedLabels the expected labels
     */
    public static void assertCounterSnapshot(MetricSnapshot snapshot, double expectedValue, Labels expectedLabels) {
        assertInstanceOf(CounterSnapshot.class, snapshot);
        CounterSnapshot counterSnapshot = (CounterSnapshot) snapshot;
        assertEquals(1, counterSnapshot.getDataPoints().size());
        CounterSnapshot.CounterDataPointSnapshot datapoint = counterSnapshot.getDataPoints().get(0);
        assertEquals(expectedValue, datapoint.getValue());
        assertEquals(expectedLabels, datapoint.getLabels());
    }

    /**
     * Check an Info snapshot
     * @param snapshot the info snapshot
     * @param labels the existing labels
     * @param newLabelName the expected new label name
     * @param newLabelValue the expected new label value
     */
    public static void assertInfoSnapshot(MetricSnapshot snapshot, Labels labels, String newLabelName, String newLabelValue) {
        assertInstanceOf(InfoSnapshot.class, snapshot);
        InfoSnapshot infoSnapshot = (InfoSnapshot) snapshot;
        assertEquals(1, infoSnapshot.getDataPoints().size());
        Labels expectedLabels = labels.add(newLabelName, newLabelValue);
        assertEquals(expectedLabels, infoSnapshot.getDataPoints().get(0).getLabels());
    }

    /**
     * Check a Summary snapshot
     * @param snapshot the summary snapshot
     * @param expectedCount the expected count
     * @param expectedSum the expected sum
     * @param expectedLabels the expected labels
     * @param expectedQuantiles the expected quantiles
     */
    public static void assertSummarySnapshot(MetricSnapshot snapshot, int expectedCount, double expectedSum, Labels expectedLabels, Quantiles expectedQuantiles) {
        assertInstanceOf(SummarySnapshot.class, snapshot);
        SummarySnapshot summarySnapshot = (SummarySnapshot) snapshot;
        assertEquals(1, summarySnapshot.getDataPoints().size());
        SummarySnapshot.SummaryDataPointSnapshot datapoint = summarySnapshot.getDataPoints().get(0);
        assertEquals(expectedCount, datapoint.getCount());
        assertEquals(expectedSum, datapoint.getSum());
        assertEquals(expectedQuantiles, datapoint.getQuantiles());
        assertEquals(expectedLabels, datapoint.getLabels());
    }

    /**
     * Filter metrics that start with a specified prefix
     * @param allMetrics all the metric names
     * @param prefix the prefix
     * @return the list of metric names that start with the prefix
     */
    public static List<String> filterMetrics(List<String> allMetrics, String prefix) {
        List<String> metrics = new ArrayList<>();
        for (String metric : allMetrics) {
            if (metric.startsWith(prefix)) {
                metrics.add(metric);
            }
        }
        return metrics;
    }

    public static void verify(GenericContainer<?> container, String prefix, ThrowingConsumer<List<String>> condition) {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            while (true) {
                try {
                    List<String> filteredMetrics = filterMetrics(getMetrics(container.getHost(), container.getMappedPort(PORT)), prefix);
                    condition.accept(filteredMetrics);
                    return;
                } catch (Throwable t) {
                    assertInstanceOf(AssertionError.class, t);
                    TimeUnit.MILLISECONDS.sleep(100L);
                }
            }
        });
    }

    public static GenericContainer<?> clientContainer(Map<String, String> env) {
        return new GenericContainer<>(CLIENTS_IMAGE)
                .withNetwork(Network.SHARED)
                .withExposedPorts(PORT)
                .withCopyFileToContainer(MountableFile.forHostPath(REPORTER_JARS), MOUNT_PATH)
                .withEnv(env)
                .waitingFor(Wait.forHttp("/metrics").forStatusCode(200));
    }

}
