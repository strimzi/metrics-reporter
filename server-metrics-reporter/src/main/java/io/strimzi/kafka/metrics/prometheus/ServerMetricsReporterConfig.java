/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.prometheus;

import io.prometheus.metrics.model.registry.PrometheusRegistry;
import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
* Configuration for the PrometheusMetricsReporter implementation.
*/
public class ServerMetricsReporterConfig extends ClientMetricsReporterConfig {

    private static final Logger LOG = LoggerFactory.getLogger(ServerMetricsReporterConfig.class);

    /**
     * Value to include the clientId in telemetry labels
     */
    public static final String CLIENT_ID_LABEL = "client_id";

    /**
     * Value to include the listener name in telemetry labels
     */
    public static final String LISTENER_NAME_LABEL = "listener_name";

    /**
     * Value to include the security protocol in telemetry labels
     */
    public static final String SECURITY_PROTOCOL_LABEL = "security_protocol";

    /**
     * Value to include the principal in telemetry labels
     */
    public static final String PRINCIPAL_LABEL = "principal";

    /**
     * Value to include the client address in telemetry labels
     */
    public static final String CLIENT_ADDRESS_LABEL = "client_address";

    /**
     * Configuration key for the telemetry labels to include in client metrics.
     */
    public static final String CLIENT_TELEMETRY_LABELS_CONFIG = CONFIG_PREFIX + "client.telemetry.labels";

    /**
     * Default value for the telemetry labels configuration.
     */
    public static final List<String> CLIENT_TELEMETRY_LABELS_CONFIG_DEFAULT = List.of(CLIENT_ID_LABEL);
    private static final String CLIENT_TELEMETRY_LABELS_CONFIG_DOC = "A comma separated list of label names to include " +
            "in client telemetry metrics. Valid values are: " + CLIENT_ID_LABEL + ", " + LISTENER_NAME_LABEL + ", " +
            SECURITY_PROTOCOL_LABEL + ", " + PRINCIPAL_LABEL + ", " + CLIENT_ADDRESS_LABEL + ".";

    static final ConfigDef SERVER_CONFIG_DEF = new ConfigDef(CONFIG_DEF)
            .define(CLIENT_TELEMETRY_LABELS_CONFIG,
                    ConfigDef.Type.LIST,
                    CLIENT_TELEMETRY_LABELS_CONFIG_DEFAULT,
                    ConfigDef.ValidList.in(
                            CLIENT_ID_LABEL,
                            LISTENER_NAME_LABEL,
                            SECURITY_PROTOCOL_LABEL,
                            PRINCIPAL_LABEL,
                            CLIENT_ADDRESS_LABEL),
                    ConfigDef.Importance.MEDIUM,
                    CLIENT_TELEMETRY_LABELS_CONFIG_DOC);

    /**
     * The configurations that are reconfigurable
     */
    public static final Set<String> RECONFIGURABLES = Set.of(ALLOWLIST_CONFIG, CLIENT_TELEMETRY_LABELS_CONFIG);

    private volatile Pattern allowlist;
    private volatile List<String> telemetryLabels;

    /**
     * Constructor.
     *
     * @param props the configuration properties.
     * @param registry the metrics registry
     */
    public ServerMetricsReporterConfig(Map<?, ?> props, PrometheusRegistry registry) {
        super(props, registry);
        this.allowlist = compileAllowlist(getList(ALLOWLIST_CONFIG));
        AbstractConfig serverConfig = new AbstractConfig(SERVER_CONFIG_DEF, props, false);
        this.telemetryLabels = serverConfig.getList(CLIENT_TELEMETRY_LABELS_CONFIG);
    }

    /**
     * Update the reconfigurable configurations
     * @param props The new configuration
     */
    public void reconfigure(Map<String, ?> props) {
        AbstractConfig abstractConfig = new AbstractConfig(SERVER_CONFIG_DEF, props, false);
        allowlist = compileAllowlist(abstractConfig.getList(ALLOWLIST_CONFIG));
        telemetryLabels = abstractConfig.getList(CLIENT_TELEMETRY_LABELS_CONFIG);
        LOG.info("Updated allowlist to {} and telemetry labels to {}", allowlist, telemetryLabels);
    }

    /**
     * Validate the reconfigurable configurations
     * @param configs The new configuration to validate
     * @throws ConfigException if the configuration is invalid
     */
    public void validate(Map<String, ?> configs) throws ConfigException {
        AbstractConfig abstractConfig = new AbstractConfig(SERVER_CONFIG_DEF, configs, false);
        compileAllowlist(abstractConfig.getList(ALLOWLIST_CONFIG));
    }

    @Override
    public Pattern allowlist() {
        return allowlist;
    }

    /**
     * The configured telemetry labels.
     * @return The List of telemetry labels
     */
    public List<String> telemetryLabels() {
        return telemetryLabels;
    }

    @Override
    public String toString() {
        return "ServerMetricsReporterConfig{" +
                "listener=" + listener +
                ", listenerEnabled=" + listenerEnabled +
                ", allowlist=" + allowlist +
                ", telemetryLabels=" + telemetryLabels +
                '}';
    }
}
