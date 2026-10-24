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
import java.util.Objects;
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
     * Two configurators are equal when the certificate PEM, PKCS#8 key, protocols, and cipher suites match.
     * {@link io.strimzi.kafka.metrics.prometheus.http.HttpServers} uses that equality to reuse a listener.
     *
     * @param sslContext  The SSL context to use for HTTPS.
     * @param certificate The PEM certificate bytes used to build {@code sslContext}.
     * @param privateKey  The PKCS#8 private key bytes used to build {@code sslContext}.
     * @return The HTTPS configurator.
     */
    public HttpsConfigurator create(SSLContext sslContext, byte[] certificate, byte[] privateKey) {
        validateConfiguredValues(sslContext);

        return new MetricsHttpsConfigurator(
                sslContext,
                certificate,
                privateKey,
                enabledProtocols,
                enabledCipherSuites);
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

    /**
     * HTTPS configurator whose equality is the certificate, private key, protocols, and cipher suites.
     * The private key is kept only so a second reporter on the same listener can be compared.
     * It is not included in {@link #toString()}.
     */
    static final class MetricsHttpsConfigurator extends HttpsConfigurator {
        private final byte[] certificate;
        private final byte[] privateKey;
        private final List<String> enabledProtocols;
        private final List<String> enabledCipherSuites;

        private MetricsHttpsConfigurator(
                SSLContext sslContext,
                byte[] certificate,
                byte[] privateKey,
                List<String> enabledProtocols,
                List<String> enabledCipherSuites) {
            super(sslContext);
            this.certificate = certificate.clone();
            this.privateKey = privateKey.clone();
            this.enabledProtocols = List.copyOf(enabledProtocols);
            this.enabledCipherSuites = List.copyOf(enabledCipherSuites);
        }

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

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof MetricsHttpsConfigurator)) {
                return false;
            }
            MetricsHttpsConfigurator that = (MetricsHttpsConfigurator) o;
            return Arrays.equals(certificate, that.certificate)
                    && Arrays.equals(privateKey, that.privateKey)
                    && enabledProtocols.equals(that.enabledProtocols)
                    && enabledCipherSuites.equals(that.enabledCipherSuites);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    Arrays.hashCode(certificate),
                    Arrays.hashCode(privateKey),
                    enabledProtocols,
                    enabledCipherSuites);
        }

        @Override
        public String toString() {
            return "MetricsHttpsConfigurator{" +
                    "enabledProtocols=" + enabledProtocols +
                    ", enabledCipherSuites=" + enabledCipherSuites +
                    '}';
        }
    }
}
