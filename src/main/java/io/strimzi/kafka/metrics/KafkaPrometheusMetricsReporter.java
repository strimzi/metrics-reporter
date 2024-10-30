/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.PrometheusNaming;
import io.strimzi.kafka.metrics.collector.MetricWrapper;
import io.strimzi.kafka.metrics.collector.PrometheusCollector;
import io.strimzi.kafka.metrics.collector.kafka.KafkaCollector;
import io.strimzi.kafka.metrics.collector.kafka.KafkaMetricWrapper;
import io.strimzi.kafka.metrics.http.HttpServers;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.MetricsContext;
import org.apache.kafka.common.metrics.MetricsReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private final KafkaCollector kafkaCollector;
    private final PrometheusCollector prometheusCollector;
    @SuppressFBWarnings({"UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR"}) // This field is initialized in the configure method
    private Optional<HttpServers.ServerCounter> httpServer;
    @SuppressFBWarnings({"UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR"}) // This field is initialized in the contextChange method
    private String prefix;

    /**
     * Constructor
     */
    public KafkaPrometheusMetricsReporter() {
        registry = PrometheusRegistry.defaultRegistry;
        prometheusCollector = PrometheusCollector.register(registry);
        kafkaCollector = KafkaCollector.getCollector(prometheusCollector);
    }

    // for testing
    KafkaPrometheusMetricsReporter(PrometheusRegistry registry, KafkaCollector kafkaCollector, PrometheusCollector prometheusCollector) {
        this.registry = registry;
        this.kafkaCollector = kafkaCollector;
        this.prometheusCollector = prometheusCollector;
    }

    @Override
    public void configure(Map<String, ?> map) {
        PrometheusMetricsReporterConfig config = new PrometheusMetricsReporterConfig(map, registry);
        prometheusCollector.updateAllowlist(config.allowlist());
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
        String prometheusName = KafkaMetricWrapper.prometheusName(prefix, metric.metricName());
        MetricWrapper metricWrapper = new KafkaMetricWrapper(prometheusName, metric, metric.metricName().name());
        kafkaCollector.addMetric(metric.metricName(), metricWrapper);
    }

    @Override
    public void metricRemoval(KafkaMetric metric) {
        kafkaCollector.removeMetric(metric.metricName());
    }

    @Override
    public void close() {
        httpServer.ifPresent(HttpServers::release);
    }

    @Override
    public void reconfigure(Map<String, ?> configs) {
        PrometheusMetricsReporterConfig newConfig = new PrometheusMetricsReporterConfig(configs, null);
        prometheusCollector.updateAllowlist(newConfig.allowlist());
        LOG.debug("KafkaPrometheusMetricsReporter reconfigured with {}", newConfig);
    }

    @Override
    public void validateReconfiguration(Map<String, ?> configs) throws ConfigException {
        new PrometheusMetricsReporterConfig(configs, null);
    }

    @Override
    public Set<String> reconfigurableConfigs() {
        return PrometheusMetricsReporterConfig.RECONFIGURABLES;
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
