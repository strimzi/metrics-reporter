/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.prometheus.http;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

/**
 * Test-only SSL helper.
 * The Certificates and private keys are used only for tests. They are self-signed, localhost-only, and must never be
 * used in production or documentation examples.
 */
public class SslTestUtils {

    public static final String CERTIFICATE = "-----BEGIN CERTIFICATE-----\n" +
            "MIIBfTCCASOgAwIBAgIUIRPqedJbenp++JeHFbGdeHElM8EwCgYIKoZIzj0EAwIw\n" +
            "FDESMBAGA1UEAwwJbG9jYWxob3N0MB4XDTI2MDUyMDIwNTkwOFoXDTM2MDUxNzIw\n" +
            "NTkwOFowFDESMBAGA1UEAwwJbG9jYWxob3N0MFkwEwYHKoZIzj0CAQYIKoZIzj0D\n" +
            "AQcDQgAEvjkYs/aUPQVCthgrFywfX6ZaLp8tVo8MBWXHjwN0VtOEbDgVoJASYOwP\n" +
            "jwLgx1Pn2lqmHE5eBRpawac2vZj2GaNTMFEwHQYDVR0OBBYEFI4T6of+BBiOcZJI\n" +
            "hM+v8RInHnRoMB8GA1UdIwQYMBaAFI4T6of+BBiOcZJIhM+v8RInHnRoMA8GA1Ud\n" +
            "EwEB/wQFMAMBAf8wCgYIKoZIzj0EAwIDSAAwRQIgWhQrK6xEp672PZyOV1GEtRMA\n" +
            "yWV8NrB2sMCZZVbEjgUCIQDDwJO3peBj9+9ZfyjRT39uSKe3Z/A/1yjdDI+pLkp7\n" +
            "VA==\n" +
            "-----END CERTIFICATE-----\n";

    public static final String PRIVATE_KEY = "-----BEGIN PRIVATE KEY-----\n" +
            "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQg1SOlhUhH8XviXGq6\n" +
            "jSVEUxYJthoq3YvExvGkQJ/GCXehRANCAAS+ORiz9pQ9BUK2GCsXLB9fplouny1W\n" +
            "jwwFZcePA3RW04RsOBWgkBJg7A+PAuDHU+faWqYcTl4FGlrBpza9mPYZ\n" +
            "-----END PRIVATE KEY-----\n";

    public static final String RSA_CERTIFICATE = "-----BEGIN CERTIFICATE-----\n" +
            "MIIDCTCCAfGgAwIBAgIUfUEc4uyHseYV8Nst9prV31cfY2cwDQYJKoZIhvcNAQEL\n" +
            "BQAwFDESMBAGA1UEAwwJbG9jYWxob3N0MB4XDTI2MDUyNjE4NDMzNloXDTM2MDUy\n" +
            "MzE4NDMzNlowFDESMBAGA1UEAwwJbG9jYWxob3N0MIIBIjANBgkqhkiG9w0BAQEF\n" +
            "AAOCAQ8AMIIBCgKCAQEA0RswvBW5n/tyB5NjVcn7ukHrj5P4KFiZ+7/z3H1q52ew\n" +
            "wPl3X4w6QVUmUVwIUzZgdsuvwu878OGnULZz5FaPD6N6ODkiWcmmF2tFvZD7WZA2\n" +
            "NO+flAysifxIk+SWvqRsVExaW7DuFJV0+H2+h/umBVrZ/Vo0UBrTcvMu2zqco/EX\n" +
            "G7otPjDicwuURkI0fWP4PpZnSwEB73PoLNzumScAauFgDpZlcbon8S19brw9gi1F\n" +
            "NCxPtw2/eqgtJYU2fhQrzODyfBV3jfIL1IoMKFJGFWwu068Z1hgkDybdLlRB3qIX\n" +
            "kRy7R6DTFLlCyn9raVO7pk8PPWMtVvzrPwQOIdEmzwIDAQABo1MwUTAdBgNVHQ4E\n" +
            "FgQUGftjSpMNn62PO2p/IfJJGhhVhUUwHwYDVR0jBBgwFoAUGftjSpMNn62PO2p/\n" +
            "IfJJGhhVhUUwDwYDVR0TAQH/BAUwAwEB/zANBgkqhkiG9w0BAQsFAAOCAQEALo2r\n" +
            "SC75WTPbPCfK/YP5Njj+nkGFcR7jGrO5VJs8luiKUJZqxeUE0Dk6l8qV3MyheGUy\n" +
            "+Fz2ASjFCEIheqm+7TGfBZiHPuXY24f43QvLT/XmRi7fQHDXp7YhMLwHUYWq8EGG\n" +
            "F2B8/XFSbPIyNJpUw6xMSsdfUp4bDkf/VKfLdwlj9ZEPF2gFL0tst0lBnaawdThE\n" +
            "0TmqT6WPaBV7ePxTKxCXBQ829K21e64dcLY0fjRMihvZAtQKugZLTP3fQAfYwUPL\n" +
            "UNXHHmE67b4gcM8JxJ8zM5QQQ28AmqJutVeZja2QhlEgkjS8UouJiq4apSyhPnRy\n" +
            "G8Js8R7Mb+mrSjWS7g==\n" +
            "-----END CERTIFICATE-----\n";

