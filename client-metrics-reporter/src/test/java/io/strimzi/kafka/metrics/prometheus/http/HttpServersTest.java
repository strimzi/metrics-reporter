/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.prometheus.http;

import com.sun.net.httpserver.HttpsConfigurator;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.strimzi.kafka.metrics.prometheus.MetricsUtils;
import org.apache.kafka.common.config.ConfigException;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.List;

import static io.strimzi.kafka.metrics.prometheus.http.SslTestUtils.CERTIFICATE;
import static io.strimzi.kafka.metrics.prometheus.http.SslTestUtils.PRIVATE_KEY;
import static io.strimzi.kafka.metrics.prometheus.http.SslTestUtils.trustingClientSslContext;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpServersTest {

    private final PrometheusRegistry registry = new PrometheusRegistry();

    @Test
    public void testLifecycle() {
        Listener listener1 = Listener.parseListener("http://localhost:0");
        HttpServers.ServerCounter server1 = HttpServers.getOrCreate(listener1, registry);
        assertTrue(listenerStarted(listener1.host, server1.port()));

        Listener listener2 = Listener.parseListener("http://localhost:0");
        HttpServers.ServerCounter server2 = HttpServers.getOrCreate(listener2, registry);
        assertTrue(listenerStarted(listener2.host, server2.port()));
        assertSame(server1, server2);

        Listener listener3 = Listener.parseListener("http://127.0.0.1:0");
        HttpServers.ServerCounter server3 = HttpServers.getOrCreate(listener3, registry);
        assertTrue(listenerStarted(listener3.host, server3.port()));

        HttpServers.release(server1);
        assertTrue(listenerStarted(listener1.host, server1.port()));
        assertTrue(listenerStarted(listener2.host, server2.port()));
        assertTrue(listenerStarted(listener3.host, server3.port()));

        HttpServers.release(server2);
        assertFalse(listenerStarted(listener1.host, server1.port()));
        assertFalse(listenerStarted(listener2.host, server2.port()));
        assertTrue(listenerStarted(listener3.host, server3.port()));

        HttpServers.release(server3);
        assertFalse(listenerStarted(listener3.host, server3.port()));
    }

    @Test
    public void testHttpsReusesIdenticalSettings() throws Exception {
        Listener listener = Listener.parseListener("https://localhost:0");
        HttpServers.ServerCounter server = null;
        HttpServers.ServerCounter again = null;
        try {
            server = HttpServers.getOrCreate(listener, registry, httpsConfigurator(List.of("TLSv1.2", "TLSv1.3")));
            again = HttpServers.getOrCreate(listener, registry, httpsConfigurator(List.of("TLSv1.2", "TLSv1.3")));

            assertSame(server, again);
            assertTrue(httpsListenerStarted(server.port()));
        } finally {
            if (again != null) {
                HttpServers.release(again);
            }
            if (server != null) {
                HttpServers.release(server);
            }
        }
    }

    @Test
    public void testHttpsRejectsDifferentSettings() throws Exception {
        Listener listener = Listener.parseListener("https://localhost:0");
        HttpServers.ServerCounter server = HttpServers.getOrCreate(
                listener, registry, httpsConfigurator(List.of("TLSv1.2", "TLSv1.3")));
        try {
            ConfigException exception = assertThrows(
                    ConfigException.class,
                    () -> HttpServers.getOrCreate(listener, registry, httpsConfigurator(List.of("TLSv1.2"))));

            assertTrue(exception.getMessage().contains("different SSL settings"));
            assertTrue(httpsListenerStarted(server.port()));
        } finally {
            HttpServers.release(server);
        }
    }

    private HttpsConfigurator httpsConfigurator(List<String> protocols) throws Exception {
        PKCS8EncodedKeySpec keySpec = PemUtils.readPrivateKey(PRIVATE_KEY);
        byte[] certificate = PemUtils.readCertificates(CERTIFICATE);
        SSLContext sslContext = new SSLContextFactory().create(keySpec, certificate);
        return new HttpsConfiguratorFactory(protocols, List.of()).create(sslContext, certificate, keySpec.getEncoded());
    }

    private boolean httpsListenerStarted(int port) throws Exception {
        return MetricsUtils.metricsEndpointReachable("localhost", port, trustingClientSslContext());
    }

    private boolean listenerStarted(String host, int port) {
        try {
            URL url = new URL("http://" + host + ":" + port + "/metrics");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("HEAD");
            con.connect();
            return con.getResponseCode() == HttpURLConnection.HTTP_OK;
        } catch (IOException ioe) {
            return false;
        }
    }
}
