/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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
import java.util.Optional;
import java.util.Set;

/**
 * MetricsReporter implementation that expose Kafka metrics in the Prometheus format.
 *
 * This can be used by Kafka brokers and clients.
 */
public class KafkaPrometheusMetricsReporter implements MetricsReporter {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaPrometheusMetricsReporter.class);

    private final PrometheusRegistry registry;
    @SuppressFBWarnings({"UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR"}) // This field is initialized in the configure method
    private KafkaMetricsCollector kafkaMetricsCollector;
    @SuppressFBWarnings({"UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR"}) // This field is initialized in the configure method
    private Optional<HTTPServer> httpServer;

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
        LOG.debug("KafkaPrometheusMetricsReporter configured with {}", config);
    }

    @Override
    public void init(List<KafkaMetric> metrics) {
        registry.register(kafkaMetricsCollector);
        for (KafkaMetric metric : metrics) {
            metricChange(metric);
        }
    }

    @Override
    public void metricChange(KafkaMetric metric) {
        kafkaMetricsCollector.addMetric(metric);
    }

    @Override
    public void metricRemoval(KafkaMetric metric) {
        kafkaMetricsCollector.removeMetric(metric);
    }

    @Override
    public void close() {
        registry.unregister(kafkaMetricsCollector);
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
        String prefix = metricsContext.contextLabels().get(MetricsContext.NAMESPACE);
        kafkaMetricsCollector.setPrefix(prefix);
    }

    // for testing
    Optional<Integer> getPort() {
        return Optional.ofNullable(httpServer.isPresent() ? httpServer.get().getPort() : null);
    }
}
