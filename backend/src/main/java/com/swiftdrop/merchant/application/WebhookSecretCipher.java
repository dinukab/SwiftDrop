package com.swiftdrop.merchant.application;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Encrypts merchant webhook secrets with AES-256-GCM (ADR 0004 section 7.4). The sender needs the
 * plain secret to sign each request, so it cannot be hashed; it is encrypted instead.
 *
 * <p>Keys come from configuration, never from the database: {@code SWIFTDROP_WEBHOOK_KEYS} holds
 * {@code keyId=base64key} pairs (each key 32 bytes) and {@code SWIFTDROP_WEBHOOK_ACTIVE_KEY_ID} picks
 * the key used for new secrets. Older keys stay readable, so keys can be rotated.
 *
 * <p>Stored form: {@code v1:<keyId>:<base64 iv>:<base64 ciphertext>}. The merchant id is bound as
 * associated data, so a secret moved to another merchant's endpoint will not decrypt.
 */
@Component
public class WebhookSecretCipher {

    private static final String PREFIX = "v1";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;
    private static final Pattern KEY_ID = Pattern.compile("^[A-Za-z0-9_-]{1,40}$");

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, SecretKeySpec> keys;
    private final String activeKeyId;

    public WebhookSecretCipher(
            @Value("${swiftdrop.webhooks.keys:}") String keySpec,
            @Value("${swiftdrop.webhooks.active-key-id:}") String activeKeyId
    ) {
        this.keys = parse(keySpec);
        this.activeKeyId = activeKeyId == null ? "" : activeKeyId.trim();
        if (!keys.isEmpty() && !keys.containsKey(this.activeKeyId)) {
            throw new IllegalStateException("SWIFTDROP_WEBHOOK_ACTIVE_KEY_ID does not name a key in SWIFTDROP_WEBHOOK_KEYS");
        }
    }

    /** Whether a key is configured, so new secrets can be stored. */
    public boolean isConfigured() {
        return keys.containsKey(activeKeyId);
    }

    public String encrypt(String plaintext, UUID merchantId) {
        if (!isConfigured()) {
            throw new IllegalStateException("Webhook encryption is not configured");
        }
        byte[] iv = new byte[IV_BYTES];
        RANDOM.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keys.get(activeKeyId), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(associatedData(merchantId));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(UTF_8));
            return PREFIX + ":" + activeKeyId + ":" + encode(iv) + ":" + encode(ciphertext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Webhook secret could not be encrypted");
        }
    }

    /**
     * @throws IllegalStateException if the stored value is not a readable secret (for example a
     *         secret stored before encryption existed). The message never includes the value.
     */
    public String decrypt(String stored, UUID merchantId) {
        String[] parts = stored == null ? new String[0] : stored.split(":", -1);
        if (parts.length != 4 || !PREFIX.equals(parts[0]) || !keys.containsKey(parts[1])) {
            throw new IllegalStateException("Stored webhook secret cannot be read");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keys.get(parts[1]),
                    new GCMParameterSpec(TAG_BITS, Base64.getDecoder().decode(parts[2])));
            cipher.updateAAD(associatedData(merchantId));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[3])), UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Stored webhook secret cannot be read");
        }
    }

    static byte[] associatedData(UUID merchantId) {
        return ("webhook-secret:" + merchantId).getBytes(UTF_8);
    }

    private static String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static Map<String, SecretKeySpec> parse(String spec) {
        Map<String, SecretKeySpec> result = new HashMap<>();
        if (spec == null || spec.isBlank()) {
            return result;
        }
        for (String pair : spec.split(",")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                throw new IllegalStateException("SWIFTDROP_WEBHOOK_KEYS must be keyId=base64key pairs");
            }
            String id = pair.substring(0, eq).trim();
            if (!KEY_ID.matcher(id).matches()) {
                throw new IllegalStateException("SWIFTDROP_WEBHOOK_KEYS has an invalid key id");
            }
            byte[] key;
            try {
                key = Base64.getDecoder().decode(pair.substring(eq + 1).trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("SWIFTDROP_WEBHOOK_KEYS holds a key that is not base64");
            }
            if (key.length != KEY_BYTES) {
                throw new IllegalStateException("Each webhook key must be 32 bytes (AES-256)");
            }
            result.put(id, new SecretKeySpec(key, "AES"));
        }
        return result;
    }
}
