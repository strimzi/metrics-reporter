/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.prometheus.http;

import org.apache.kafka.common.config.ConfigException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;

import static io.strimzi.kafka.metrics.prometheus.ClientMetricsReporterConfig.LISTENER_SSL_CERTIFICATE_LOCATION_CONFIG;
import static io.strimzi.kafka.metrics.prometheus.ClientMetricsReporterConfig.LISTENER_SSL_KEY_LOCATION_CONFIG;

/**
 * Builds an {@link SSLContext} from a PEM certificate chain and an unencrypted PKCS#8 private key.
 */
public class SSLContextFactory {
    private static final String KEY_ENTRY_ALIAS = "metrics-reporter";
    private static final char[] IN_MEMORY_KEY_PASSWORD = new char[0];
    private static final String PKCS8_LABEL = "PRIVATE KEY";
    private static final String PKCS8_REQUIRED =
            "Private key must be unencrypted PKCS#8 PEM (BEGIN PRIVATE KEY)";
    private static final String[] KEY_ALGORITHMS = {"RSA", "EC"};

    /**
     * The path to the PEM file containing the server certificate or certificate chain.
     */
    public final String certificateLocation;
    /**
     * The path to the PEM file containing the server private key.
     */
    public final String keyLocation;
    /**
     * The inline PEM server certificate or certificate chain.
     */
    public final String certificate;
    /**
     * The inline PEM server private key.
     */
    public final String key;

    /**
     * Constructor.
     *
     * @param certificateLocation The path to the PEM file containing the server certificate or certificate chain.
     * @param keyLocation The path to the PEM file containing the private key.
     * @param certificate The inline PEM server certificate or certificate chain.
     * @param key The inline PEM server private key.
     */
    public SSLContextFactory(String certificateLocation, String keyLocation, String certificate, String key) {
        this.certificateLocation = certificateLocation;
        this.keyLocation = keyLocation;
        this.certificate = certificate;
        this.key = key;
    }

    /**
     * Creates a new {@link SSLContext} for the HTTPS metrics listener.
     *
     * @return the initialized SSL context
     */
    public SSLContext create() {
        Certificate[] certificateChain = certificates();
        PrivateKey privateKey = privateKey();
        try {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setKeyEntry(KEY_ENTRY_ALIAS, privateKey, IN_MEMORY_KEY_PASSWORD, certificateChain);

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, IN_MEMORY_KEY_PASSWORD);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
            return sslContext;
        } catch (GeneralSecurityException | IOException e) {
            throw new ConfigException("Failed to create SSL context for the metrics reporter listener");
        }
    }

    private Certificate[] certificates() {
        String pem = loadPem(
                certificate,
                certificateLocation,
                LISTENER_SSL_CERTIFICATE_LOCATION_CONFIG,
                "SSL certificate");
        try {
            Collection<? extends Certificate> certificates = CertificateFactory.getInstance("X.509")
                    .generateCertificates(new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
            if (certificates.isEmpty()) {
                throw new ConfigException("No X.509 certificates found");
            }
            return certificates.toArray(new Certificate[0]);
        } catch (GeneralSecurityException e) {
            throw new ConfigException("Failed to parse SSL certificate: " + e.getMessage());
        }
    }

    private PrivateKey privateKey() {
        String pem = loadPem(
                key,
                keyLocation,
                LISTENER_SSL_KEY_LOCATION_CONFIG,
                "SSL private key");
        if (!pem.contains("BEGIN " + PKCS8_LABEL)) {
            throw new ConfigException(PKCS8_REQUIRED);
        }

        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decodePemBlock(pem, PKCS8_LABEL));
        for (String algorithm : KEY_ALGORITHMS) {
            try {
                return KeyFactory.getInstance(algorithm).generatePrivate(keySpec);
            } catch (GeneralSecurityException ignored) {
                // try the next key algorithm
            }
        }
        throw new ConfigException("Failed to parse private key");
    }

    private String loadPem(
            String inlineValue,
            String location,
            String locationConfig,
            String description) {
        if (inlineValue != null) {
            return inlineValue;
        }
        if (location != null) {
            try {
                return Files.readString(Path.of(location), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new ConfigException(
                        locationConfig,
                        location,
                        "Failed to read " + description + ": " + e.getMessage());
            }
        }
        throw new ConfigException(description + " must be configured for HTTPS listeners");
    }

    private byte[] decodePemBlock(String pem, String label) {
        String beginMarker = "-----BEGIN " + label + "-----";
        String endMarker = "-----END " + label + "-----";
        int beginIndex = pem.indexOf(beginMarker);
        int endIndex = pem.indexOf(endMarker);
        if (beginIndex < 0 || endIndex < 0 || endIndex < beginIndex) {
            throw new ConfigException("No " + label + " PEM block found");
        }

        String encoded = pem.substring(beginIndex + beginMarker.length(), endIndex).replaceAll("\\s", "");
        if (encoded.isEmpty()) {
            throw new ConfigException("Empty " + label + " PEM block");
        }

        try {
            return Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw new ConfigException("Failed to decode private key: " + e.getMessage());
        }
    }

    @Override
    public String toString() {
        return "SSLContextFactory{" +
                "certificateLocation=" + certificateLocation +
                ", keyLocation=" + keyLocation +
                ", certificate=" + (certificate != null ? "[hidden]" : null) +
                ", key=" + (key != null ? "[hidden]" : null) +
                '}';
    }
}
