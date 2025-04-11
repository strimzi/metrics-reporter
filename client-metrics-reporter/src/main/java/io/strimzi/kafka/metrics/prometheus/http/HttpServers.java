/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.prometheus.http;

import io.prometheus.metrics.exporter.httpserver.HTTPServer;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Class to keep track of all the HTTP servers started by all the Kafka components in a JVM.
 */
public class HttpServers {

    private final static Logger LOG = LoggerFactory.getLogger(HttpServers.class);
    private static final Map<Listener, ServerCounter> SERVERS = new HashMap<>();

    private HttpServers() {
        // utility class that shouldn't be instantiated
    }

    /**
     * Get or create a new HTTP server if there isn't an existing instance for the specified listener.
     * @param listener The host and port
     * @param registry The Prometheus registry to expose
     * @return A ServerCounter instance
     */
    public synchronized static ServerCounter getOrCreate(Listener listener, PrometheusRegistry registry) {
        ServerCounter serverCounter = SERVERS.get(listener);
        if (serverCounter == null) {
            serverCounter = new ServerCounter(listener, registry);
            serverCounter.start();
            SERVERS.put(listener, serverCounter);
        }
        serverCounter.count.incrementAndGet();
        return serverCounter;
    }

    /**
     * Release an HTTP server instance. If no other components hold this instance, it is closed.
     * @param serverCounter The HTTP server instance to release
     */
    public synchronized static void release(ServerCounter serverCounter) {
        if (serverCounter.close()) {
            SERVERS.remove(serverCounter.listener);
        }
    }

    /**
     * Class used to keep track of the HTTP server started on a listener.
     */
    public static class ServerCounter {
        private final HTTPServer.Builder builder;
        private final Listener listener;
        private final AtomicInteger count;
        private HTTPServer server;

        private ServerCounter(Listener listener, PrometheusRegistry registry) {
            this.builder = HTTPServer.builder()
                .port(listener.port)
                .registry(registry);
            if (!listener.host.isEmpty()) {
                builder.hostname(listener.host);
            }
            this.listener = listener;
            this.count = new AtomicInteger();
        }

        /**
         * Start the HTTP server.
         */
        private void start() {
            try {
                this.server = builder.buildAndStart();
                LOG.debug("Started HTTP server on http://{}:{}", listener.host, server.getPort());
            } catch (IOException e) {
                LOG.error("Failed starting HTTP server", e);
                throw new RuntimeException(e);
            }
        }

        /**
         * The port this HTTP server instance is listening on. If the listener port is 0, this returns the actual port
         * that is used.
         * @return The port number
         */
        public int port() {
            return server.getPort();
        }

        private synchronized boolean close() {
            int remaining = count.decrementAndGet();
            if (remaining == 0) {
                server.close();
                LOG.debug("Stopped HTTP server on http://{}:{}", listener.host, server.getPort());
                return true;
            }
            return false;
        }
    }
}
