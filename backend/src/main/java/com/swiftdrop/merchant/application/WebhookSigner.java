package com.swiftdrop.merchant.application;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.GeneralSecurityException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signs outgoing webhooks (ADR 0004 section 7.2): {@code v1=} plus the hex HMAC-SHA256 of
 * {@code timestamp + "." + rawBody}, keyed with the endpoint's secret. This is the exact formula the
 * store checks in {@code signature.ts}.
 */
public final class WebhookSigner {

    public static final String PREFIX = "v1=";

    private WebhookSigner() {
    }

    public static String sign(String secret, String timestamp, String rawBody) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((timestamp + "." + rawBody).getBytes(UTF_8));
            StringBuilder hex = new StringBuilder(PREFIX.length() + digest.length * 2).append(PREFIX);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }
}
