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
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static io.strimzi.kafka.metrics.prometheus.http.SslTestUtils.CERTIFICATE;
import static io.strimzi.kafka.metrics.prometheus.http.SslTestUtils.EC_CERTIFICATE;
import static io.strimzi.kafka.metrics.prometheus.http.SslTestUtils.EC_PRIVATE_KEY;
import static io.strimzi.kafka.metrics.prometheus.http.SslTestUtils.PRIVATE_KEY;
import static io.strimzi.kafka.metrics.prometheus.http.SslTestUtils.RSA_CERTIFICATE;
import static io.strimzi.kafka.metrics.prometheus.http.SslTestUtils.RSA_PRIVATE_KEY;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SSLContextFactoryTest {

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
        assertThrows(ConfigException.class, () -> new SSLContextFactory(null, null, null, PRIVATE_KEY).create());
    }

    @Test
    public void testMissingKeyFails() {
        assertThrows(ConfigException.class, () -> new SSLContextFactory(null, null, CERTIFICATE, null).create());
    }

    @Test
    public void testCreateFromPkcs1RsaPrivateKey() {
        SSLContext sslContext = new SSLContextFactory(null, null, RSA_CERTIFICATE, RSA_PRIVATE_KEY).create();

        assertNotNull(sslContext);
    }

    @Test
    public void testCreateFromSec1EcPrivateKey() {
        SSLContext sslContext = new SSLContextFactory(null, null, EC_CERTIFICATE, EC_PRIVATE_KEY).create();

        assertNotNull(sslContext);
    }

    @Test
    public void testPrivateKeyThatDoesNotMatchCertificateFails() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        keyPairGenerator.initialize(256);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        String key = "-----BEGIN PRIVATE KEY-----\n" +
                Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(keyPair.getPrivate().getEncoded()) +
                "\n-----END PRIVATE KEY-----\n";

        assertThrows(ConfigException.class, () -> new SSLContextFactory(null, null, CERTIFICATE, key).create());
    }
}
