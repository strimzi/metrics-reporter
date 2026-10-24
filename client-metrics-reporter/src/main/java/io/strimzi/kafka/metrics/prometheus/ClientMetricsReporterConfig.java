/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.prometheus;

import com.sun.net.httpserver.HttpsConfigurator;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.strimzi.kafka.metrics.prometheus.http.HttpServers;
import io.strimzi.kafka.metrics.prometheus.http.HttpsConfiguratorFactory;
import io.strimzi.kafka.metrics.prometheus.http.Listener;
import io.strimzi.kafka.metrics.prometheus.http.PemUtils;
import io.strimzi.kafka.metrics.prometheus.http.SSLContextFactory;
import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
* Configuration for {@link ClientMetricsReporter}.
*/
public class ClientMetricsReporterConfig extends AbstractConfig {

    private static final Logger LOG = LoggerFactory.getLogger(ClientMetricsReporterConfig.class);
    static final String CONFIG_PREFIX = "prometheus.metrics.reporter.";

    /**
     * Configuration key for the listener to expose the metrics.
     */
    public static final String LISTENER_CONFIG = CONFIG_PREFIX + "listener";

    /**
     * Default value for the listener configuration.
     */
    public static final String LISTENER_CONFIG_DEFAULT = "http://:8080";
    private static final String LISTENER_CONFIG_DOC = "The HTTP or HTTPS listener to expose the metrics.";
    private static final String LISTENER_SSL_CONFIG_PREFIX = LISTENER_CONFIG + ".ssl.";

    /**
     * Configuration key for the PEM file containing the server certificate or certificate chain.
     */
    public static final String LISTENER_SSL_CERTIFICATE_LOCATION_CONFIG =
            LISTENER_SSL_CONFIG_PREFIX + "certificate.location";
    private static final String LISTENER_SSL_CERTIFICATE_LOCATION_CONFIG_DOC =
            "The path to the PEM file containing the server certificate or certificate chain.";

    /**
     * Configuration key for the PEM file containing the server private key.
     */
    public static final String LISTENER_SSL_KEY_LOCATION_CONFIG = LISTENER_SSL_CONFIG_PREFIX + "key.location";
    private static final String LISTENER_SSL_KEY_LOCATION_CONFIG_DOC =
            "The path to the PEM file containing the unencrypted PKCS#8 RSA or EC private key.";

    /**
     * Configuration key for the inline PEM server certificate or certificate chain.
     */
    public static final String LISTENER_SSL_CERTIFICATE_CONFIG = LISTENER_SSL_CONFIG_PREFIX + "certificate";
    private static final String LISTENER_SSL_CERTIFICATE_CONFIG_DOC =
            "The server certificate or certificate chain in PEM format. This takes precedence over " +
                    LISTENER_SSL_CERTIFICATE_LOCATION_CONFIG + ".";

    /**
     * Configuration key for the inline PEM server private key.
     */
    public static final String LISTENER_SSL_KEY_CONFIG = LISTENER_SSL_CONFIG_PREFIX + "key";
    private static final String LISTENER_SSL_KEY_CONFIG_DOC =
            "The unencrypted PKCS#8 RSA or EC private key in PEM format. This takes precedence over " +
                    LISTENER_SSL_KEY_LOCATION_CONFIG + ".";

    /**
     * Configuration key for the enabled secure transport protocols.
     */
    public static final String LISTENER_SSL_ENABLED_PROTOCOLS_CONFIG = LISTENER_SSL_CONFIG_PREFIX + "enabled.protocols";

    /**
     * Default value for the enabled secure transport protocols.
     */
    public static final String LISTENER_SSL_ENABLED_PROTOCOLS_CONFIG_DEFAULT = "TLSv1.2,TLSv1.3";
    private static final String LISTENER_SSL_ENABLED_PROTOCOLS_CONFIG_DOC =
            "A comma separated list of enabled secure transport protocols.";

    /**
     * Configuration key for the enabled cipher suites.
     */
    public static final String LISTENER_SSL_ENABLED_CIPHER_SUITES_CONFIG =
            LISTENER_SSL_CONFIG_PREFIX + "enabled.cipher.suites";

    /**
     * Default value for the enabled cipher suites. An empty list uses Java's default cipher suites.
     */
    public static final String LISTENER_SSL_ENABLED_CIPHER_SUITES_CONFIG_DEFAULT = "";
    private static final String LISTENER_SSL_ENABLED_CIPHER_SUITES_CONFIG_DOC =
            "A comma separated list of cipher suites that the server will support. " +
                    "Empty uses Java's default cipher suites.";

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
    private static final String ALLOWLIST_CONFIG_DOC =
            "A comma separated list of regex patterns to specify the metrics to collect.";

