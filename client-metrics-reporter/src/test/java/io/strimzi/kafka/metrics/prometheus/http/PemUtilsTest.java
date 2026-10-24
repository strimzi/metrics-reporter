/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.prometheus.http;

import org.apache.kafka.common.config.ConfigException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static io.strimzi.kafka.metrics.prometheus.http.SslTestUtils.CERTIFICATE;
import static io.strimzi.kafka.metrics.prometheus.http.SslTestUtils.PRIVATE_KEY;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PemUtilsTest {

    @TempDir
    private Path tempDir;

    @Test
    public void testReadPrivateKeyFromString() {
        assertNotNull(PemUtils.readPrivateKey(PRIVATE_KEY));
    }

    @Test
    public void testReadPrivateKeyFromFile() throws Exception {
        Path key = tempDir.resolve("tls.key");
        Files.writeString(key, PRIVATE_KEY, StandardCharsets.US_ASCII);

        assertArrayEquals(
                PemUtils.readPrivateKey(PRIVATE_KEY).getEncoded(),
                PemUtils.readPrivateKey(key.toFile()).getEncoded());
    }

    @Test
    public void testReadCertificatesFromString() throws Exception {
        assertEquals(
                CERTIFICATE,
                new String(PemUtils.readCertificates(CERTIFICATE), StandardCharsets.US_ASCII));
    }

    @Test
    public void testReadCertificatesFromFile() throws Exception {
        Path certificate = tempDir.resolve("tls.crt");
        Files.writeString(certificate, CERTIFICATE, StandardCharsets.US_ASCII);

        assertEquals(
                CERTIFICATE,
                new String(PemUtils.readCertificates(certificate.toFile()), StandardCharsets.US_ASCII));
    }

    @Test
    public void testUnsupportedPrivateKeyLabelIsRejected() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> PemUtils.readPrivateKey("-----BEGIN RSA PRIVATE KEY-----\nAA==\n-----END RSA PRIVATE KEY-----\n"));

        assertTrue(exception.getMessage().contains("PRIVATE KEY"));
    }

    @Test
    public void testMissingCertificateBlockIsRejected() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> PemUtils.readCertificates("-----BEGIN PRIVATE KEY-----\nAA==\n-----END PRIVATE KEY-----\n"));

        assertTrue(exception.getMessage().contains("CERTIFICATE"));
    }

    @Test
    public void testEmptyPrivateKeyBlockIsRejected() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> PemUtils.readPrivateKey("-----BEGIN PRIVATE KEY-----\n-----END PRIVATE KEY-----\n"));

        assertTrue(exception.getMessage().contains("Empty"));
    }

    @Test
    public void testNonBase64PrivateKeyBodyIsRejected() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> PemUtils.readPrivateKey("-----BEGIN PRIVATE KEY-----\n!!!!\n-----END PRIVATE KEY-----\n"));

        assertTrue(exception.getMessage().contains("Failed to decode"));
    }

    @Test
    public void testNonStandardDashCountIsRejected() {
        String pem = "----BEGIN PRIVATE KEY----\n"
                + Base64.getEncoder().encodeToString(new byte[]{1, 2, 3, 4}) + "\n"
                + "----END PRIVATE KEY----\n";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> PemUtils.readPrivateKey(pem));

        assertTrue(exception.getMessage().contains("PRIVATE KEY"));
    }

    @Test
    public void testCertificateBytesKeepOriginalPem() {
        String pem = "prefix\n" + CERTIFICATE;

        assertEquals(pem, new String(PemUtils.readCertificates(pem), StandardCharsets.US_ASCII));
    }

    @Test
    public void testUnreadableFileFails() {
        File missing = tempDir.resolve("missing.key").toFile();

        ConfigException exception = assertThrows(
                ConfigException.class,
                () -> PemUtils.readPrivateKey(missing));

        assertTrue(exception.getMessage().contains("Failed to read"));
    }

    @Test
    public void testDecodedPrivateKeyMatchesBase64Payload() {
        byte[] expected = new byte[]{1, 2, 3, 4};
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getEncoder().encodeToString(expected) + "\n"
                + "-----END PRIVATE KEY-----\n";

        assertArrayEquals(expected, PemUtils.readPrivateKey(pem).getEncoded());
    }
}
