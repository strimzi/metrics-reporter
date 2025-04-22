/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.prometheus;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.PrometheusNaming;
import io.strimzi.kafka.metrics.prometheus.common.AbstractReporter;
import io.strimzi.kafka.metrics.prometheus.common.MetricWrapper;
import io.strimzi.kafka.metrics.prometheus.common.PrometheusCollector;
import io.strimzi.kafka.metrics.prometheus.http.HttpServers;
import io.strimzi.kafka.metrics.prometheus.kafka.KafkaCollector;
import io.strimzi.kafka.metrics.prometheus.kafka.KafkaMetricWrapper;
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
import java.util.regex.Pattern;

/**
 * {@link MetricsReporter} implementation that exposes Kafka client metrics in the Prometheus format.
 */
public class ClientMetricsReporter extends AbstractReporter implements MetricsReporter {

    private static final Logger LOG = LoggerFactory.getLogger(ClientMetricsReporter.class);
    static final Set<String> PREFIXES = Set.of(
            "kafka.admin.client",
            "kafka.consumer",
            "kafka.producer",
            "kafka.connect",
            "kafka.connect.mirror",
            "kafka.streams"
    );

    final PrometheusRegistry registry;
    final KafkaCollector kafkaCollector;

    private ClientMetricsReporterConfig config;
    Optional<HttpServers.ServerCounter> httpServer = Optional.empty();
    @SuppressFBWarnings({"UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR"}) // This field is initialized in the contextChange method
    String prefix;

    /**
     * Constructor
     */
    public ClientMetricsReporter() {
        registry = PrometheusRegistry.defaultRegistry;
        kafkaCollector = KafkaCollector.getCollector(PrometheusCollector.register(registry));
        kafkaCollector.addReporter(this);
    }

    // for testing
    ClientMetricsReporter(PrometheusRegistry registry, KafkaCollector kafkaCollector) {
        this.registry = registry;
        this.kafkaCollector = kafkaCollector;
        kafkaCollector.addReporter(this);
    }

    @Override
    public void configure(Map<String, ?> map) {
        config = new ClientMetricsReporterConfig(map, registry);
        httpServer = config.startHttpServer();
        LOG.debug("ClientMetricsReporter configured with {}", config);
    }

    @Override
    public void init(List<KafkaMetric> metrics) {
        for (KafkaMetric metric : metrics) {
            metricChange(metric);
        }
    }

    @Override
    public void metricChange(KafkaMetric metric) {
        String prometheusName = KafkaMetricWrapper.prometheusName(prefix, metric.metricName());
        MetricWrapper metricWrapper = new KafkaMetricWrapper(prometheusName, metric, metric.metricName().name());
        addMetric(metric, metricWrapper);
    }

    @Override
    public void metricRemoval(KafkaMetric metric) {
        removeMetric(metric);
    }

    @Override
    public void close() {
        kafkaCollector.removeReporter(this);
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
        return Set.of();
    }

    @Override
    public void contextChange(MetricsContext metricsContext) {
        String prefix = metricsContext.contextLabels().get(MetricsContext.NAMESPACE);
        if (!PREFIXES.contains(prefix)) {
            throw new IllegalStateException("ClientMetricsReporter should only be used in Kafka clients. " +
                    "Valid prefixes: " + PREFIXES + ", found " + prefix);
        }
        this.prefix = PrometheusNaming.prometheusName(prefix);
    }

    // for testing
    Optional<Integer> getPort() {
        return Optional.ofNullable(httpServer.isPresent() ? httpServer.get().port() : null);
    }

    @Override
    protected Pattern allowlist() {
        return config.allowlist();
    }

}
