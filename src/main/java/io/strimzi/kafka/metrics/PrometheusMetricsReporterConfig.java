/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import io.prometheus.client.exporter.HTTPServer;
import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 /**
 * Configuration for the PrometheusMetricsReporter implementation.
 */
public class PrometheusMetricsReporterConfig extends AbstractConfig {

    private static final Logger LOG = LoggerFactory.getLogger(PrometheusMetricsReporterConfig.class.getName());

    /**
     * Configuration prefix for Prometheus Metrics Reporter.
     */
    public static final String CONFIG_PREFIX = "prometheus.metrics.reporter.";

    /**
     * Configuration key for the listener to expose the metrics.
     */
    public static final String LISTENER_CONFIG = CONFIG_PREFIX + "listener";

    /**
     * Default value for the listener configuration.
     */
    public static final String LISTENER_CONFIG_DEFAULT = "http://:8080";
    private static final String LISTENER_CONFIG_DOC = "The HTTP listener to expose the metrics.";

    /**
     * Configuration key for the allowlist of metrics to collect.
     */
    public static final String ALLOWLIST_CONFIG = CONFIG_PREFIX + "allowlist";

    /**
     * Default value for the allowlist configuration.
     */
    public static final String ALLOWLIST_CONFIG_DEFAULT = ".*";
    private static final String ALLOWLIST_CONFIG_DOC = "A comma separated list of regex Patterns to specify the metrics to collect.";

    private static final ConfigDef CONFIG_DEF = new ConfigDef()
            .define(LISTENER_CONFIG, ConfigDef.Type.STRING, LISTENER_CONFIG_DEFAULT, new ListenerValidator(), ConfigDef.Importance.HIGH, LISTENER_CONFIG_DOC)
            .define(ALLOWLIST_CONFIG, ConfigDef.Type.LIST, ALLOWLIST_CONFIG_DEFAULT, ConfigDef.Importance.HIGH, ALLOWLIST_CONFIG_DOC);

    private final Listener listener;
    private final Pattern allowlist;

    /**
     * Constructor.
     *
     * @param props the configuration properties.
     */
    public PrometheusMetricsReporterConfig(Map<?, ?> props) {
        super(CONFIG_DEF, props);
        this.listener = Listener.parseListener(getString(LISTENER_CONFIG));
        this.allowlist = compileAllowlist(getList(ALLOWLIST_CONFIG));
    }

    /**
     * Check if a metric is allowed.
     *
     * @param name the name of the metric.
     * @return true if the metric is allowed, false otherwise.
     */
    public boolean isAllowed(String name) {
        return allowlist.matcher(name).matches();
    }

    private Pattern compileAllowlist(List<String> allowlist) {
        String joined = String.join("|", allowlist);
        return Pattern.compile(joined);
    }

    /**
     * Gets the listener.
     *
     * @return the listener.
     */
    public String listener() {
        return listener.toString();
    }

    @Override
     public String toString() {
        return "PrometheusMetricsReporterConfig{" +
                "allowlist=" + allowlist +
                ", listener=" + listener +
                '}';
    }

    /**
     * Start the HTTP server for exposing metrics.
     *
     * @return An optional HTTPServer instance if started successfully, otherwise empty.
     */
    public synchronized Optional<HTTPServer> startHttpServer() {
        try {
            InetSocketAddress address = listener.getInetSocketAddress();
            HTTPServer httpServer = new HTTPServer(address.getPort(), true);
            LOG.info("HTTP server started on listener http://{}:{}", listener.host, httpServer.getPort());
            return Optional.of(httpServer);
        } catch (BindException be) {
            LOG.info("HTTP server already started");
            return Optional.empty();
        } catch (IOException ioe) {
            LOG.error("Failed starting HTTP server", ioe);
            throw new RuntimeException(ioe);
        }
    }

    static class ListenerValidator implements ConfigDef.Validator {

        @Override
         public void ensureValid(String name, Object value) {
            Matcher matcher = Listener.PATTERN.matcher(String.valueOf(value));
            if (!matcher.matches()) {
                throw new ConfigException(name, value, "Listener must be of format http://[host]:[port]");
            }
        }
    }

    static class Listener {

        private static final Pattern PATTERN = Pattern.compile("http://\\[?([0-9a-zA-Z\\-%._:]*)]?:([0-9]+)");

        final String host;
        final int port;

        Listener(String host, int port) {
            this.host = host;
            this.port = port;
        }

        static Listener parseListener(String listener) {
            Matcher matcher = PATTERN.matcher(listener);
            if (matcher.matches()) {
                String host = matcher.group(1);
                int port = Integer.parseInt(matcher.group(2));
                return new Listener(host, port);
            } else {
                throw new ConfigException(LISTENER_CONFIG, listener, "Listener be of format http://[host]:[port]");
            }
        }

        public InetSocketAddress getInetSocketAddress() {
            return host == null || host.isEmpty() ? new InetSocketAddress(port) : InetSocketAddress.createUnresolved(host, port);
        }

        @Override
         public String toString() {
            return "http://" + host + ":" + port;
        }

        @Override
         public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Listener listener = (Listener) o;
            return port == listener.port && Objects.equals(host, listener.host);
        }

        @Override
         public int hashCode() {
            return Objects.hash(host, port);
        }
    }
}
