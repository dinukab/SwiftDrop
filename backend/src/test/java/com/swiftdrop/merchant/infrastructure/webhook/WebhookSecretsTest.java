package com.swiftdrop.merchant.infrastructure.webhook;

import static org.assertj.core.api.Assertions.*;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WebhookSecretsTest {
    private final String key = Base64.getEncoder().encodeToString(new byte[32]);
    private final WebhookSecrets secrets = new WebhookSecrets("current", "current:" + key);

    @Test void authenticatedEncryptionBindsEndpointAndKeyAndUsesFreshNonce() {
        UUID endpoint = UUID.randomUUID();
        byte[] secret = secrets.generate();
        var first = secrets.encrypt(endpoint, secret);
        var second = secrets.encrypt(endpoint, secret);
        assertThat(first.nonce()).hasSize(12).isNotEqualTo(second.nonce());
        assertThat(first.ciphertext()).hasSize(48).isNotEqualTo(secret);
        assertThat(secrets.decrypt(endpoint, first)).isEqualTo(secret);
        assertThatThrownBy(() -> secrets.decrypt(UUID.randomUUID(), first))
                .isInstanceOf(WebhookSecrets.SecretUnavailable.class);
        first.ciphertext()[0] ^= 1;
        assertThatThrownBy(() -> secrets.decrypt(endpoint, first))
                .isInstanceOf(WebhookSecrets.SecretUnavailable.class);
        assertThat(first.toString()).doesNotContain(key);
    }

    @Test void keyRotationRetainsOldDecryptKeyAndFailsClosedWhenMissing() {
        UUID endpoint = UUID.randomUUID();
        var encrypted = secrets.encrypt(endpoint, secrets.generate());
        var rotated = new WebhookSecrets("next", "current:" + key + ",next:" + key);
        assertThat(rotated.decrypt(endpoint, encrypted)).hasSize(32);
        assertThat(rotated.encrypt(endpoint, secrets.generate()).keyId()).isEqualTo("next");
        var missing = new WebhookSecrets("next", "next:" + key);
        assertThatThrownBy(() -> missing.decrypt(endpoint, encrypted))
                .isInstanceOf(WebhookSecrets.SecretUnavailable.class);
        assertThatThrownBy(() -> new WebhookSecrets("absent", "current:" + key)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WebhookSecrets("current", "current:bad")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void signatureMatchesIndependentKnownVectorAndChangesWithBodyAndTimestamp() throws Exception {
        byte[] secret = "test-only-signing-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(secret, "HmacSHA256"));
        String expected = "v1=" + java.util.HexFormat.of().formatHex(mac.doFinal("123.{}".getBytes()));
        assertThat(WebhookSecrets.sign(secret, 123, "{}")).isEqualTo(expected);
        assertThat(WebhookSecrets.sign(secret, 124, "{}")).isNotEqualTo(expected);
        assertThat(WebhookSecrets.sign(secret, 123, "{ }")).isNotEqualTo(expected);
    }
}
