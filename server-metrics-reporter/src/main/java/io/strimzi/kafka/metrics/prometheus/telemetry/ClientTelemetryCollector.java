/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.prometheus.telemetry;

import io.prometheus.metrics.model.snapshots.CounterSnapshot;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;
import io.prometheus.metrics.model.snapshots.PrometheusNaming;
import io.strimzi.kafka.metrics.prometheus.common.DataPointSnapshotBuilder;
import io.strimzi.kafka.metrics.prometheus.common.MetricsCollector;
import io.strimzi.kafka.metrics.prometheus.common.PrometheusCollector;
import org.apache.kafka.server.authorizer.AuthorizableRequestContext;
import org.apache.kafka.server.telemetry.ClientTelemetryContext;
import org.apache.kafka.server.telemetry.ClientTelemetryExporter;
import org.apache.kafka.server.telemetry.ClientTelemetryPayload;
import org.apache.kafka.shaded.com.google.protobuf.InvalidProtocolBufferException;
import org.apache.kafka.shaded.io.opentelemetry.proto.common.v1.KeyValue;
import org.apache.kafka.shaded.io.opentelemetry.proto.metrics.v1.Gauge;
import org.apache.kafka.shaded.io.opentelemetry.proto.metrics.v1.Metric;
import org.apache.kafka.shaded.io.opentelemetry.proto.metrics.v1.MetricsData;
import org.apache.kafka.shaded.io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import org.apache.kafka.shaded.io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import org.apache.kafka.shaded.io.opentelemetry.proto.metrics.v1.ScopeMetrics;
import org.apache.kafka.shaded.io.opentelemetry.proto.metrics.v1.Sum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import static io.strimzi.kafka.metrics.prometheus.ServerMetricsReporterConfig.CLIENT_ADDRESS_LABEL;
import static io.strimzi.kafka.metrics.prometheus.ServerMetricsReporterConfig.CLIENT_ID_LABEL;
import static io.strimzi.kafka.metrics.prometheus.ServerMetricsReporterConfig.LISTENER_NAME_LABEL;
import static io.strimzi.kafka.metrics.prometheus.ServerMetricsReporterConfig.PRINCIPAL_LABEL;
import static io.strimzi.kafka.metrics.prometheus.ServerMetricsReporterConfig.SECURITY_PROTOCOL_LABEL;

/**
 * Collector for client telemetry metrics received via KIP-714.
 * Parses OTLP protobuf payloads from clients and exposes them as Prometheus metrics.
 */
@SuppressWarnings("ClassFanOutComplexity")
public class ClientTelemetryCollector implements MetricsCollector, ClientTelemetryExporter {

    private static final Logger LOG = LoggerFactory.getLogger(ClientTelemetryCollector.class);
    private static final String CLIENTS_PREFIX = "clients_";
    private static final String CLIENT_INSTANCE_ID_LABEL = "client_instance_id";

    private final LongSupplier clock;
    private final Map<String, ClientData> clients = new ConcurrentHashMap<>();
    private volatile List<String> telemetryLabels;

    /**
     * Constructor
     * @param prometheusCollector the PrometheusCollector to register with
     * @param telemetryLabels the initial list of telemetry labels to include
     */
    public ClientTelemetryCollector(PrometheusCollector prometheusCollector, List<String> telemetryLabels) {
        this(prometheusCollector, telemetryLabels, System::currentTimeMillis);
    }

    // for testing
    ClientTelemetryCollector(PrometheusCollector prometheusCollector, List<String> telemetryLabels, LongSupplier clock) {
        this.clock = clock;
        this.telemetryLabels = telemetryLabels;
        prometheusCollector.addCollector(this);
    }

    /**
     * Update the telemetry labels configuration
     * @param telemetryLabels the new list of telemetry labels
     */
    public void updateTelemetryLabels(List<String> telemetryLabels) {
        this.telemetryLabels = telemetryLabels;
    }

    /**
     * Receive and process client telemetry metrics. Called from the Kafka request handling thread.
     * @param context the client telemetry context
     * @param payload the client telemetry payload
     */
    @Override
    public void exportMetrics(ClientTelemetryContext context, ClientTelemetryPayload payload) {
        String clientInstanceId = payload.clientInstanceId().toString();
        try {
            MetricsData metricsData = MetricsData.parseFrom(payload.data());
            Map<MetricKey, Double> gauges = new HashMap<>();
            Map<MetricKey, Double> counters = new HashMap<>();
            ClientData existingData = clients.get(clientInstanceId);
            Map<MetricKey, Double> deltaAccumulators = existingData != null
                    ? existingData.deltaAccumulators
                    : new HashMap<>();

            for (ResourceMetrics resourceMetrics : metricsData.getResourceMetricsList()) {
                for (ScopeMetrics scopeMetrics : resourceMetrics.getScopeMetricsList()) {
                    for (Metric metric : scopeMetrics.getMetricsList()) {
                        String prometheusName = prometheusName(metric.getName());
                        processMetric(metric, prometheusName, clientInstanceId, context, gauges, counters, deltaAccumulators);
                    }
                }
            }

            clients.put(clientInstanceId, new ClientData(
                    gauges,
                    counters,
                    deltaAccumulators,
                    clock.getAsLong(),
                    context.pushIntervalMs()));

        } catch (InvalidProtocolBufferException e) {
            LOG.error("Unable to parse metrics data from client {}", clientInstanceId, e);
        }
    }

