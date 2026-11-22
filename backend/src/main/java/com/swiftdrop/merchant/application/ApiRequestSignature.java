package com.swiftdrop.merchant.application;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Request signatures for the store-facing API (ADR 0004 section 7; Phase 5). The store signs each
 * request with its key's signing secret:
 *
 * <pre>
 * X-SwiftDrop-Timestamp: 1763200000                       (Unix seconds)
 * X-SwiftDrop-Signature: v1=hex(HMAC-SHA256(secret, timestamp + "." + METHOD + "." + path + "." + sha256hex(body)))
 * </pre>
 *
 * <p>Binding the method, path and body digest means a signature cannot be moved to another request. The
 * timestamp must be within {@link #TOLERANCE} of now. A captured request can be sent again within that
 * window; that is harmless for orders, because a repeated order reference returns the same parcel.
 */
public final class ApiRequestSignature {

    public static final String TIMESTAMP_HEADER = "X-SwiftDrop-Timestamp";
    public static final String SIGNATURE_HEADER = "X-SwiftDrop-Signature";
    public static final Duration TOLERANCE = Duration.ofMinutes(5);
    static final String PREFIX = "v1=";

    private ApiRequestSignature() {
    }

    public static String sign(String secret, String timestamp, String method, String path, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(signedText(timestamp, method, path, body).getBytes(UTF_8));
            return PREFIX + HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }

    /**
     * Whether {@code signatureHeader} is the valid signature of this request, sent within the tolerance of
     * {@code now}. Compared in constant time.
     */
    public static boolean verify(String secret, String timestamp, String signatureHeader, String method,
            String path, byte[] body, Instant now) {
        if (secret == null || timestamp == null || signatureHeader == null || !timestamp.matches("\\d{9,11}")) {
            return false;
        }
        long sentAt = Long.parseLong(timestamp);
        if (Math.abs(now.getEpochSecond() - sentAt) > TOLERANCE.getSeconds()) {
            return false;
        }
        String expected = sign(secret, timestamp, method, path, body);
        return MessageDigest.isEqual(expected.getBytes(UTF_8), signatureHeader.trim().getBytes(UTF_8));
    }

    /** The exact text that is signed. Public so the store's implementation can be checked against it. */
    public static String signedText(String timestamp, String method, String path, byte[] body) {
        return timestamp + "." + method + "." + path + "." + sha256Hex(body);
    }

    static String sha256Hex(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
