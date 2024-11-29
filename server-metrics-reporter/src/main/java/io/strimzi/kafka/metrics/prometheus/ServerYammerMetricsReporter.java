/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.prometheus;

import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Metric;
import com.yammer.metrics.core.MetricName;
import com.yammer.metrics.core.MetricsRegistry;
import com.yammer.metrics.core.MetricsRegistryListener;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.strimzi.kafka.metrics.prometheus.common.AbstractReporter;
import io.strimzi.kafka.metrics.prometheus.common.MetricWrapper;
import io.strimzi.kafka.metrics.prometheus.common.PrometheusCollector;
import io.strimzi.kafka.metrics.prometheus.yammer.YammerCollector;
import io.strimzi.kafka.metrics.prometheus.yammer.YammerMetricWrapper;
import kafka.metrics.KafkaMetricsReporter;
import kafka.utils.VerifiableProperties;
import org.apache.kafka.server.metrics.KafkaYammerMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * ServerYammerMetricsReporter to export Yammer broker metrics in the Prometheus format.
 */
public class ServerYammerMetricsReporter extends AbstractReporter implements KafkaMetricsReporter, MetricsRegistryListener {

    private static final Logger LOG = LoggerFactory.getLogger(ServerYammerMetricsReporter.class);

    private static ServerYammerMetricsReporter instance;

    private final PrometheusRegistry registry;
    private final YammerCollector yammerCollector;
    @SuppressFBWarnings({"UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR"}) // This field is initialized in the init method
    /* test */ ServerMetricsReporterConfig config;

    /**
     * Retrieve the ServerYammerMetricsReporter instance
     * @return The ServerYammerMetricsReporter singleton
     */
    public static ServerYammerMetricsReporter getInstance() {
        return instance;
    }

    /**
     * Constructor
     */
    @SuppressFBWarnings("CT_CONSTRUCTOR_THROW")
    public ServerYammerMetricsReporter() {
        if (instance != null) {
            throw new IllegalStateException("Cannot create multiple instances of ServerYammerMetricsReporter");
        }
        instance = this;
        registry = PrometheusRegistry.defaultRegistry;
        yammerCollector = YammerCollector.getCollector(PrometheusCollector.register(registry));
        yammerCollector.addReporter(this);
    }

    // for testing
    ServerYammerMetricsReporter(PrometheusRegistry registry, YammerCollector yammerCollector) {
        this.registry = registry;
        this.yammerCollector = yammerCollector;
        yammerCollector.addReporter(this);
    }

    @Override
    public void init(VerifiableProperties props) {
        config = new ServerMetricsReporterConfig(props.props(), registry);
        for (MetricsRegistry yammerRegistry : List.of(KafkaYammerMetrics.defaultRegistry(), Metrics.defaultRegistry())) {
            yammerRegistry.addListener(this);
        }
        LOG.debug("ServerYammerMetricsReporter configured with {}", config);
    }

    @Override
    public void onMetricAdded(MetricName name, Metric metric) {
        String prometheusName = YammerMetricWrapper.prometheusName(name);
        MetricWrapper metricWrapper = new YammerMetricWrapper(prometheusName, name.getScope(), metric, name.getName());
        addMetric(name, metricWrapper);
    }

    @Override
    public void onMetricRemoved(MetricName name) {
        removeMetric(name);
    }

    /**
     * Update the reconfigurable configurations
     * @param props The new configuration
     */
    public void reconfigure(Map<String, ?> props) {
        config.reconfigure(props);
        updateAllowedMetrics();
    }

    @Override
    protected Pattern allowlist() {
        return config.allowlist();
    }

    @Override
    protected boolean isReconfigurable() {
        return true;
    }
}
