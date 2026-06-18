/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.prometheus.http;

import io.strimzi.kafka.metrics.prometheus.ClientMetricsReporterConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigException;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.strimzi.kafka.metrics.prometheus.ClientMetricsReporterConfig.LISTENER_CONFIG;

/**
 * Class parsing and handling the listener specified via {@link ClientMetricsReporterConfig#LISTENER_CONFIG} for
 * the HTTP server used to expose the metrics.
 */
public class Listener {

    private static final Pattern PATTERN = Pattern.compile("(https?)://\\[?([0-9a-zA-Z\\-%._:]*)]?:([0-9]+)");

    /**
     * The host of the listener
     */
    public final String host;
    /**
     * The port of the listener
     */
    public final int port;
    /**
     * The scheme of the listener. Default is "http"
     */
    public final String scheme;

    /* test */ Listener(String host, int port) {
        this("http", host, port);
    }

    /* test */ Listener(String scheme, String host, int port) {
        this.scheme = scheme;
        this.host = host;
        this.port = port;
    }

    /**
     * Build a Listener instance from an "http(s)://[host]:[port]" string
     * @param listener the input string
     * @return the listener
     */
    public static Listener parseListener(String listener) {
        Matcher matcher = PATTERN.matcher(listener);
        if (matcher.matches()) {
            String scheme = matcher.group(1);
            String host = matcher.group(2);
            int port = Integer.parseInt(matcher.group(3));
            return new Listener(scheme, host, port);
        } else {
            throw new ConfigException(LISTENER_CONFIG, listener, "Listener must be of format http(s)://[host]:[port]");
        }
    }

    /**
     * Checks whether the listener uses HTTPS.
     *
     * @return true when the listener scheme is HTTPS.
     */
    public boolean isHttps() {
        return "https".equals(scheme);
    }

    @Override
    public String toString() {
        return scheme + "://" + host + ":" + port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Listener listener = (Listener) o;
        return port == listener.port
                && Objects.equals(scheme, listener.scheme)
                && Objects.equals(host, listener.host);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scheme, host, port);
    }

    /**
     * Validator to check the user provided listener configuration
     */
    public static class ListenerValidator implements ConfigDef.Validator {

        /**
         * Constructor.
         */
        public ListenerValidator() { }

        @Override
        public void ensureValid(String name, Object value) {
            Matcher matcher = PATTERN.matcher(String.valueOf(value));
            if (!matcher.matches()) {
                throw new ConfigException(name, value, "The Listener must be of format http(s)://[host]:[port]");
            }
        }
    }
}
