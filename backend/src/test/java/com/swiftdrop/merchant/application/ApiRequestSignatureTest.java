package com.swiftdrop.merchant.application;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class ApiRequestSignatureTest {

    private static final String SECRET = "sig-secret-test-123456";
    private static final String TS = "1763200000";
    private static final String PATH = "/api/v1/merchant-api/orders";
    private static final byte[] BODY = "{\"a\":1}".getBytes(UTF_8);
    private static final Instant NOW = Instant.ofEpochSecond(1763200000L);

    /**
     * Reference values computed with Node.js from the documented formula: HMAC-SHA256 keyed with the
     * signing secret over "timestamp.METHOD.path.sha256hex(body)". The store must produce the same.
     */
    @Test
    void matchesTheDocumentedFormulaExactly() {
        assertThat(ApiRequestSignature.signedText(TS, "POST", PATH, BODY))
                .isEqualTo("1763200000.POST./api/v1/merchant-api/orders."
                        + "015abd7f5cc57a2dd94b7590f04ad8084273905ee33ec5cebeae62276a97f862");
        assertThat(ApiRequestSignature.sign(SECRET, TS, "POST", PATH, BODY))
                .isEqualTo("v1=a080e6f603b7f531499fe2f8b22d3999c33e6e64cbf646f56d8bb2a06cca2abd");
    }

    @Test
    void aValidSignatureWithinTheToleranceIsAccepted() {
        String signature = ApiRequestSignature.sign(SECRET, TS, "POST", PATH, BODY);

        assertThat(ApiRequestSignature.verify(SECRET, TS, signature, "POST", PATH, BODY, NOW)).isTrue();
        assertThat(ApiRequestSignature.verify(SECRET, TS, signature, "POST", PATH, BODY,
                NOW.plusSeconds(299))).isTrue();
    }

    @Test
    void anySingleChangeToTheRequestBreaksTheSignature() {
        String signature = ApiRequestSignature.sign(SECRET, TS, "POST", PATH, BODY);

        assertThat(ApiRequestSignature.verify(SECRET, TS, signature, "PUT", PATH, BODY, NOW)).as("method").isFalse();
        assertThat(ApiRequestSignature.verify(SECRET, TS, signature, "POST", "/api/v1/merchant-api/other", BODY, NOW))
                .as("path").isFalse();
        assertThat(ApiRequestSignature.verify(SECRET, TS, signature, "POST", PATH,
                "{\"a\":2}".getBytes(UTF_8), NOW)).as("body").isFalse();
        assertThat(ApiRequestSignature.verify("another-secret-12345", TS, signature, "POST", PATH, BODY, NOW))
                .as("secret").isFalse();
    }

    @Test
    void aStaleOrFutureTimestampIsRefused() {
        String signature = ApiRequestSignature.sign(SECRET, TS, "POST", PATH, BODY);

        assertThat(ApiRequestSignature.verify(SECRET, TS, signature, "POST", PATH, BODY, NOW.plusSeconds(301)))
                .isFalse();
        assertThat(ApiRequestSignature.verify(SECRET, TS, signature, "POST", PATH, BODY, NOW.minusSeconds(301)))
                .isFalse();
    }

    @Test
    void missingOrMalformedHeadersAreRefusedWithoutThrowing() {
        String signature = ApiRequestSignature.sign(SECRET, TS, "POST", PATH, BODY);

        assertThat(ApiRequestSignature.verify(SECRET, null, signature, "POST", PATH, BODY, NOW)).isFalse();
        assertThat(ApiRequestSignature.verify(SECRET, TS, null, "POST", PATH, BODY, NOW)).isFalse();
        assertThat(ApiRequestSignature.verify(SECRET, "not-a-number", signature, "POST", PATH, BODY, NOW)).isFalse();
        assertThat(ApiRequestSignature.verify(null, TS, signature, "POST", PATH, BODY, NOW)).isFalse();
    }
}