    public static final String RSA_PRIVATE_KEY = "-----BEGIN RSA PRIVATE KEY-----\n" +
            "MIIEowIBAAKCAQEA0RswvBW5n/tyB5NjVcn7ukHrj5P4KFiZ+7/z3H1q52ewwPl3\n" +
            "X4w6QVUmUVwIUzZgdsuvwu878OGnULZz5FaPD6N6ODkiWcmmF2tFvZD7WZA2NO+f\n" +
            "lAysifxIk+SWvqRsVExaW7DuFJV0+H2+h/umBVrZ/Vo0UBrTcvMu2zqco/EXG7ot\n" +
            "PjDicwuURkI0fWP4PpZnSwEB73PoLNzumScAauFgDpZlcbon8S19brw9gi1FNCxP\n" +
            "tw2/eqgtJYU2fhQrzODyfBV3jfIL1IoMKFJGFWwu068Z1hgkDybdLlRB3qIXkRy7\n" +
            "R6DTFLlCyn9raVO7pk8PPWMtVvzrPwQOIdEmzwIDAQABAoIBAAfgs1xSoTSiv3AD\n" +
            "oHlp657fvuUg2PeEJwDyAVjsLKvdHy6V92ZVHRi7AX+NLQ8dfFLdZ5i7dJGlnq0O\n" +
            "wpz2mdsn+IHHvUCOtUAqnWz/2khMg45I/MUSGSn1pDJWKUuzXBVs7vaHWuDRpJ97\n" +
            "9UKgO2f2PUIrNM9Tw2WQPdKqiZ4vdJLF+ujziXXgmzdPBU9cpJJrDVoVlPqq5EjT\n" +
            "w2h1KGs50LNnsjL3ZVKkFCDl8MJ+MqCT7N/xVZxt/X9LJB8NOwwESPEbOcViXPCo\n" +
            "UO6EU8VdZbsa6ZJOe4T1xltL6aZurLI2BlzTCGad+wZUEZ1svoz9c82HniQJAR3B\n" +
            "0daIxQECgYEA76jb7zyNEtGG9J2wlOxHzV4k7ZVC2NDCFQQjeMC0bfpqZhn5YBT3\n" +
            "/vAAC3c+oBiTP8x0wT2/3qt75lWw8GOqOpCJDJljh0Btsl1dC27YcJHFa4HUuUSF\n" +
            "W1oiFJuBAM6Qw31OceDeQd+2qYgAKpoE0Q5WNDe8/zlCv3JAkRDV+4ECgYEA310J\n" +
            "aIZPrePet4PW7MXkFJP2UhZ9CheNv0fTQPFgiyQ7QtLf1ejGsT5qBYOQGz30UaMs\n" +
            "4b9FNv8d+b5yjxPy5qJV7qiCkSjm68OQyoOi3R2A6GMaEuKmZg5dgXAQBP4zOGe3\n" +
            "apiyDs6HdQqZzXXKTg5G884HrcoJrq0K3cS1ik8CgYAXpD+19PIxtgurG9csibZ3\n" +
            "kt7vtPa4LrfGnPbm1ZO2+an/UnagPNFOC9zlRKkf3+y+sWufGHlR/Pam/TMMM7i6\n" +
            "OEHcxVDlKbzoiH9CPngJesfP2Cnk8NZ68YRFJiXur3HmZvkTEMy8ENaTGMBzc2gg\n" +
            "O1rTod/6CWsFOb8W0GNbgQKBgQCnzmnHEUDDTzJ/hJjvgSx9GTwxJ5wA/PkM/5UC\n" +
            "cVUqs6mh5nMrbZn4hv937QNStBTdUE3ju6LwUnHesum1/DK8erE8f3S4QZZbAa3B\n" +
            "t2uf2w6BiBKKpaSFf71YBSzD04X/lkwB1X3QCfFSSQiLhzbZtXgwqlSyh9sGVBKZ\n" +
            "0bLhmQKBgBrYH0nF8fvL3yTA9e+Fya0lL0FZDxktAm7GpZRQEuw7UraUOoftXZTt\n" +
            "SjTrai4uiIgYChJvDTvR+iW64Q+runffnKwZcsBvupYRiHuPPwmXripKvLpJ8B+E\n" +
            "5JrFc2dCX7GX/rydY3KAfieR0U00D0imfch1N7owADIIYbYTfY4h\n" +
            "-----END RSA PRIVATE KEY-----\n";

