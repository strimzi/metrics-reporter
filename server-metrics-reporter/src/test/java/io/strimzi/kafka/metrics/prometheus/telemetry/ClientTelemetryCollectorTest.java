/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.prometheus.telemetry;

import io.prometheus.metrics.model.snapshots.CounterSnapshot;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;
import io.strimzi.kafka.metrics.prometheus.common.PrometheusCollector;
import org.apache.kafka.common.Uuid;
import org.apache.kafka.common.security.auth.KafkaPrincipal;
import org.apache.kafka.common.security.auth.SecurityProtocol;
import org.apache.kafka.server.authorizer.AuthorizableRequestContext;
import org.apache.kafka.server.telemetry.ClientTelemetryContext;
import org.apache.kafka.server.telemetry.ClientTelemetryPayload;
import org.apache.kafka.shaded.io.opentelemetry.proto.common.v1.AnyValue;
import org.apache.kafka.shaded.io.opentelemetry.proto.common.v1.InstrumentationScope;
import org.apache.kafka.shaded.io.opentelemetry.proto.common.v1.KeyValue;
import org.apache.kafka.shaded.io.opentelemetry.proto.metrics.v1.AggregationTemporality;
import org.apache.kafka.shaded.io.opentelemetry.proto.metrics.v1.Gauge;
import org.apache.kafka.shaded.io.opentelemetry.proto.metrics.v1.Metric;
import org.apache.kafka.shaded.io.opentelemetry.proto.metrics.v1.MetricsData;
import org.apache.kafka.shaded.io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import org.apache.kafka.shaded.io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import org.apache.kafka.shaded.io.opentelemetry.proto.metrics.v1.ScopeMetrics;
import org.apache.kafka.shaded.io.opentelemetry.proto.metrics.v1.Sum;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static io.strimzi.kafka.metrics.prometheus.ServerMetricsReporterConfig.CLIENT_ADDRESS_LABEL;
import static io.strimzi.kafka.metrics.prometheus.ServerMetricsReporterConfig.CLIENT_ID_LABEL;
import static io.strimzi.kafka.metrics.prometheus.ServerMetricsReporterConfig.LISTENER_NAME_LABEL;
import static io.strimzi.kafka.metrics.prometheus.ServerMetricsReporterConfig.PRINCIPAL_LABEL;
import static io.strimzi.kafka.metrics.prometheus.ServerMetricsReporterConfig.SECURITY_PROTOCOL_LABEL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClientTelemetryCollectorTest {

    private static final Uuid CLIENT_INSTANCE_ID = Uuid.randomUuid();

    @Test
    public void testPrometheusName() {
        assertEquals("clients_org_apache_kafka_producer_topic_byte_rate",
                ClientTelemetryCollector.prometheusName("org.apache.kafka.producer.topic.byte.rate"));
    }

    @Test
    public void testBuildLabelsDefault() throws Exception {
        ClientTelemetryCollector collector = new ClientTelemetryCollector(new PrometheusCollector(), List.of(CLIENT_ID_LABEL));
        TestContext context = newTestContext();
        Labels labels = collector.buildLabels(CLIENT_INSTANCE_ID.toString(), context, List.of());
        assertEquals(CLIENT_INSTANCE_ID.toString(), labels.get("client_instance_id"));
        assertEquals("my-client", labels.get("client_id"));
        assertEquals(2, labels.size());
    }

    @Test
    public void testBuildLabelsAll() throws Exception {
        ClientTelemetryCollector collector = new ClientTelemetryCollector(new PrometheusCollector(),
                List.of(CLIENT_ID_LABEL, LISTENER_NAME_LABEL, SECURITY_PROTOCOL_LABEL, PRINCIPAL_LABEL, CLIENT_ADDRESS_LABEL));
        TestContext context = newTestContext();
        Labels labels = collector.buildLabels(CLIENT_INSTANCE_ID.toString(), context, List.of());
        assertEquals(CLIENT_INSTANCE_ID.toString(), labels.get("client_instance_id"));
        assertEquals("my-client", labels.get("client_id"));
        assertEquals("PLAINTEXT", labels.get("listener_name"));
        assertEquals("PLAINTEXT", labels.get("security_protocol"));
        assertEquals(KafkaPrincipal.ANONYMOUS.toString(), labels.get("principal"));
        assertEquals(InetAddress.getLocalHost().getHostAddress(), labels.get("client_address"));
        assertEquals(6, labels.size());
    }

    @Test
    public void testBuildLabelsNone() throws Exception {
        ClientTelemetryCollector collector = new ClientTelemetryCollector(new PrometheusCollector(), List.of());
        TestContext context = newTestContext();
        Labels labels = collector.buildLabels(CLIENT_INSTANCE_ID.toString(), context, List.of());
        assertEquals(CLIENT_INSTANCE_ID.toString(), labels.get("client_instance_id"));
        assertEquals(1, labels.size());
    }

    @Test
    public void testBuildLabelsWithAttributes() throws Exception {
        ClientTelemetryCollector collector = new ClientTelemetryCollector(new PrometheusCollector(), List.of());
        TestContext context = newTestContext();
        List<KeyValue> attributes = List.of(KeyValue.newBuilder()
                .setKey("my_key")
                .setValue(AnyValue.newBuilder().setStringValue("my-value"))
                .build());
        Labels labels = collector.buildLabels(CLIENT_INSTANCE_ID.toString(), context, attributes);
        assertEquals("my-value", labels.get("my_key"));
        assertEquals(2, labels.size());
    }

    @Test
    public void testExportAndCollectGaugeMetric() throws Exception {
        PrometheusCollector prometheusCollector = new PrometheusCollector();
        ClientTelemetryCollector collector = new ClientTelemetryCollector(prometheusCollector, List.of(CLIENT_ID_LABEL));

        MetricsData metricsData = MetricsData.newBuilder()
                .addResourceMetrics(ResourceMetrics.newBuilder()
                        .addScopeMetrics(ScopeMetrics.newBuilder()
                                .setScope(InstrumentationScope.newBuilder())
                                .addMetrics(Metric.newBuilder()
                                        .setName("org.apache.kafka.producer.record.queue.time.avg")
                                        .setGauge(Gauge.newBuilder()
                                                .addDataPoints(NumberDataPoint.newBuilder()
                                                        .setAsDouble(42.5))))))
                .build();

        TestContext context = newTestContext();
        collector.exportMetrics(
                new TestTelemetryContext(context, 30000),
                new TestPayload(CLIENT_INSTANCE_ID, false, metricsData));

        List<MetricSnapshot> snapshots = collector.collect();
        assertEquals(1, snapshots.size());
        GaugeSnapshot gauge = (GaugeSnapshot) snapshots.get(0);
        assertEquals("clients_org_apache_kafka_producer_record_queue_time_avg", gauge.getMetadata().getName());
        assertEquals(1, gauge.getDataPoints().size());
        assertEquals(42.5, gauge.getDataPoints().get(0).getValue());
        assertEquals(CLIENT_INSTANCE_ID.toString(), gauge.getDataPoints().get(0).getLabels().get("client_instance_id"));
    }

    @Test
    public void testExportAndCollectSumCumulative() throws Exception {
        PrometheusCollector prometheusCollector = new PrometheusCollector();
        ClientTelemetryCollector collector = new ClientTelemetryCollector(prometheusCollector, List.of());

        MetricsData metricsData = buildSumMetricsData("org.apache.kafka.producer.byte.total",
                AggregationTemporality.AGGREGATION_TEMPORALITY_CUMULATIVE, 100.0);

        TestContext context = newTestContext();
        collector.exportMetrics(
                new TestTelemetryContext(context, 30000),
                new TestPayload(CLIENT_INSTANCE_ID, false, metricsData));

        List<MetricSnapshot> snapshots = collector.collect();
        assertEquals(1, snapshots.size());
        CounterSnapshot counter = (CounterSnapshot) snapshots.get(0);
        assertEquals(100.0, counter.getDataPoints().get(0).getValue());

        // Second push with cumulative sum should replace the value
        metricsData = buildSumMetricsData("org.apache.kafka.producer.byte.total",
                AggregationTemporality.AGGREGATION_TEMPORALITY_CUMULATIVE, 200.0);
        collector.exportMetrics(
                new TestTelemetryContext(context, 30000),
                new TestPayload(CLIENT_INSTANCE_ID, false, metricsData));

        snapshots = collector.collect();
        assertEquals(1, snapshots.size());
        counter = (CounterSnapshot) snapshots.get(0);
        assertEquals(200.0, counter.getDataPoints().get(0).getValue());
    }

    @Test
    public void testExportAndCollectSumDelta() throws Exception {
        PrometheusCollector prometheusCollector = new PrometheusCollector();
        ClientTelemetryCollector collector = new ClientTelemetryCollector(prometheusCollector, List.of());

        TestContext context = newTestContext();

        // First push: delta of 100
        MetricsData metricsData = buildSumMetricsData("org.apache.kafka.producer.byte.total",
                AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA, 100.0);
        collector.exportMetrics(
                new TestTelemetryContext(context, 30000),
                new TestPayload(CLIENT_INSTANCE_ID, false, metricsData));

        List<MetricSnapshot> snapshots = collector.collect();
        CounterSnapshot counter = (CounterSnapshot) snapshots.get(0);
        assertEquals(100.0, counter.getDataPoints().get(0).getValue());

        // Second push: delta of 50, accumulated should be 150
        metricsData = buildSumMetricsData("org.apache.kafka.producer.byte.total",
                AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA, 50.0);
        collector.exportMetrics(
                new TestTelemetryContext(context, 30000),
                new TestPayload(CLIENT_INSTANCE_ID, false, metricsData));

        snapshots = collector.collect();
        counter = (CounterSnapshot) snapshots.get(0);
        assertEquals(150.0, counter.getDataPoints().get(0).getValue());
    }

    @Test
    public void testStaleClientRemoved() throws Exception {
        AtomicLong clock = new AtomicLong(10000);
        ClientTelemetryCollector collector = new ClientTelemetryCollector(
                new PrometheusCollector(), List.of(), clock::get);

        MetricsData metricsData = MetricsData.newBuilder()
                .addResourceMetrics(ResourceMetrics.newBuilder()
                        .addScopeMetrics(ScopeMetrics.newBuilder()
                                .setScope(InstrumentationScope.newBuilder())
                                .addMetrics(Metric.newBuilder()
                                        .setName("org.apache.kafka.producer.record.send.total")
                                        .setGauge(Gauge.newBuilder()
                                                .addDataPoints(NumberDataPoint.newBuilder()
                                                        .setAsDouble(10.0))))))
                .build();

        TestContext context = newTestContext();
        collector.exportMetrics(
                new TestTelemetryContext(context, 1000),
                new TestPayload(CLIENT_INSTANCE_ID, false, metricsData));

        assertEquals(1, collector.clientCount());

        // Before push interval: metrics still present
        clock.set(10500);
        List<MetricSnapshot> snapshots = collector.collect();
        assertEquals(1, snapshots.size());
        assertEquals(1, collector.clientCount());

        // After push interval: metrics are removed
        clock.set(12000);
        snapshots = collector.collect();
        assertTrue(snapshots.isEmpty());
        assertEquals(0, collector.clientCount());
    }

    @Test
    public void testMultipleClients() throws Exception {
        PrometheusCollector prometheusCollector = new PrometheusCollector();
        ClientTelemetryCollector collector = new ClientTelemetryCollector(prometheusCollector, List.of());

        Uuid client1 = Uuid.randomUuid();
        Uuid client2 = Uuid.randomUuid();
        TestContext context = newTestContext();

        MetricsData metricsData = MetricsData.newBuilder()
                .addResourceMetrics(ResourceMetrics.newBuilder()
                        .addScopeMetrics(ScopeMetrics.newBuilder()
                                .setScope(InstrumentationScope.newBuilder())
                                .addMetrics(Metric.newBuilder()
                                        .setName("org.apache.kafka.producer.record.send.total")
                                        .setGauge(Gauge.newBuilder()
                                                .addDataPoints(NumberDataPoint.newBuilder()
                                                        .setAsDouble(10.0))))))
                .build();

        collector.exportMetrics(
                new TestTelemetryContext(context, 30000),
                new TestPayload(client1, false, metricsData));
        collector.exportMetrics(
                new TestTelemetryContext(context, 30000),
                new TestPayload(client2, false, metricsData));

        assertEquals(2, collector.clientCount());

        List<MetricSnapshot> snapshots = collector.collect();
        assertEquals(1, snapshots.size());
        GaugeSnapshot gauge = (GaugeSnapshot) snapshots.get(0);
        assertEquals(2, gauge.getDataPoints().size());
    }

    @Test
    public void testUpdateTelemetryLabels() throws Exception {
        PrometheusCollector prometheusCollector = new PrometheusCollector();
        ClientTelemetryCollector collector = new ClientTelemetryCollector(prometheusCollector, List.of());

        TestContext context = newTestContext();

        MetricsData metricsData = MetricsData.newBuilder()
                .addResourceMetrics(ResourceMetrics.newBuilder()
                        .addScopeMetrics(ScopeMetrics.newBuilder()
                                .setScope(InstrumentationScope.newBuilder())
                                .addMetrics(Metric.newBuilder()
                                        .setName("org.apache.kafka.producer.request.total")
                                        .setGauge(Gauge.newBuilder()
                                                .addDataPoints(NumberDataPoint.newBuilder()
                                                        .setAsDouble(5.0))))))
                .build();

        collector.exportMetrics(
                new TestTelemetryContext(context, 30000),
                new TestPayload(CLIENT_INSTANCE_ID, false, metricsData));

        List<MetricSnapshot> snapshots = collector.collect();
        GaugeSnapshot gauge = (GaugeSnapshot) snapshots.get(0);
        assertEquals(1, gauge.getDataPoints().get(0).getLabels().size());

        // Reconfigure to include client_id
        collector.updateTelemetryLabels(List.of(CLIENT_ID_LABEL));

        collector.exportMetrics(
                new TestTelemetryContext(context, 30000),
                new TestPayload(CLIENT_INSTANCE_ID, false, metricsData));

        snapshots = collector.collect();
        gauge = (GaugeSnapshot) snapshots.get(0);
        assertEquals(2, gauge.getDataPoints().get(0).getLabels().size());
        assertEquals("my-client", gauge.getDataPoints().get(0).getLabels().get("client_id"));
    }

    private static MetricsData buildSumMetricsData(String metricName, AggregationTemporality temporality, double value) {
        return MetricsData.newBuilder()
                .addResourceMetrics(ResourceMetrics.newBuilder()
                        .addScopeMetrics(ScopeMetrics.newBuilder()
                                .setScope(InstrumentationScope.newBuilder())
                                .addMetrics(Metric.newBuilder()
                                        .setName(metricName)
                                        .setSum(Sum.newBuilder()
                                                .setIsMonotonic(true)
                                                .setAggregationTemporality(temporality)
                                                .addDataPoints(NumberDataPoint.newBuilder()
                                                        .setAsDouble(value))))))
                .build();
    }

    private static TestContext newTestContext() throws Exception {
        return new TestContext(
                "PLAINTEXT",
                SecurityProtocol.PLAINTEXT,
                KafkaPrincipal.ANONYMOUS,
                InetAddress.getLocalHost(),
                72,
                0,
                "my-client",
                0);
    }

    static class TestTelemetryContext implements ClientTelemetryContext {
        private final AuthorizableRequestContext requestContext;
        private final int pushIntervalMs;

        TestTelemetryContext(AuthorizableRequestContext requestContext, int pushIntervalMs) {
            this.requestContext = requestContext;
            this.pushIntervalMs = pushIntervalMs;
        }

        @Override
        public int pushIntervalMs() {
            return pushIntervalMs;
        }

        @Override
        public AuthorizableRequestContext authorizableRequestContext() {
            return requestContext;
        }
    }

    static class TestPayload implements ClientTelemetryPayload {
        private final Uuid clientInstanceId;
        private final boolean isTerminating;
        private final MetricsData metricsData;

        TestPayload(Uuid clientInstanceId, boolean isTerminating, MetricsData metricsData) {
            this.clientInstanceId = clientInstanceId;
            this.isTerminating = isTerminating;
            this.metricsData = metricsData;
        }

        @Override
        public Uuid clientInstanceId() {
            return clientInstanceId;
        }

        @Override
        public boolean isTerminating() {
            return isTerminating;
        }

        @Override
        public String contentType() {
            return "application/x-protobuf";
        }

        @Override
        public ByteBuffer data() {
            return ByteBuffer.wrap(metricsData.toByteArray());
        }
    }

    static class TestContext implements AuthorizableRequestContext {
        private final String listenerName;
        private final SecurityProtocol protocol;
        private final KafkaPrincipal principal;
        private final InetAddress clientAddress;
        private final int requestType;
        private final int requestVersion;
        private final String clientId;
        private final int correlationId;

        TestContext(String listenerName, SecurityProtocol protocol, KafkaPrincipal principal, InetAddress clientAddress,
                    int requestType, int requestVersion, String clientId, int correlationId) {
            this.listenerName = listenerName;
            this.protocol = protocol;
            this.principal = principal;
            this.clientAddress = clientAddress;
            this.requestType = requestType;
            this.requestVersion = requestVersion;
            this.clientId = clientId;
            this.correlationId = correlationId;
        }

        @Override
        public String listenerName() {
            return listenerName;
        }

        @Override
        public SecurityProtocol securityProtocol() {
            return protocol;
        }

        @Override
        public KafkaPrincipal principal() {
            return principal;
        }

        @Override
        public InetAddress clientAddress() {
            return clientAddress;
        }

        @Override
        public int requestType() {
            return requestType;
        }

        @Override
        public int requestVersion() {
            return requestVersion;
        }

        @Override
        public String clientId() {
            return clientId;
        }

        @Override
        public int correlationId() {
            return correlationId;
        }
    }
}
