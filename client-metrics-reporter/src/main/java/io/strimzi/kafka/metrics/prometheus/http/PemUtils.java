/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.prometheus.http;

import org.apache.kafka.common.config.ConfigException;

import java.io.ByteArrayInputStream;
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

    // Header/footer patterns allow a flexible number of dashes and whitespace, but keep labels strict:
    // only "CERTIFICATE" and unencrypted PKCS#8 "PRIVATE KEY" (not RSA/EC/ENCRYPTED PRIVATE KEY).
    private static final Pattern CERT_HEADER = Pattern.compile(
            "-+BEGIN\\s+CERTIFICATE-+[\\s\\r\\n]+");
    private static final Pattern CERT_FOOTER = Pattern.compile(
            "-+END\\s+CERTIFICATE-+(?:\\s|\\r|\\n)*");
    private static final Pattern KEY_HEADER = Pattern.compile(
            "-+BEGIN\\s+PRIVATE\\s+KEY-+(?:\\s|\\r|\\n)+");
    private static final Pattern KEY_FOOTER = Pattern.compile(
            "-+END\\s+PRIVATE\\s+KEY-+(?:\\s|\\r|\\n)*");
    private static final Pattern BODY = Pattern.compile(
            "[a-z0-9+/=][a-z0-9+/=\\r\\n]*", Pattern.CASE_INSENSITIVE);

    private PemUtils() {
    }

    /**
     * Reads an unencrypted PKCS#8 private key from PEM content.
     *
     * @param key the PEM content
     * @return the PKCS#8 key spec
     */
    public static PKCS8EncodedKeySpec readPrivateKey(String key) {
        return new PKCS8EncodedKeySpec(decodeBlock(key));
    }

    /**
     * Reads an unencrypted PKCS#8 private key from a PEM file.
     *
     * @param file the PEM file
     * @return the PKCS#8 key spec
     */
    public static PKCS8EncodedKeySpec readPrivateKey(File file) {
        return readPrivateKey(readFile(file, "SSL private key"));
    }

    /**
     * Validates PEM certificate content and returns it as a stream for {@code CertificateFactory}.
     *
     * @param certificate the PEM content
     * @return the certificate bytes
     */
    public static ByteArrayInputStream readCertificates(String certificate) {
        if (!containsBlock(certificate)) {
            throw new ConfigException("No CERTIFICATE PEM block found");
        }
        return new ByteArrayInputStream(certificate.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Validates a PEM certificate file and returns it as a stream for {@code CertificateFactory}.
     *
     * @param file the PEM file
     * @return the certificate bytes
     */
    public static ByteArrayInputStream readCertificates(File file) {
        return readCertificates(readFile(file, "SSL certificate"));
    }

    private static byte[] decodeBlock(String pem) {
        Matcher header = PemUtils.KEY_HEADER.matcher(pem);
        if (!header.find()) {
            throw new ConfigException("No " + "PRIVATE KEY" + " PEM block found");
        }

        Matcher body = BODY.matcher(pem);
        body.region(header.end(), pem.length());
        if (!body.lookingAt()) {
            throw new ConfigException("Empty " + "PRIVATE KEY" + " PEM block");
        }

        Matcher footer = PemUtils.KEY_FOOTER.matcher(pem);
        footer.region(body.end(), pem.length());
        if (!footer.lookingAt()) {
            throw new ConfigException("No " + "PRIVATE KEY" + " PEM block found");
        }

        String encoded = body.group().replaceAll("\\s", "");
        try {
            return Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw new ConfigException("Failed to decode " + "PRIVATE KEY" + ": " + e.getMessage());
        }
    }

    private static boolean containsBlock(String pem) {
        Matcher header = PemUtils.CERT_HEADER.matcher(pem);
        if (!header.find()) {
            return false;
        }
        Matcher body = BODY.matcher(pem);
        body.region(header.end(), pem.length());
        if (!body.lookingAt()) {
            return false;
        }
        Matcher footer = PemUtils.CERT_FOOTER.matcher(pem);
        footer.region(body.end(), pem.length());
        return footer.lookingAt();
    }

    private static String readFile(File file, String description) {
        try {
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ConfigException("Failed to read " + description + " from " + file + ": " + e.getMessage());
        }
    }
}
