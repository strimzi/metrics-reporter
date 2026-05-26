/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.prometheus.http;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import org.apache.kafka.common.config.ConfigException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static io.strimzi.kafka.metrics.prometheus.ClientMetricsReporterConfig.LISTENER_SSL_ENABLED_CIPHER_SUITES_CONFIG;
import static io.strimzi.kafka.metrics.prometheus.ClientMetricsReporterConfig.LISTENER_SSL_ENABLED_PROTOCOLS_CONFIG;

/**
 * Factory for building HTTPS listener configuration.
 */
public class HttpsConfiguratorFactory {
    /**
     * The enabled secure transport protocols. Empty means Java's default protocols are used.
     */
    private final List<String> enabledProtocols;
    /**
     * The enabled cipher suites. Empty means Java's default cipher suites are used.
     */
    private final List<String> enabledCipherSuites;

    /**
     * Constructor.
     *
     * @param enabledProtocols    The enabled secure transport protocols.
     * @param enabledCipherSuites The enabled cipher suites. Empty means Java's default cipher suites are used.
     */
    public HttpsConfiguratorFactory(List<String> enabledProtocols, List<String> enabledCipherSuites) {
        this.enabledProtocols = enabledProtocols;
        this.enabledCipherSuites = enabledCipherSuites;
    }

    /**
     * Creates the HTTPS configurator for the supplied SSL context.
     *
     * @param sslContext The SSL context to use for HTTPS.
     * @return The HTTPS configurator.
     */
    public HttpsConfigurator create(SSLContext sslContext) {
        validateConfiguredValues(sslContext);

        return new HttpsConfigurator(sslContext) {
            @Override
            public void configure(HttpsParameters params) {
                SSLParameters sslParameters = getSSLContext().getDefaultSSLParameters();

                if (!enabledProtocols.isEmpty()) {
                    sslParameters.setProtocols(enabledProtocols.toArray(String[]::new));
                }

                if (!enabledCipherSuites.isEmpty()) {
                    sslParameters.setCipherSuites(enabledCipherSuites.toArray(String[]::new));
                }
                params.setSSLParameters(sslParameters);
            }
        };
    }

    /**
     * @return SSL enabled Protocols.
     */
    public List<String> enabledProtocols() {
        return enabledProtocols;
    }

    /**
     * @return SSL cipher suites.
     */
    public List<String> enabledCipherSuites() {
        return enabledCipherSuites;
    }

    private void validateConfiguredValues(SSLContext sslContext) {
        SSLParameters supportedParameters = sslContext.getSupportedSSLParameters();
        validateConfiguredValues(
                enabledProtocols,
                Arrays.stream(supportedParameters.getProtocols()).collect(Collectors.toSet()),
                LISTENER_SSL_ENABLED_PROTOCOLS_CONFIG,
                "protocol");
        validateConfiguredValues(
                enabledCipherSuites,
                Arrays.stream(supportedParameters.getCipherSuites()).collect(Collectors.toSet()),
                LISTENER_SSL_ENABLED_CIPHER_SUITES_CONFIG,
                "cipher suite");
    }

    private void validateConfiguredValues(
            List<String> configuredValues,
            Set<String> supportedValues,
            String configName,
            String valueDescription) {
        List<String> unsupportedValues = configuredValues.stream()
                .filter(value -> !supportedValues.contains(value))
                .collect(Collectors.toList());

        if (!unsupportedValues.isEmpty()) {
            throw new ConfigException(
                    configName,
                    String.join(",", configuredValues),
                    "Unsupported SSL " + valueDescription + "(s): " + String.join(",", unsupportedValues));
        }
    }

    @Override
    public String toString() {
        return "HttpsConfiguratorFactory{" +
                "enabledProtocols=" + enabledProtocols +
                ", enabledCipherSuites=" + enabledCipherSuites +
                '}';
    }
}
