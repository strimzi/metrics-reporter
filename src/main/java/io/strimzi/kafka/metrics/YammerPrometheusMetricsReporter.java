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
import io.strimzi.kafka.metrics.collector.MetricWrapper;
import io.strimzi.kafka.metrics.collector.PrometheusCollector;
import io.strimzi.kafka.metrics.collector.yammer.YammerCollector;
import io.strimzi.kafka.metrics.collector.yammer.YammerMetricWrapper;
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
    private final YammerCollector yammerCollector;
    private final PrometheusCollector prometheusCollector;
    @SuppressFBWarnings({"UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR"}) // This field is initialized in the init method
    /* test */ PrometheusMetricsReporterConfig config;

    /**
     * Constructor
     */
    public YammerPrometheusMetricsReporter() {
        registry = PrometheusRegistry.defaultRegistry;
        prometheusCollector = PrometheusCollector.register(registry);
        yammerCollector = YammerCollector.getCollector(prometheusCollector);
    }

    // for testing
    YammerPrometheusMetricsReporter(PrometheusRegistry registry, PrometheusCollector prometheusCollector) {
        this.registry = registry;
        yammerCollector = YammerCollector.getCollector(prometheusCollector);
        this.prometheusCollector = prometheusCollector;
    }

    @Override
    public void init(VerifiableProperties props) {
        config = new PrometheusMetricsReporterConfig(props.props(), registry);
        for (MetricsRegistry yammerRegistry : Arrays.asList(KafkaYammerMetrics.defaultRegistry(), Metrics.defaultRegistry())) {
            yammerRegistry.addListener(this);
        }
        yammerCollector.updateAllowlist(config.allowlist());
        LOG.debug("YammerPrometheusMetricsReporter configured with {}", config);
    }

    @Override
    public void onMetricAdded(MetricName name, Metric metric) {
        String prometheusName = YammerMetricWrapper.prometheusName(name);
        MetricWrapper metricWrapper = new YammerMetricWrapper(prometheusName, name.getScope(), metric, name.getName());
        yammerCollector.addMetric(name, metricWrapper);
    }

    @Override
    public void onMetricRemoved(MetricName name) {
        yammerCollector.removeMetric(name);
    }
}
