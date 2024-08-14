/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Metric;
import com.yammer.metrics.core.MetricName;
import com.yammer.metrics.core.MetricsRegistry;
import com.yammer.metrics.core.MetricsRegistryListener;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import kafka.metrics.KafkaMetricsReporter;
import kafka.utils.VerifiableProperties;
import org.apache.kafka.server.metrics.KafkaYammerMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * KafkaMetricsReporter to export Kafka broker metrics in the Prometheus format.
 */
public class YammerPrometheusMetricsReporter implements KafkaMetricsReporter, MetricsRegistryListener {

    private static final Logger LOG = LoggerFactory.getLogger(YammerPrometheusMetricsReporter.class);

    private final PrometheusRegistry registry;
    @SuppressFBWarnings({"UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR"}) // This field is initialized in the init method
    private YammerMetricsCollector collector;
    @SuppressFBWarnings({"UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR"}) // This field is initialized in the init method
    /* test */ PrometheusMetricsReporterConfig config;

    /**
     * Constructor
     */
    public YammerPrometheusMetricsReporter() {
        this(PrometheusRegistry.defaultRegistry);
    }

    // for testing
    YammerPrometheusMetricsReporter(PrometheusRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void init(VerifiableProperties props) {
        config = new PrometheusMetricsReporterConfig(props.props(), registry);
        collector = new YammerMetricsCollector();
        registry.register(collector);
        for (MetricsRegistry yammerRegistry : Arrays.asList(KafkaYammerMetrics.defaultRegistry(), Metrics.defaultRegistry())) {
            yammerRegistry.addListener(this);
        }
        LOG.debug("YammerPrometheusMetricsReporter configured with {}", config);
    }

    @Override
    public void onMetricAdded(MetricName name, Metric metric) {
        String prometheusName = MetricWrapper.prometheusName(name);
        if (!config.isAllowed(prometheusName)) {
            LOG.trace("Ignoring metric {} as it does not match the allowlist", prometheusName);
        } else {
            MetricWrapper metricWrapper = new MetricWrapper(prometheusName, name.getScope(), metric, name.getName());
            collector.addMetric(name, metricWrapper);
        }
    }

    @Override
    public void onMetricRemoved(MetricName name) {
        collector.removeMetric(name);
    }
}
