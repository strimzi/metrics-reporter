/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import io.prometheus.metrics.model.registry.PrometheusRegistry;
import kafka.metrics.KafkaMetricsReporter;
import kafka.utils.VerifiableProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * KafkaMetricsReporter to export Kafka broker metrics in the Prometheus format.
 */
public class YammerPrometheusMetricsReporter implements KafkaMetricsReporter {

    private static final Logger LOG = LoggerFactory.getLogger(YammerPrometheusMetricsReporter.class.getName());

    private final PrometheusRegistry registry;

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
        LOG.info(">>> in init() yammer");
        PrometheusMetricsReporterConfig config = new PrometheusMetricsReporterConfig(props.props(), registry);
        registry.register(new YammerMetricsCollector(config));
    }

}
