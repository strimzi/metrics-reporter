/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.prometheus.http;

import org.apache.kafka.common.config.ConfigException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLContext;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.strimzi.kafka.metrics.prometheus.http.SslTestUtils.CERTIFICATE;
import static io.strimzi.kafka.metrics.prometheus.http.SslTestUtils.PRIVATE_KEY;
import static io.strimzi.kafka.metrics.prometheus.http.SslTestUtils.RSA_CERTIFICATE;
import static io.strimzi.kafka.metrics.prometheus.http.SslTestUtils.RSA_PRIVATE_KEY;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SSLContextFactoryTest {

    private static final String PKCS1_RSA_PRIVATE_KEY = "-----BEGIN RSA PRIVATE KEY-----\nMIIE\n-----END RSA PRIVATE KEY-----\n";
    private static final String SEC1_EC_PRIVATE_KEY = "-----BEGIN EC PRIVATE KEY-----\nMHcC\n-----END EC PRIVATE KEY-----\n";
    private static final String ENCRYPTED_PRIVATE_KEY = "-----BEGIN ENCRYPTED PRIVATE KEY-----\nMIIE\n-----END ENCRYPTED PRIVATE KEY-----\n";

    @TempDir
    private Path tempDir;

    @Test
    public void testCreateFromInlineCertificateAndKey() {
        SSLContext sslContext = new SSLContextFactory(null, null, CERTIFICATE, PRIVATE_KEY).create();

        assertNotNull(sslContext);
    }

    @Test
    public void testCreateFromCertificateAndKeyLocations() throws Exception {
        Path certificate = tempDir.resolve("tls.crt");
        Path key = tempDir.resolve("tls.key");
        Files.writeString(certificate, CERTIFICATE);
        Files.writeString(key, PRIVATE_KEY);

        SSLContext sslContext = new SSLContextFactory(
                certificate.toString(),
                key.toString(),
                null,
                null).create();

        assertNotNull(sslContext);
    }

    @Test
    public void testInlineCertificateAndKeyTakePrecedenceOverLocations() {
        SSLContext sslContext = new SSLContextFactory(
                tempDir.resolve("missing.crt").toString(),
                tempDir.resolve("missing.key").toString(),
                CERTIFICATE,
                PRIVATE_KEY).create();

        assertNotNull(sslContext);
    }

    @Test
    public void testMissingCertificateFails() {
        ConfigException exception = assertThrows(
                ConfigException.class,
                () -> new SSLContextFactory(null, null, null, PRIVATE_KEY).create());

        assertTrue(exception.getMessage().contains("SSL certificate"));
    }

    @Test
    public void testMissingKeyFails() {
        ConfigException exception = assertThrows(
                ConfigException.class,
                () -> new SSLContextFactory(null, null, CERTIFICATE, null).create());

        assertTrue(exception.getMessage().contains("SSL private key"));
    }

    @Test
    public void testCreateFromPkcs8RsaPrivateKey() {
        SSLContext sslContext = new SSLContextFactory(null, null, RSA_CERTIFICATE, RSA_PRIVATE_KEY).create();

        assertNotNull(sslContext);
    }

    @Test
    public void testPkcs1RsaPrivateKeyIsRejected() {
        ConfigException exception = assertThrows(
                ConfigException.class,
                () -> new SSLContextFactory(null, null, CERTIFICATE, PKCS1_RSA_PRIVATE_KEY).create());

        assertTrue(exception.getMessage().contains("PKCS#8"));
    }

    @Test
    public void testSec1EcPrivateKeyIsRejected() {
        ConfigException exception = assertThrows(
                ConfigException.class,
                () -> new SSLContextFactory(null, null, CERTIFICATE, SEC1_EC_PRIVATE_KEY).create());

        assertTrue(exception.getMessage().contains("PKCS#8"));
    }

    @Test
    public void testEncryptedPrivateKeyIsRejected() {
        ConfigException exception = assertThrows(
                ConfigException.class,
                () -> new SSLContextFactory(null, null, CERTIFICATE, ENCRYPTED_PRIVATE_KEY).create());

        assertTrue(exception.getMessage().contains("PKCS#8"));
    }
}
