package com.swiftdrop.merchant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class WebhookSecretCipherTest {

    private static final String KEY_1 = Base64.getEncoder().encodeToString(bytes(1));
    private static final String KEY_2 = Base64.getEncoder().encodeToString(bytes(2));
    private static final UUID MERCHANT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    @Test
    void encryptedSecretDecryptsBackToTheSameText() {
        WebhookSecretCipher cipher = new WebhookSecretCipher("k1=" + KEY_1, "k1");

        String stored = cipher.encrypt("super-secret-key-12345", MERCHANT);

        assertThat(stored).startsWith("v1:k1:").doesNotContain("super-secret-key-12345");
        assertThat(cipher.decrypt(stored, MERCHANT)).isEqualTo("super-secret-key-12345");
    }

    @Test
    void theSameSecretIsStoredDifferentlyEachTime() {
        WebhookSecretCipher cipher = new WebhookSecretCipher("k1=" + KEY_1, "k1");

        assertThat(cipher.encrypt("same-secret-123456", MERCHANT))
                .isNotEqualTo(cipher.encrypt("same-secret-123456", MERCHANT));
    }

    @Test
    void aSecretMovedToAnotherMerchantDoesNotDecrypt() {
        WebhookSecretCipher cipher = new WebhookSecretCipher("k1=" + KEY_1, "k1");
        String stored = cipher.encrypt("super-secret-key-12345", MERCHANT);

        assertThatThrownBy(() -> cipher.decrypt(stored, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Stored webhook secret cannot be read");
    }

    @Test
    void tamperedCiphertextDoesNotDecrypt() {
        WebhookSecretCipher cipher = new WebhookSecretCipher("k1=" + KEY_1, "k1");
        String[] parts = cipher.encrypt("super-secret-key-12345", MERCHANT).split(":");
        byte[] ciphertext = Base64.getDecoder().decode(parts[3]);
        ciphertext[0] ^= 1;
        String tampered = parts[0] + ":" + parts[1] + ":" + parts[2] + ":" + Base64.getEncoder().encodeToString(ciphertext);

        assertThatThrownBy(() -> cipher.decrypt(tampered, MERCHANT)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void legacyBase64SecretsAreNotReadAsEncryptedOnes() {
        WebhookSecretCipher cipher = new WebhookSecretCipher("k1=" + KEY_1, "k1");
        String legacy = Base64.getEncoder().encodeToString("old-secret-123456".getBytes());

        assertThatThrownBy(() -> cipher.decrypt(legacy, MERCHANT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Stored webhook secret cannot be read");
    }

    @Test
    void oldKeysStayReadableAfterRotation() {
        String stored = new WebhookSecretCipher("k1=" + KEY_1, "k1").encrypt("rotated-secret-1234", MERCHANT);

        WebhookSecretCipher rotated = new WebhookSecretCipher("k1=" + KEY_1 + ",k2=" + KEY_2, "k2");

        assertThat(rotated.decrypt(stored, MERCHANT)).isEqualTo("rotated-secret-1234");
        assertThat(rotated.encrypt("new-secret-123456", MERCHANT)).startsWith("v1:k2:");
    }

    @Test
    void encryptionRefusesToRunWithoutAKey() {
        WebhookSecretCipher none = new WebhookSecretCipher("", "");

        assertThat(none.isConfigured()).isFalse();
        assertThatThrownBy(() -> none.encrypt("secret-1234567890", MERCHANT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Webhook encryption is not configured");
    }

    @Test
    void startupFailsOnAKeyThatIsNotAes256() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new WebhookSecretCipher("k1=" + shortKey, "k1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void startupFailsWhenTheActiveKeyIsMissing() {
        assertThatThrownBy(() -> new WebhookSecretCipher("k1=" + KEY_1, "k9"))
                .isInstanceOf(IllegalStateException.class);
    }

    private static byte[] bytes(int seed) {
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) (seed * 31 + i);
        }
        return key;
    }
}
