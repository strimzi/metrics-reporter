/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.http;

import io.prometheus.metrics.model.registry.PrometheusRegistry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpServersTest {

    private final PrometheusRegistry registry = new PrometheusRegistry();

    @Test
    public void testLifecycle() throws IOException {
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
