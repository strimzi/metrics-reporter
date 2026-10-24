/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.prometheus.http;

import org.apache.kafka.common.config.ConfigException;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;

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

    @Test
    public void testCreateFromCertificateAndKey() {
        SSLContext sslContext = new SSLContextFactory().create(
                PemUtils.readPrivateKey(PRIVATE_KEY),
                PemUtils.readCertificates(CERTIFICATE));

        assertNotNull(sslContext);
    }

    @Test
    public void testCreateFromPkcs8RsaPrivateKey() {
        SSLContext sslContext = new SSLContextFactory().create(
                PemUtils.readPrivateKey(RSA_PRIVATE_KEY),
                PemUtils.readCertificates(RSA_CERTIFICATE));

        assertNotNull(sslContext);
    }

    @Test
    public void testPkcs1RsaPrivateKeyIsRejected() {
        ConfigException exception = assertThrows(
                ConfigException.class,
                () -> PemUtils.readPrivateKey(PKCS1_RSA_PRIVATE_KEY));

        assertTrue(exception.getMessage().contains("PRIVATE KEY"));
    }

    @Test
    public void testSec1EcPrivateKeyIsRejected() {
        ConfigException exception = assertThrows(
                ConfigException.class,
                () -> PemUtils.readPrivateKey(SEC1_EC_PRIVATE_KEY));

        assertTrue(exception.getMessage().contains("PRIVATE KEY"));
    }

    @Test
    public void testEncryptedPrivateKeyIsRejected() {
        ConfigException exception = assertThrows(
                ConfigException.class,
                () -> PemUtils.readPrivateKey(ENCRYPTED_PRIVATE_KEY));

        assertTrue(exception.getMessage().contains("PRIVATE KEY"));
    }
}
