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

    public static final String RSA_PRIVATE_KEY = "-----BEGIN PRIVATE KEY-----\n" +
            "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDRGzC8Fbmf+3IH\n" +
            "k2NVyfu6QeuPk/goWJn7v/PcfWrnZ7DA+XdfjDpBVSZRXAhTNmB2y6/C7zvw4adQ\n" +
            "tnPkVo8Po3o4OSJZyaYXa0W9kPtZkDY075+UDKyJ/EiT5Ja+pGxUTFpbsO4UlXT4\n" +
            "fb6H+6YFWtn9WjRQGtNy8y7bOpyj8Rcbui0+MOJzC5RGQjR9Y/g+lmdLAQHvc+gs\n" +
            "3O6ZJwBq4WAOlmVxuifxLX1uvD2CLUU0LE+3Db96qC0lhTZ+FCvM4PJ8FXeN8gvU\n" +
            "igwoUkYVbC7TrxnWGCQPJt0uVEHeoheRHLtHoNMUuULKf2tpU7umTw89Yy1W/Os/\n" +
            "BA4h0SbPAgMBAAECggEAB+CzXFKhNKK/cAOgeWnrnt++5SDY94QnAPIBWOwsq90f\n" +
            "LpX3ZlUdGLsBf40tDx18Ut1nmLt0kaWerQ7CnPaZ2yf4gce9QI61QCqdbP/aSEyD\n" +
            "jkj8xRIZKfWkMlYpS7NcFWzu9oda4NGkn3v1QqA7Z/Y9Qis0z1PDZZA90qqJni90\n" +
            "ksX66POJdeCbN08FT1ykkmsNWhWU+qrkSNPDaHUoaznQs2eyMvdlUqQUIOXwwn4y\n" +
            "oJPs3/FVnG39f0skHw07DARI8Rs5xWJc8KhQ7oRTxV1luxrpkk57hPXGW0vppm6s\n" +
            "sjYGXNMIZp37BlQRnWy+jP1zzYeeJAkBHcHR1ojFAQKBgQDvqNvvPI0S0Yb0nbCU\n" +
            "7EfNXiTtlULY0MIVBCN4wLRt+mpmGflgFPf+8AALdz6gGJM/zHTBPb/eq3vmVbDw\n" +
            "Y6o6kIkMmWOHQG2yXV0LbthwkcVrgdS5RIVbWiIUm4EAzpDDfU5x4N5B37apiAAq\n" +
            "mgTRDlY0N7z/OUK/ckCRENX7gQKBgQDfXQlohk+t4963g9bsxeQUk/ZSFn0KF42/\n" +
            "R9NA8WCLJDtC0t/V6MaxPmoFg5AbPfRRoyzhv0U2/x35vnKPE/LmolXuqIKRKObr\n" +
            "w5DKg6LdHYDoYxoS4qZmDl2BcBAE/jM4Z7dqmLIOzod1CpnNdcpODkbzzgetygmu\n" +
            "rQrdxLWKTwKBgBekP7X08jG2C6sb1yyJtneS3u+09rgut8ac9ubVk7b5qf9SdqA8\n" +
            "0U4L3OVEqR/f7L6xa58YeVH89qb9MwwzuLo4QdzFUOUpvOiIf0I+eAl6x8/YKeTw\n" +
            "1nrxhEUmJe6vceZm+RMQzLwQ1pMYwHNzaCA7WtOh3/oJawU5vxbQY1uBAoGBAKfO\n" +
            "accRQMNPMn+EmO+BLH0ZPDEnnAD8+Qz/lQJxVSqzqaHmcyttmfiG/3ftA1K0FN1Q\n" +
            "TeO7ovBScd6y6bX8Mrx6sTx/dLhBllsBrcG3a5/bDoGIEoqlpIV/vVgFLMPThf+W\n" +
            "TAHVfdAJ8VJJCIuHNtm1eDCqVLKH2wZUEpnRsuGZAoGAGtgfScXx+8vfJMD174XJ\n" +
            "rSUvQVkPGS0CbsallFAS7DtStpQ6h+1dlO1KNOtqLi6IiBgKEm8NO9H6JbrhD6u6\n" +
            "d9+crBlywG+6lhGIe48/CZeuKkq8uknwH4TkmsVzZ0JfsZf+vJ1jcoB+J5HRTTQP\n" +
            "SKZ9yHU3ujAAMghhthN9jiE=\n" +
            "-----END PRIVATE KEY-----\n";

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
