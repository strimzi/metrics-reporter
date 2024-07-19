/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import io.prometheus.metrics.exporter.httpserver.HTTPServer;
import io.prometheus.metrics.instrumentation.jvm.JvmMetrics;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.MetricsContext;
import org.apache.kafka.common.metrics.MetricsReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * MetricsReporter implementation that expose Kafka metrics in the Prometheus format.
 *
 * This can be used by Kafka brokers and clients.
 */
public class KafkaPrometheusMetricsReporter implements MetricsReporter {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaPrometheusMetricsReporter.class.getName());

    private final PrometheusRegistry registry;
    private KafkaMetricsCollector kafkaMetricsCollector;
    private Optional<HTTPServer> httpServer = Optional.empty();

    /**
     * Constructor
     */
    public KafkaPrometheusMetricsReporter() {
        registry = PrometheusRegistry.defaultRegistry;
    }

    // for testing
    KafkaPrometheusMetricsReporter(PrometheusRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void configure(Map<String, ?> map) {
        PrometheusMetricsReporterConfig config = new PrometheusMetricsReporterConfig(map, registry);
        kafkaMetricsCollector = new KafkaMetricsCollector(config);
        // Add JVM metrics
        JvmMetrics.builder().register(registry);
        httpServer = config.startHttpServer();
    }

    @Override
    public void init(List<KafkaMetric> metrics) {
        Objects.requireNonNull(kafkaMetricsCollector);
        registry.register(kafkaMetricsCollector);
        for (KafkaMetric metric : metrics) {
            metricChange(metric);
        }
    }

    @Override
    public void metricChange(KafkaMetric metric) {
        Objects.requireNonNull(kafkaMetricsCollector);
        LOG.info("Kafka metricChange {}", metric.metricName());
        kafkaMetricsCollector.addMetric(metric);
    }

    @Override
    public void metricRemoval(KafkaMetric metric) {
        Objects.requireNonNull(kafkaMetricsCollector);
        LOG.info("Kafka metricRemoval {}", metric.metricName());
        kafkaMetricsCollector.removeMetric(metric);
    }

    @Override
    public void close() {
        Objects.requireNonNull(kafkaMetricsCollector);
        registry.unregister(kafkaMetricsCollector);
        LOG.info("Closing the HTTP server");
    }

    @Override
    public void reconfigure(Map<String, ?> configs) {
    }

    @Override
    public void validateReconfiguration(Map<String, ?> configs) throws ConfigException {
    }

    @Override
    public Set<String> reconfigurableConfigs() {
        return Collections.emptySet();
    }

    @Override
    public void contextChange(MetricsContext metricsContext) {
        Objects.requireNonNull(kafkaMetricsCollector);
        LOG.info("Kafka contextChange with {}", metricsContext.contextLabels());
        String prefix = metricsContext.contextLabels().get(MetricsContext.NAMESPACE);
        kafkaMetricsCollector.setPrefix(prefix);
    }

    // for testing
    Optional<Integer> getPort() {
        return Optional.ofNullable(httpServer.isPresent() ? httpServer.get().getPort() : null);
    }
}
