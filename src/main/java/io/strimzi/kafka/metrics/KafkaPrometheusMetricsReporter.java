/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.PrometheusNaming;
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
 * This can be used by Kafka brokers and clients.
 */
public class KafkaPrometheusMetricsReporter implements MetricsReporter {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaPrometheusMetricsReporter.class);

    private final PrometheusRegistry registry;
    private final PrometheusCollector collector;
    @SuppressFBWarnings({"UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR"}) // This field is initialized in the configure method
    private PrometheusMetricsReporterConfig config;
    @SuppressFBWarnings({"UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR"}) // This field is initialized in the configure method
    private Optional<HttpServers.ServerCounter> httpServer;
    @SuppressFBWarnings({"UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR"}) // This field is initialized in the contextChange method
    private String prefix;

    /**
     * Constructor
     */
    public KafkaPrometheusMetricsReporter() {
        registry = PrometheusRegistry.defaultRegistry;
        collector = PrometheusCollector.register(registry);
    }

    // for testing
    KafkaPrometheusMetricsReporter(PrometheusRegistry registry, PrometheusCollector collector) {
        this.registry = registry;
        this.collector = collector;
    }

    @Override
    public void configure(Map<String, ?> map) {
        config = new PrometheusMetricsReporterConfig(map, registry);
        httpServer = config.startHttpServer();
        LOG.debug("KafkaPrometheusMetricsReporter configured with {}", config);
    }

    @Override
    public void init(List<KafkaMetric> metrics) {
        for (KafkaMetric metric : metrics) {
            metricChange(metric);
        }
    }

    public void metricChange(KafkaMetric metric) {
        String prometheusName = MetricWrapper.prometheusName(prefix, metric.metricName());
        if (!config.isAllowed(prometheusName)) {
            LOG.trace("Ignoring metric {} as it does not match the allowlist", prometheusName);
        } else {
            MetricWrapper metricWrapper = new MetricWrapper(prometheusName, metric, metric.metricName().name());
            collector.addKafkaMetric(metric.metricName(), metricWrapper);
        }
    }

    @Override
    public void metricRemoval(KafkaMetric metric) {
        collector.removeKafkaMetric(metric.metricName());
    }

    @Override
    public void close() {
        httpServer.ifPresent(HttpServers::release);
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
        this.prefix = PrometheusNaming.prometheusName(prefix);
    }

    // for testing
    Optional<Integer> getPort() {
        return Optional.ofNullable(httpServer.isPresent() ? httpServer.get().port() : null);
    }
}
