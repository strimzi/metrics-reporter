/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import io.prometheus.client.CollectorRegistry;
import kafka.metrics.KafkaMetricsReporter;
import kafka.utils.VerifiableProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class YammerPrometheusMetricsReporter implements KafkaMetricsReporter {

    private static final Logger LOG = LoggerFactory.getLogger(YammerPrometheusMetricsReporter.class.getName());

    @Override
    public void init(VerifiableProperties props) {
        LOG.info(">>> in init() yammer");
        PrometheusMetricsReporterConfig config = new PrometheusMetricsReporterConfig(props.props());
        LOG.info("yammer defaultRegistry" + CollectorRegistry.defaultRegistry);
        CollectorRegistry.defaultRegistry.register(new YammerMetricsCollector(config));
    }

}