    private void processMetric(Metric metric,
                               String prometheusName,
                               String clientInstanceId,
                               ClientTelemetryContext context,
                               Map<MetricKey, Double> gaugeValues,
                               Map<MetricKey, Double> counterValues,
                               Map<MetricKey, Double> deltaAccumulators) {
        switch (metric.getDataCase()) {
            case GAUGE:
                Gauge gauge = metric.getGauge();
                for (NumberDataPoint dp : gauge.getDataPointsList()) {
                    Labels labels = buildLabels(clientInstanceId, context.authorizableRequestContext(), dp.getAttributesList());
                    MetricKey key = new MetricKey(prometheusName, labels);
                    gaugeValues.put(key, dataPointValue(dp));
                }
                break;
            case SUM:
                Sum sum = metric.getSum();
                Map<MetricKey, Double> target = sum.getIsMonotonic() ? counterValues : gaugeValues;
                for (NumberDataPoint dp : sum.getDataPointsList()) {
                    Labels labels = buildLabels(clientInstanceId, context.authorizableRequestContext(), dp.getAttributesList());
                    MetricKey key = new MetricKey(prometheusName, labels);
                    double value = dataPointValue(dp);
                    switch (sum.getAggregationTemporality()) {
                        case AGGREGATION_TEMPORALITY_DELTA:
                            double accumulated = deltaAccumulators.getOrDefault(key, 0.0) + value;
                            deltaAccumulators.put(key, accumulated);
                            target.put(key, accumulated);
                            break;
                        case AGGREGATION_TEMPORALITY_CUMULATIVE:
                            target.put(key, value);
                            break;
                        default:
                            LOG.warn("Unexpected aggregation temporality {} for metric {}",
                                    sum.getAggregationTemporality(), prometheusName);
                            target.put(key, value);
                            break;
                    }
                }
                break;
            default:
                LOG.debug("Unsupported metric type {} for metric {}", metric.getDataCase(), prometheusName);
                break;
        }
    }

    private static double dataPointValue(NumberDataPoint dp) {
        if (dp.hasAsDouble()) {
            return dp.getAsDouble();
        }
        return dp.getAsInt();
    }

    Labels buildLabels(String clientInstanceId, AuthorizableRequestContext context, List<KeyValue> attributes) {
        Labels.Builder builder = Labels.builder();
        builder.label(CLIENT_INSTANCE_ID_LABEL, clientInstanceId);
        List<String> currentLabels = telemetryLabels;
        if (currentLabels.contains(CLIENT_ID_LABEL)) {
            builder.label(CLIENT_ID_LABEL, context.clientId());
        }
        if (currentLabels.contains(LISTENER_NAME_LABEL)) {
            builder.label(LISTENER_NAME_LABEL, context.listenerName());
        }
        if (currentLabels.contains(SECURITY_PROTOCOL_LABEL)) {
            builder.label(SECURITY_PROTOCOL_LABEL, context.securityProtocol().toString());
        }
        if (currentLabels.contains(PRINCIPAL_LABEL)) {
            builder.label(PRINCIPAL_LABEL, context.principal().toString());
        }
        if (currentLabels.contains(CLIENT_ADDRESS_LABEL)) {
            builder.label(CLIENT_ADDRESS_LABEL, context.clientAddress().getHostAddress());
        }
        for (KeyValue kv : attributes) {
            String labelName = PrometheusNaming.sanitizeLabelName(kv.getKey());
            builder.label(labelName, kv.getValue().getStringValue());
        }
        return builder.build();
    }

    static String prometheusName(String otlpName) {
        return PrometheusNaming.prometheusName(
                PrometheusNaming.sanitizeMetricName(CLIENTS_PREFIX + otlpName.toLowerCase(Locale.ROOT)));
    }

    @Override
    public List<MetricSnapshot> collect() {
        long now = clock.getAsLong();
        Map<String, GaugeSnapshot.Builder> gaugeBuilders = new HashMap<>();
        Map<String, CounterSnapshot.Builder> counterBuilders = new HashMap<>();
        Iterator<Map.Entry<String, ClientData>> it = clients.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ClientData> entry = it.next();
            ClientData clientData = entry.getValue();
            if (now - clientData.lastPushTime > clientData.pushIntervalMs) {
                it.remove();
                continue;
            }
            for (Map.Entry<MetricKey, Double> gauge : clientData.gauges.entrySet()) {
                MetricKey key = gauge.getKey();
                GaugeSnapshot.Builder builder = gaugeBuilders.computeIfAbsent(key.prometheusName,
                        k -> GaugeSnapshot.builder().name(k));
                builder.dataPoint(DataPointSnapshotBuilder.gaugeDataPoint(key.labels, gauge.getValue()));
            }
            for (Map.Entry<MetricKey, Double> counter : clientData.counters.entrySet()) {
                MetricKey key = counter.getKey();
                CounterSnapshot.Builder builder = counterBuilders.computeIfAbsent(key.prometheusName,
                        k -> CounterSnapshot.builder().name(k));
                builder.dataPoint(DataPointSnapshotBuilder.counterDataPoint(key.labels, counter.getValue()));
            }
        }
        List<MetricSnapshot> snapshots = new ArrayList<>();
        for (GaugeSnapshot.Builder builder : gaugeBuilders.values()) {
            snapshots.add(builder.build());
        }
        for (CounterSnapshot.Builder builder : counterBuilders.values()) {
            snapshots.add(builder.build());
        }
        return snapshots;
    }

    // visible for testing
    int clientCount() {
        return clients.size();
    }

    record MetricKey(
            String prometheusName,
            Labels labels) {
    }

    record ClientData(
            Map<MetricKey, Double> gauges,
            Map<MetricKey, Double> counters,
            Map<MetricKey, Double> deltaAccumulators,
            long lastPushTime,
            int pushIntervalMs) {
    }
}