    static final ConfigDef CONFIG_DEF = new ConfigDef()
            .define(LISTENER_CONFIG, ConfigDef.Type.STRING, LISTENER_CONFIG_DEFAULT, new Listener.ListenerValidator(),
                    ConfigDef.Importance.HIGH, LISTENER_CONFIG_DOC)
            .define(LISTENER_SSL_CERTIFICATE_LOCATION_CONFIG, ConfigDef.Type.STRING, null,
                    ConfigDef.Importance.MEDIUM, LISTENER_SSL_CERTIFICATE_LOCATION_CONFIG_DOC)
            .define(LISTENER_SSL_KEY_LOCATION_CONFIG, ConfigDef.Type.STRING, null,
                    ConfigDef.Importance.MEDIUM, LISTENER_SSL_KEY_LOCATION_CONFIG_DOC)
            .define(LISTENER_SSL_CERTIFICATE_CONFIG, ConfigDef.Type.PASSWORD, null,
                    ConfigDef.Importance.MEDIUM, LISTENER_SSL_CERTIFICATE_CONFIG_DOC)
            .define(LISTENER_SSL_KEY_CONFIG, ConfigDef.Type.PASSWORD, null,
                    ConfigDef.Importance.MEDIUM, LISTENER_SSL_KEY_CONFIG_DOC)
            .define(LISTENER_SSL_ENABLED_PROTOCOLS_CONFIG, ConfigDef.Type.LIST,
                    LISTENER_SSL_ENABLED_PROTOCOLS_CONFIG_DEFAULT,
                    ConfigDef.Importance.MEDIUM, LISTENER_SSL_ENABLED_PROTOCOLS_CONFIG_DOC)
            .define(LISTENER_SSL_ENABLED_CIPHER_SUITES_CONFIG, ConfigDef.Type.LIST,
                    LISTENER_SSL_ENABLED_CIPHER_SUITES_CONFIG_DEFAULT,
                    ConfigDef.Importance.MEDIUM, LISTENER_SSL_ENABLED_CIPHER_SUITES_CONFIG_DOC)
            .define(ALLOWLIST_CONFIG, ConfigDef.Type.LIST, ALLOWLIST_CONFIG_DEFAULT,
                    ConfigDef.Importance.HIGH, ALLOWLIST_CONFIG_DOC)
            .define(LISTENER_ENABLE_CONFIG, ConfigDef.Type.BOOLEAN, LISTENER_ENABLE_CONFIG_DEFAULT,
                    ConfigDef.Importance.HIGH, LISTENER_ENABLE_CONFIG_DOC);

    final Listener listener;
    final boolean listenerEnabled;
    final PrometheusRegistry registry;
    final Pattern allowlist;

    /**
     * Constructor.
     *
     * @param props the configuration properties.
     * @param registry the metrics registry
     */
    @SuppressFBWarnings("CT_CONSTRUCTOR_THROW")
    public ClientMetricsReporterConfig(Map<?, ?> props, PrometheusRegistry registry) {
        super(CONFIG_DEF, props);
        this.listener = Listener.parseListener(getString(LISTENER_CONFIG));
        this.allowlist = compileAllowlist(getList(ALLOWLIST_CONFIG));
        this.listenerEnabled = getBoolean(LISTENER_ENABLE_CONFIG);
        this.registry = registry;
    }

    private String passwordValue(String config) {
        return getPassword(config) != null ? getPassword(config).value() : null;
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

    /**
     * The configured allowlist.
     * @return The Pattern for the allowlist
     */
    public Pattern allowlist() {
        return allowlist;
    }

    Pattern compileAllowlist(List<String> allowlist) {
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
        return "ClientMetricsReporterConfig{" +
                "listener=" + listener +
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
        HttpsConfigurator httpsConfigurator = null;
        if (listener.isHttps()) {
            PKCS8EncodedKeySpec keySpec = privateKey();
            byte[] certificate = certificates();
            HttpsConfiguratorFactory httpsConfiguratorFactory = new HttpsConfiguratorFactory(
                    getList(LISTENER_SSL_ENABLED_PROTOCOLS_CONFIG),
                    getList(LISTENER_SSL_ENABLED_CIPHER_SUITES_CONFIG));
            httpsConfigurator = httpsConfiguratorFactory.create(
                    new SSLContextFactory().create(keySpec, certificate),
                    certificate,
                    keySpec.getEncoded());
        }
        HttpServers.ServerCounter server = HttpServers.getOrCreate(listener, registry, httpsConfigurator);
        LOG.info("HTTP server listening on {}://{}:{}", listener.scheme, listener.host, server.port());
        return Optional.of(server);
    }

    /**
     * Loads the unencrypted PKCS#8 RSA or EC private key.
     *
     * @return the PKCS#8 key spec
     */
    private PKCS8EncodedKeySpec privateKey() {
        String inline = passwordValue(LISTENER_SSL_KEY_CONFIG);
        if (inline != null) {
            return PemUtils.readPrivateKey(inline);
        }
        String location = getString(LISTENER_SSL_KEY_LOCATION_CONFIG);
        if (location != null) {
            return PemUtils.readPrivateKey(new File(location));
        }
        throw new ConfigException("SSL private key must be configured for HTTPS listeners");
    }

    private byte[] certificates() {
        String inline = passwordValue(LISTENER_SSL_CERTIFICATE_CONFIG);
        if (inline != null) {
            return PemUtils.readCertificates(inline);
        }
        String location = getString(LISTENER_SSL_CERTIFICATE_LOCATION_CONFIG);
        if (location != null) {
            return PemUtils.readCertificates(new File(location));
        }
        throw new ConfigException("SSL certificate must be configured for HTTPS listeners");
    }
}
