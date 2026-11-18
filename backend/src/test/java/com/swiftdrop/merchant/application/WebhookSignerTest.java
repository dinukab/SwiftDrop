package com.swiftdrop.merchant.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WebhookSignerTest {

    /**
     * Reference value computed with Node.js using the store's own formula (signature.ts signV1):
     * HMAC-SHA256 keyed with the secret, over "timestamp.rawBody". If this ever differs, the store
     * would reject every webhook.
     */
    @Test
    void matchesTheStoreSignatureFormulaExactly() {
        String body = "{\"eventId\":\"e\",\"data\":{\"newStatus\":\"PAUSED\"}}";

        String signature = WebhookSigner.sign("whsec-test-1234567890", "1763200000", body);

        assertThat(signature).isEqualTo("v1=d0b46272b9fc356fd0fab061dfec0bc038fed71ef0c713822668362a47d03a19");
    }

    @Test
    void theSignatureChangesWithTheTimestampOrTheBody() {
        String base = WebhookSigner.sign("secret-123456789012", "1763200000", "{}");

        assertThat(WebhookSigner.sign("secret-123456789012", "1763200001", "{}")).isNotEqualTo(base);
        assertThat(WebhookSigner.sign("secret-123456789012", "1763200000", "{ }")).isNotEqualTo(base);
        assertThat(WebhookSigner.sign("another-secret-1234", "1763200000", "{}")).isNotEqualTo(base);
    }
}
