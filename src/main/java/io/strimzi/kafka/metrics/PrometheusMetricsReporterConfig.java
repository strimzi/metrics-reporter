/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import io.prometheus.client.exporter.HTTPServer;
import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.BindException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Configuration for the reporter implementations.
 */
public class PrometheusMetricsReporterConfig extends AbstractConfig {

    private static final Logger LOG = LoggerFactory.getLogger(PrometheusMetricsReporterConfig.class.getName());

    public static final String CONFIG_PREFIX = "prometheus.metrics.reporter.";
    public static final String LISTENER_CONFIG = CONFIG_PREFIX + "listener";
    public static final String LISTENER_CONFIG_DEFAULT = "http://:8080";
    public static final String LISTENER_CONFIG_DOC = "The HTTP listener to expose the metrics.";

    public static final String ALLOWLIST_CONFIG = CONFIG_PREFIX + "allowlist";
    public static final String ALLOWLIST_CONFIG_DEFAULT = ".*";
    public static final String ALLOWLIST_CONFIG_DOC = "A comma separated list of regex Patterns to specify the metrics to collect.";

    private static final ConfigDef CONFIG_DEF = new ConfigDef()
            .define(LISTENER_CONFIG, ConfigDef.Type.STRING, LISTENER_CONFIG_DEFAULT, ConfigDef.Importance.HIGH, LISTENER_CONFIG_DOC)
            .define(ALLOWLIST_CONFIG, ConfigDef.Type.LIST, ALLOWLIST_CONFIG_DEFAULT, ConfigDef.Importance.HIGH, ALLOWLIST_CONFIG_DOC);

    private final String listener;
    private final Pattern allowlist;

    public PrometheusMetricsReporterConfig(Map<?, ?> props) {
        super(CONFIG_DEF, props);
        this.listener = getString(LISTENER_CONFIG);
        this.allowlist = compileAllowlist(getList(ALLOWLIST_CONFIG));
    }

    public String listener() {
        return listener;
    }

    public boolean isAllowed(String name) {
        return allowlist.matcher(name).matches();
    }

    private Pattern compileAllowlist(List<String> allowlist) {
        String joined = String.join("|", allowlist);
        return Pattern.compile(joined);
    }

    @Override
    public String toString() {
        return "PrometheusMetricsReporterConfig{" +
                "allowlist=" + allowlist +
                ", listener=" + listener +
                '}';
    }

    public static class HostPort {
        private final String host;
        private final int port;

        public HostPort(String host, int port) {
            this.host = host;
            this.port = port;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        public static HostPort parseListener(String listener) throws IOException {
            if (listener.equals("http://"))
                throw new IOException("Invalid listener: Must contain a port number");
            if (listener.startsWith("http://")) {
                char targetLastColon = ':';
                int lastIndex = listener.lastIndexOf(targetLastColon);
                String host = listener.substring("http://".length(), lastIndex);
                System.out.println("Host: " + host);
                if (host.isEmpty()) {
                    host = "localhost";
                }
                String portString = listener.substring(lastIndex + 1);
                if (!portString.matches("\\d+")) {
                    throw new IOException("Invalid port: Port must be a number");
                }
                int port = Integer.parseInt(portString);
                return new HostPort(host, port);
            } else {
                throw new IOException("Invalid listener: Must start with 'http://'");
            }
        }
    }

    public synchronized Optional<HTTPServer> startHttpServer() {
        try {
            HostPort parsedListener = HostPort.parseListener(listener);
            String host = parsedListener.getHost();
            int port = parsedListener.getPort();
            HTTPServer httpServer = new HTTPServer(host, port, true);
            LOG.info("HTTP server started on listener " + host + ":" + port);
            return Optional.of(httpServer);
        } catch (BindException be) {
            LOG.info("HTTP server already started");
            return Optional.empty();
        } catch (IOException ioe) {
            LOG.error("Failed starting HTTP server", ioe);
            throw new RuntimeException(ioe);
        }
    }
}
