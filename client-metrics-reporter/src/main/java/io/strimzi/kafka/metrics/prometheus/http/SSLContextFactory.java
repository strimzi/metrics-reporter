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
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;

import static io.strimzi.kafka.metrics.prometheus.ClientMetricsReporterConfig.LISTENER_SSL_CERTIFICATE_CONFIG;
import static io.strimzi.kafka.metrics.prometheus.ClientMetricsReporterConfig.LISTENER_SSL_CERTIFICATE_LOCATION_CONFIG;
import static io.strimzi.kafka.metrics.prometheus.ClientMetricsReporterConfig.LISTENER_SSL_KEY_CONFIG;
import static io.strimzi.kafka.metrics.prometheus.ClientMetricsReporterConfig.LISTENER_SSL_KEY_LOCATION_CONFIG;

/**
 * Factory for building the SSL context used by the HTTPS listener.
 */
public class SSLContextFactory {
    private static final String KEY_ENTRY_ALIAS = "metrics-reporter";
    private static final String KEY_STORE_PASSWORD = "changeit";
    private static final byte[] RSA_ENCRYPTION_OID = oid("1.2.840.113549.1.1.1");
    private static final byte[] EC_PUBLIC_KEY_OID = oid("1.2.840.10045.2.1");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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
     * @param keyLocation The path to the PEM file containing the server private key.
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
     * Creates a new {@link javax.net.ssl.SSLContext}
     * @return {@link javax.net.ssl.SSLContext}
     */
    public SSLContext create() {
        try {
            X509Certificate[] certificateChain = certificateChain(loadCertificateChain());
            PrivateKey privateKey = privateKey(loadKey());
            validatePrivateKeyMatchesCertificate(privateKey, certificateChain[0]);

            char[] keyPassword = KEY_STORE_PASSWORD.toCharArray();

            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, null);
            keyStore.setKeyEntry(KEY_ENTRY_ALIAS, privateKey, keyPassword, certificateChain);

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, keyPassword);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagerFactory.getKeyManagers(), null, SECURE_RANDOM);
            return sslContext;
        } catch (ConfigException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigException(
                    "Failed to create SSL context for the metrics reporter listener",
                    e);
        }
    }

    private String loadCertificateChain() {
        return loadPem(
                certificate,
                certificateLocation,
                LISTENER_SSL_CERTIFICATE_CONFIG,
                LISTENER_SSL_CERTIFICATE_LOCATION_CONFIG,
                "SSL certificate");
    }

    private String loadKey() {
        return loadPem(
                key,
                keyLocation,
                LISTENER_SSL_KEY_CONFIG,
                LISTENER_SSL_KEY_LOCATION_CONFIG,
                "SSL private key");
    }

    private String loadPem(
            String inlineValue,
            String location,
            String inlineConfig,
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
        throw new ConfigException(inlineConfig, null, description + " must be configured for HTTPS listeners");
    }

    private X509Certificate[] certificateChain(String pem) throws Exception {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        Collection<? extends Certificate> certificates = certificateFactory.generateCertificates(
                new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
        if (certificates.isEmpty()) {
            throw new ConfigException(LISTENER_SSL_CERTIFICATE_CONFIG, null, "No X.509 certificates found");
        }

        X509Certificate[] certificateChain = new X509Certificate[certificates.size()];
        int index = 0;
        for (Certificate certificate : certificates) {
            if (!(certificate instanceof X509Certificate)) {
                throw new ConfigException(LISTENER_SSL_CERTIFICATE_CONFIG, null, "Only X.509 certificates are supported");
            }
            certificateChain[index] = (X509Certificate) certificate;
            index++;
        }
        return certificateChain;
    }

    private PrivateKey privateKey(String pem) {
        if (pem.contains("BEGIN PRIVATE KEY")) {
            return parsePkcs8PrivateKey(decodePemBlock(pem, "PRIVATE KEY"));
        } else if (pem.contains("BEGIN RSA PRIVATE KEY")) {
            return parsePrivateKey("RSA", wrapPkcs1RsaPrivateKey(decodePemBlock(pem, "RSA PRIVATE KEY")), "RSA");
        } else if (pem.contains("BEGIN EC PRIVATE KEY")) {
            return parsePrivateKey("EC", wrapSec1EcPrivateKey(decodePemBlock(pem, "EC PRIVATE KEY")), "EC");
        } else {
            throw new ConfigException(
                    LISTENER_SSL_KEY_CONFIG,
                    null,
                    "No unencrypted PEM private key found");
        }
    }

    private PrivateKey parsePkcs8PrivateKey(byte[] keyBytes) {
        for (String algorithm : new String[]{"RSA", "EC", "DSA"}) {
            try {
                return parsePrivateKey(algorithm, keyBytes);
            } catch (InvalidKeySpecException ignored) {
                // try the next key algorithm
            } catch (Exception e) {
                throw new ConfigException(
                        LISTENER_SSL_KEY_CONFIG,
                        null,
                        "Failed to parse private key: " + e.getMessage());
            }
        }
        throw new ConfigException(LISTENER_SSL_KEY_CONFIG, null, "Failed to parse private key");
    }

    private PrivateKey parsePrivateKey(String algorithm, byte[] keyBytes, String keyType) {
        try {
            return parsePrivateKey(algorithm, keyBytes);
        } catch (GeneralSecurityException e) {
            throw new ConfigException(
                    LISTENER_SSL_KEY_CONFIG,
                    null,
                    "Failed to parse " + keyType + " private key: " + e.getMessage());
        }
    }

    private PrivateKey parsePrivateKey(String algorithm, byte[] keyBytes) throws GeneralSecurityException {
        return KeyFactory.getInstance(algorithm).generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }

    private byte[] decodePemBlock(String pem, String label) {
        String beginMarker = "-----BEGIN " + label + "-----";
        String endMarker = "-----END " + label + "-----";
        int beginIndex = pem.indexOf(beginMarker);
        int endIndex = pem.indexOf(endMarker);
        if (beginIndex < 0 || endIndex < 0 || endIndex < beginIndex) {
            throw new ConfigException(LISTENER_SSL_KEY_CONFIG, null, "No " + label + " PEM block found");
        }

        String encoded = pem.substring(beginIndex + beginMarker.length(), endIndex).replaceAll("\\s", "");
        if (encoded.isEmpty()) {
            throw new ConfigException(LISTENER_SSL_KEY_CONFIG, null, "Empty " + label + " PEM block");
        }

        try {
            return Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw new ConfigException(LISTENER_SSL_KEY_CONFIG, null, "Failed to decode private key: " + e.getMessage());
        }
    }

    private byte[] wrapPkcs1RsaPrivateKey(byte[] pkcs1KeyBytes) {
        return sequence(
                integer(0),
                sequence(RSA_ENCRYPTION_OID, derNull()),
                octetString(pkcs1KeyBytes));
    }

    private byte[] wrapSec1EcPrivateKey(byte[] sec1KeyBytes) {
        byte[] namedCurveOid = sec1EcParameters(sec1KeyBytes);
        return sequence(
                integer(0),
                sequence(EC_PUBLIC_KEY_OID, namedCurveOid),
                octetString(sec1KeyBytes));
    }

    private byte[] sec1EcParameters(byte[] sec1KeyBytes) {
        DerValue sequence = readDerValue(sec1KeyBytes, 0);
        if (sequence.tag != 0x30) {
            throw new ConfigException(LISTENER_SSL_KEY_CONFIG, null, "Failed to parse EC private key");
        }

        int end = sequence.valueOffset + sequence.length;
        int offset = sequence.valueOffset;
        while (offset < end) {
            DerValue value = readDerValue(sec1KeyBytes, offset);
            if (value.tag == 0xA0) {
                DerValue parameters = readDerValue(sec1KeyBytes, value.valueOffset);
                if (parameters.tag == 0x06) {
                    return copyOf(sec1KeyBytes, value.valueOffset, parameters.endOffset);
                }
            }
            offset = value.endOffset;
        }

        throw new ConfigException(
                LISTENER_SSL_KEY_CONFIG,
                null,
                "EC private key must include named curve parameters");
    }

    private void validatePrivateKeyMatchesCertificate(PrivateKey privateKey, X509Certificate certificate) {
        try {
            Signature signature = Signature.getInstance(signatureAlgorithm(privateKey.getAlgorithm()));
            byte[] challenge = new byte[32];
            SECURE_RANDOM.nextBytes(challenge);

            signature.initSign(privateKey);
            signature.update(challenge);
            byte[] signedChallenge = signature.sign();

            signature.initVerify(certificate.getPublicKey());
            signature.update(challenge);
            if (!signature.verify(signedChallenge)) {
                throw new ConfigException(
                        LISTENER_SSL_KEY_CONFIG,
                        null,
                        "Private key does not match the leaf certificate");
            }
        } catch (GeneralSecurityException e) {
            throw new ConfigException(
                    LISTENER_SSL_KEY_CONFIG,
                    null,
                    "Private key does not match the leaf certificate");
        }
    }

    private String signatureAlgorithm(String keyAlgorithm) {
        switch (keyAlgorithm) {
            case "RSA":
                return "SHA256withRSA";
            case "EC":
                return "SHA256withECDSA";
            case "DSA":
                return "SHA256withDSA";
            default:
                throw new ConfigException(
                        LISTENER_SSL_KEY_CONFIG,
                        null,
                        "Unsupported private key algorithm: " + keyAlgorithm);
        }
    }

    private static byte[] sequence(byte[]... values) {
        return derValue(0x30, concat(values));
    }

    private static byte[] integer(int value) {
        return derValue(0x02, new byte[]{(byte) value});
    }

    private static byte[] octetString(byte[] value) {
        return derValue(0x04, value);
    }

    private static byte[] derNull() {
        return new byte[]{0x05, 0x00};
    }

    private static byte[] oid(String dottedOid) {
        String[] parts = dottedOid.split("\\.");
        List<Byte> bytes = new ArrayList<>();
        bytes.add((byte) (Integer.parseInt(parts[0]) * 40 + Integer.parseInt(parts[1])));
        for (int i = 2; i < parts.length; i++) {
            long value = Long.parseLong(parts[i]);
            List<Byte> encodedValue = new ArrayList<>();
            encodedValue.add((byte) (value & 0x7F));
            value >>= 7;
            while (value > 0) {
                encodedValue.add(0, (byte) ((value & 0x7F) | 0x80));
                value >>= 7;
            }
            bytes.addAll(encodedValue);
        }

        byte[] oidBytes = new byte[bytes.size()];
        for (int i = 0; i < bytes.size(); i++) {
            oidBytes[i] = bytes.get(i);
        }
        return derValue(0x06, oidBytes);
    }

    private static byte[] derValue(int tag, byte[] value) {
        return concat(new byte[]{(byte) tag}, length(value.length), value);
    }

    private static byte[] length(int length) {
        if (length < 128) {
            return new byte[]{(byte) length};
        }

        int lengthBytes = 0;
        int remaining = length;
        while (remaining > 0) {
            lengthBytes++;
            remaining >>= 8;
        }

        byte[] encoded = new byte[lengthBytes + 1];
        encoded[0] = (byte) (0x80 | lengthBytes);
        for (int i = lengthBytes; i > 0; i--) {
            encoded[i] = (byte) (length & 0xFF);
            length >>= 8;
        }
        return encoded;
    }

    private static byte[] concat(byte[]... values) {
        int length = 0;
        for (byte[] value : values) {
            length += value.length;
        }

        byte[] result = new byte[length];
        int offset = 0;
        for (byte[] value : values) {
            System.arraycopy(value, 0, result, offset, value.length);
            offset += value.length;
        }
        return result;
    }

    private static DerValue readDerValue(byte[] bytes, int offset) {
        if (offset + 2 > bytes.length) {
            throw new ConfigException(LISTENER_SSL_KEY_CONFIG, null, "Failed to parse private key");
        }

        int tag = bytes[offset] & 0xFF;
        int lengthByte = bytes[offset + 1] & 0xFF;
        int lengthOffset = offset + 2;
        int valueLength;
        if ((lengthByte & 0x80) == 0) {
            valueLength = lengthByte;
        } else {
            int lengthBytes = lengthByte & 0x7F;
            if (lengthBytes == 0 || lengthBytes > 4 || lengthOffset + lengthBytes > bytes.length) {
                throw new ConfigException(LISTENER_SSL_KEY_CONFIG, null, "Failed to parse private key");
            }

            valueLength = 0;
            for (int i = 0; i < lengthBytes; i++) {
                valueLength = (valueLength << 8) | (bytes[lengthOffset + i] & 0xFF);
            }
            lengthOffset += lengthBytes;
        }

        int endOffset = lengthOffset + valueLength;
        if (endOffset > bytes.length) {
            throw new ConfigException(LISTENER_SSL_KEY_CONFIG, null, "Failed to parse private key");
        }
        return new DerValue(tag, lengthOffset, valueLength, endOffset);
    }

    private static byte[] copyOf(byte[] bytes, int from, int to) {
        byte[] copy = new byte[to - from];
        System.arraycopy(bytes, from, copy, 0, copy.length);
        return copy;
    }

    private static class DerValue {
        private final int tag;
        private final int valueOffset;
        private final int length;
        private final int endOffset;

        private DerValue(int tag, int valueOffset, int length, int endOffset) {
            this.tag = tag;
            this.valueOffset = valueOffset;
            this.length = length;
            this.endOffset = endOffset;
        }
    }

    @Override
    public String toString() {
        return "SslContextFactory{" +
                "certificateLocation=" + certificateLocation +
                ", keyLocation=" + keyLocation +
                ", certificate=" + (certificate != null ? "[hidden]" : null) +
                ", key=" + (key != null ? "[hidden]" : null) +
                '}';
    }
}
