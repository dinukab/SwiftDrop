package com.swiftdrop.merchant.infrastructure.webhook;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Secrets are never included in exception messages or object string representations. */
public final class WebhookSecrets {
    private final Map<String, byte[]> keys = new HashMap<>();
    private final String active;
    private final SecureRandom random = new SecureRandom();

    public WebhookSecrets(String active, String entries) {
        this.active = active;
        try {
            for (String entry : entries.split(",")) {
                String[] pair = entry.trim().split(":", 2);
                if (pair.length != 2 || !pair[0].matches("[A-Za-z0-9_-]{1,64}")) {
                    throw new IllegalArgumentException();
                }
                byte[] key = Base64.getDecoder().decode(pair[1]);
                if (key.length != 32 || keys.putIfAbsent(pair[0], key) != null) {
                    throw new IllegalArgumentException();
                }
            }
            if (!keys.containsKey(active)) throw new IllegalArgumentException();
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid webhook encryption key configuration");
        }
    }

    public byte[] generate() {
        byte[] secret = new byte[32];
        random.nextBytes(secret);
        return secret;
    }

    public Encrypted encrypt(UUID endpoint, byte[] secret) {
        byte[] nonce = new byte[12];
        random.nextBytes(nonce);
        return new Encrypted(active, nonce, crypt(Cipher.ENCRYPT_MODE, endpoint, active, nonce, secret));
    }

    public byte[] decrypt(UUID endpoint, Encrypted encrypted) {
        return crypt(Cipher.DECRYPT_MODE, endpoint, encrypted.keyId(), encrypted.nonce(), encrypted.ciphertext());
    }

    private byte[] crypt(int mode, UUID endpoint, String keyId, byte[] nonce, byte[] value) {
        try {
            byte[] key = keys.get(keyId);
            if (key == null || nonce.length != 12) throw new GeneralSecurityException();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD((endpoint + ":" + keyId).getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(value);
        } catch (GeneralSecurityException ex) {
            throw new SecretUnavailable();
        }
    }

    public static String sign(byte[] secret, long timestamp, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return "v1=" + HexFormat.of().formatHex(mac.doFinal(
                    (timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new SecretUnavailable();
        }
    }

    public record Encrypted(String keyId, byte[] nonce, byte[] ciphertext) {
        @Override public String toString() { return "Encrypted[redacted]"; }
    }

    public static final class SecretUnavailable extends RuntimeException {
        public SecretUnavailable() { super("Webhook secret unavailable"); }
    }
}
