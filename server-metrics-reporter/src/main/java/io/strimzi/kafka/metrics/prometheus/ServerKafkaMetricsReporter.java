/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.prometheus;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.PrometheusNaming;
import io.strimzi.kafka.metrics.prometheus.common.PrometheusCollector;
import io.strimzi.kafka.metrics.prometheus.kafka.KafkaCollector;
import io.strimzi.kafka.metrics.prometheus.telemetry.ClientTelemetryCollector;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.metrics.MetricsContext;
import org.apache.kafka.server.telemetry.ClientTelemetryExporter;
import org.apache.kafka.server.telemetry.ClientTelemetryExporterProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * MetricsReporter implementation that expose Kafka server metrics in the Prometheus format.
 */
public class ServerKafkaMetricsReporter extends ClientMetricsReporter implements ClientTelemetryExporterProvider {

    private static final Logger LOG = LoggerFactory.getLogger(ServerKafkaMetricsReporter.class);

    @SuppressFBWarnings({"UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR"}) // This field is initialized in the configure method
    private ServerMetricsReporterConfig config;
    @SuppressFBWarnings({"UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR"}) // This field is initialized in the configure method
    private ClientTelemetryCollector telemetryCollector;

    /**
     * Constructor
     */
    public ServerKafkaMetricsReporter() {
        super();
    }

    // for testing
    ServerKafkaMetricsReporter(PrometheusRegistry registry, KafkaCollector kafkaCollector) {
        super(registry, kafkaCollector);
    }

    @Override
    public void configure(Map<String, ?> map) {
        config = new ServerMetricsReporterConfig(map, registry);
        telemetryCollector = new ClientTelemetryCollector(PrometheusCollector.register(registry), config.telemetryLabels());
        httpServer = config.startHttpServer();
        LOG.debug("ServerKafkaMetricsReporter configured with {}", config);
    }

    @Override
    public void reconfigure(Map<String, ?> configs) {
        config.reconfigure(configs);
        telemetryCollector.updateTelemetryLabels(config.telemetryLabels());
        ServerYammerMetricsReporter yammerReporter = ServerYammerMetricsReporter.getInstance();
        if (yammerReporter != null) {
            yammerReporter.reconfigure(configs);
        }
        updateAllowedMetrics();
    }

    @Override
    public void validateReconfiguration(Map<String, ?> configs) throws ConfigException {
        config.validate(configs);
    }

    @Override
    public Set<String> reconfigurableConfigs() {
        return ServerMetricsReporterConfig.RECONFIGURABLES;
    }

    @Override
    public void contextChange(MetricsContext metricsContext) {
        String prefix = metricsContext.contextLabels().get(MetricsContext.NAMESPACE);
        this.prefix = PrometheusNaming.prometheusName(prefix);
    }

    @Override
    protected boolean isReconfigurable() {
        return true;
    }

    @Override
    protected Pattern allowlist() {
        return config.allowlist();
    }

    @Override
    public ClientTelemetryExporter clientTelemetryExporter() {
        return telemetryCollector;
    }
}
