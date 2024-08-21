/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics;

import io.prometheus.metrics.model.registry.PrometheusRegistry;
import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
* Configuration for the PrometheusMetricsReporter implementation.
*/
public class PrometheusMetricsReporterConfig extends AbstractConfig {

    private static final Logger LOG = LoggerFactory.getLogger(PrometheusMetricsReporterConfig.class);
    private static final String CONFIG_PREFIX = "prometheus.metrics.reporter.";

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
     * Configuration key to determine if the listener is enabled or not.
     */
    public static final String LISTENER_ENABLE_CONFIG = CONFIG_PREFIX + "listener.enable";

    /**
     * Default value for the listener enabled configuration.
     */
    public static final Boolean LISTENER_ENABLE_CONFIG_DEFAULT = true;
    private static final String LISTENER_ENABLE_CONFIG_DOC = "Enable the listener to expose the metrics.";

    /**
     * Configuration key for the allowlist of metrics to collect.
     */
    public static final String ALLOWLIST_CONFIG = CONFIG_PREFIX + "allowlist";

    /**
     * Default value for the allowlist configuration.
     */
    public static final String ALLOWLIST_CONFIG_DEFAULT = ".*";
    private static final String ALLOWLIST_CONFIG_DOC = "A comma separated list of regex patterns to specify the metrics to collect.";

    private static final ConfigDef CONFIG_DEF = new ConfigDef()
            .define(LISTENER_CONFIG, ConfigDef.Type.STRING, LISTENER_CONFIG_DEFAULT, new Listener.ListenerValidator(), ConfigDef.Importance.HIGH, LISTENER_CONFIG_DOC)
            .define(ALLOWLIST_CONFIG, ConfigDef.Type.LIST, ALLOWLIST_CONFIG_DEFAULT, ConfigDef.Importance.HIGH, ALLOWLIST_CONFIG_DOC)
            .define(LISTENER_ENABLE_CONFIG, ConfigDef.Type.BOOLEAN, LISTENER_ENABLE_CONFIG_DEFAULT, ConfigDef.Importance.HIGH, LISTENER_ENABLE_CONFIG_DOC);

    private final Listener listener;
    private final boolean listenerEnabled;
    private final Pattern allowlist;
    private final PrometheusRegistry registry;

    /**
     * Constructor.
     *
     * @param props the configuration properties.
     * @param registry the metrics registry
     */
    public PrometheusMetricsReporterConfig(Map<?, ?> props, PrometheusRegistry registry) {
        super(CONFIG_DEF, props);
        this.listener = Listener.parseListener(getString(LISTENER_CONFIG));
        this.allowlist = compileAllowlist(getList(ALLOWLIST_CONFIG));
        this.listenerEnabled = getBoolean(LISTENER_ENABLE_CONFIG);
        this.registry = registry;
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
        for (String entry : allowlist) {
            try {
                Pattern.compile(entry);
            } catch (PatternSyntaxException pse) {
                throw new ConfigException("Invalid regex pattern found in " + ALLOWLIST_CONFIG + ": " + entry);
            }
        }
        String joined = allowlist.stream().map(v -> "(" + v + ")").collect(Collectors.joining("|"));
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

    /**
     * Check whether the listener is enabled.
     *
     * @return {@code true} if the listener is enabled, {@code false} otherwise.
     */
    public boolean isListenerEnabled() {
        return listenerEnabled;
    }

    @Override
    public String toString() {
        return "PrometheusMetricsReporterConfig{" +
                ", listener=" + listener +
                ", listenerEnabled=" + listenerEnabled +
                ", allowlist=" + allowlist +
                '}';
    }

    /**
     * Start the HTTP server for exposing metrics.
     *
     * @return An optional ServerCounter instance if {@link #LISTENER_ENABLE_CONFIG} is enabled, otherwise empty.
     */
    public synchronized Optional<HttpServers.ServerCounter> startHttpServer() {
        if (!listenerEnabled) {
            LOG.info("HTTP server listener not enabled");
            return Optional.empty();
        }
        try {
            HttpServers.ServerCounter server = HttpServers.getOrCreate(listener, registry);
            LOG.info("HTTP server started on listener http://{}:{}", listener.host, server.port());
            return Optional.of(server);
        } catch (IOException ioe) {
            LOG.error("Failed starting HTTP server", ioe);
            throw new RuntimeException(ioe);
        }
    }
}
