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
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Collection;

/**
 * Builds an {@link SSLContext} from a PEM certificate chain and an unencrypted PKCS#8 private key.
 */
public class SSLContextFactory {
    private static final String KEY_ENTRY_ALIAS = "metrics-reporter";
    private static final String[] KEY_ALGORITHMS = {"RSA", "EC"};

    /**
     * Creates a new {@link SSLContext} for the HTTPS metrics listener.
     *
     * @param keySpec the PKCS#8 private key
     * @param certificates the PEM certificate chain
     * @return the initialized SSL context
     */
    public SSLContext create(PKCS8EncodedKeySpec keySpec, ByteArrayInputStream certificates) {
        Certificate[] certificateChain = certificates(certificates);
        PrivateKey privateKey = privateKey(keySpec);
        try {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setKeyEntry(KEY_ENTRY_ALIAS, privateKey, null, certificateChain);

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, null);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
            return sslContext;
        } catch (GeneralSecurityException | IOException e) {
            throw new ConfigException("Failed to create SSL context for the metrics reporter listener");
        }
    }

    private Certificate[] certificates(ByteArrayInputStream certificates) {
        try {
            Collection<? extends Certificate> parsed = CertificateFactory.getInstance("X.509")
                    .generateCertificates(certificates);
            if (parsed.isEmpty()) {
                throw new ConfigException("No X.509 certificates found");
            }
            return parsed.toArray(new Certificate[0]);
        } catch (GeneralSecurityException e) {
            throw new ConfigException("Failed to parse SSL certificate: " + e.getMessage());
        }
    }

    private PrivateKey privateKey(PKCS8EncodedKeySpec keySpec) {
        for (String algorithm : KEY_ALGORITHMS) {
            try {
                return KeyFactory.getInstance(algorithm).generatePrivate(keySpec);
            } catch (GeneralSecurityException ignored) {
                // try the next key algorithm
            }
        }
        throw new ConfigException("Failed to parse private key");
    }
}