    public static final String EC_CERTIFICATE = "-----BEGIN CERTIFICATE-----\n" +
            "MIIBfTCCASOgAwIBAgIUYPRKuwOewP16JMqsD6J7I/T3114wCgYIKoZIzj0EAwIw\n" +
            "FDESMBAGA1UEAwwJbG9jYWxob3N0MB4XDTI2MDUyNjE4NDM0N1oXDTM2MDUyMzE4\n" +
            "NDM0N1owFDESMBAGA1UEAwwJbG9jYWxob3N0MFkwEwYHKoZIzj0CAQYIKoZIzj0D\n" +
            "AQcDQgAEaLTcfMJK7zSroPAhZozlMH/AhqzGVgK6nSve3xVMgEZH/5dSEWl98g7e\n" +
            "6v3pbR2e/+dhqLVylD2PNR8hTJHq+qNTMFEwHQYDVR0OBBYEFNLM3F5ZU5rD/rne\n" +
            "SQ5gVWoUy6sRMB8GA1UdIwQYMBaAFNLM3F5ZU5rD/rneSQ5gVWoUy6sRMA8GA1Ud\n" +
            "EwEB/wQFMAMBAf8wCgYIKoZIzj0EAwIDSAAwRQIgdqxrPRDBn6DMWJ6vAj+kHBhD\n" +
            "RUGgcfJVsMSFbVmWb00CIQDH5g02bIZ8oEiAJAASxK8a7CxKgRW3hnaud/zR6YQO\n" +
            "KQ==\n" +
            "-----END CERTIFICATE-----\n";

    public static final String EC_PRIVATE_KEY = "-----BEGIN EC PRIVATE KEY-----\n" +
            "MHcCAQEEIMe8Z38Xj9m0xY+6CZW+2AQP2lu+teCpa6wmfYPH8HsgoAoGCCqGSM49\n" +
            "AwEHoUQDQgAEaLTcfMJK7zSroPAhZozlMH/AhqzGVgK6nSve3xVMgEZH/5dSEWl9\n" +
            "8g7e6v3pbR2e/+dhqLVylD2PNR8hTJHq+g==\n" +
            "-----END EC PRIVATE KEY-----\n";

    private SslTestUtils() {
    }

    public static SSLContext trustingClientSslContext() throws Exception {
        Certificate certificate = CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(CERTIFICATE.getBytes()));
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("metrics-reporter-test", certificate);

        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagerFactory.getTrustManagers(), new SecureRandom());
        return sslContext;
    }
}
