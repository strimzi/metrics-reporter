/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.prometheus.http;

import org.apache.kafka.common.config.ConfigException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilities for loading and parsing PEM-encoded material.
 */
public final class PemUtils {

    private static final Pattern CERTIFICATE_PEM = Pattern.compile(
            "-----BEGIN CERTIFICATE-----(.*?)-----END CERTIFICATE-----",
            Pattern.DOTALL);
    private static final Pattern PRIVATE_KEY_PEM = Pattern.compile(
            "-----BEGIN PRIVATE KEY-----(.*?)-----END PRIVATE KEY-----",
            Pattern.DOTALL);

    private PemUtils() {
    }

    /**
     * Reads an unencrypted PKCS#8 private key from PEM content.
     * The key must be an RSA or EC key in {@code BEGIN PRIVATE KEY} form.
     *
     * @param key the PEM content
     * @return the PKCS#8 key spec
     */
    public static PKCS8EncodedKeySpec readPrivateKey(String key) {
        Matcher matcher = PRIVATE_KEY_PEM.matcher(key);

        if (!matcher.find()) {
            throw new IllegalArgumentException("No PRIVATE KEY PEM block found");
        }

        String encoded = matcher.group(1).replaceAll("\\s", "");
        if (encoded.isEmpty()) {
            throw new IllegalArgumentException("Empty PRIVATE KEY PEM block");
        }
        byte[] decodedKey;
        try {
            decodedKey = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Failed to decode PRIVATE KEY: " + e.getMessage());
        }

        return new PKCS8EncodedKeySpec(decodedKey);
    }

    /**
     * Reads an unencrypted PKCS#8 private key from a PEM file.
     *
     * @param file the PEM file
     * @return the PKCS#8 key spec
     */
    public static PKCS8EncodedKeySpec readPrivateKey(File file) {
        try {
            return readPrivateKey(Files.readString(file.toPath(), StandardCharsets.US_ASCII));
        } catch (IOException e) {
            throw new ConfigException("Failed to read SSL private key from " + file + ": " + e.getMessage());
        }
    }

    /**
     * Checks that {@code certificate} contains a {@code CERTIFICATE} block and returns the original PEM text.
     *
     * @param certificate the PEM content
     * @return the original certificate PEM
     */
    public static byte[] readCertificates(String certificate) {
        if (!CERTIFICATE_PEM.matcher(certificate).find()) {
            throw new IllegalArgumentException("No CERTIFICATE PEM block found");
        }
        return certificate.getBytes(StandardCharsets.US_ASCII);
    }

    /**
     * Checks that a PEM file contains a {@code CERTIFICATE} block and returns the original PEM text.
     *
     * @param file the PEM file
     * @return the original certificate PEM
     */
    public static byte[] readCertificates(File file) {
        try {
            return readCertificates(Files.readString(file.toPath(), StandardCharsets.US_ASCII));
        } catch (IOException e) {
            throw new ConfigException("Failed to read SSL certificate from " + file + ": " + e.getMessage());
        }
    }
}
