/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.prometheus;

import io.prometheus.metrics.model.snapshots.GaugeSnapshot;
import io.prometheus.metrics.model.snapshots.InfoSnapshot;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Utility class to create and retrieve metrics
 */
public class MetricsUtils {

    public static final String VERSION = "1.0.0-SNAPSHOT";
    private static final String CLIENTS_IMAGE = "quay.io/strimzi-test-clients/test-clients:latest-kafka-3.9.0";
    private static final Duration TIMEOUT = Duration.ofSeconds(10L);

    public static final String REPORTER_JARS = "target/client-metrics-reporter-" + VERSION + "/client-metrics-reporter-" + VERSION + "/libs/";
    public static final String MOUNT_PATH = "/opt/strimzi/metrics-reporter/";

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

    public static void verify(GenericContainer<?> container, String prefix, int port, ThrowingConsumer<List<String>> condition) {
        assertTimeoutPreemptively(TIMEOUT, () -> {
            while (true) {
                try {
                    List<String> filteredMetrics = filterMetrics(getMetrics(container.getHost(), container.getMappedPort(port)), prefix);
                    condition.accept(filteredMetrics);
                    return;
                } catch (Throwable t) {
                    assertInstanceOf(AssertionError.class, t);
                    TimeUnit.MILLISECONDS.sleep(100L);
                }
            }
        });
    }

    public static GenericContainer<?> clientContainer(Map<String, String> env, int port) {
        return new GenericContainer<>(CLIENTS_IMAGE)
                .withNetwork(Network.SHARED)
                .withExposedPorts(port)
                .withCopyFileToContainer(MountableFile.forHostPath(REPORTER_JARS), MOUNT_PATH)
                .withEnv(env)
                .waitingFor(Wait.forHttp("/metrics").forStatusCode(200));
    }

}
