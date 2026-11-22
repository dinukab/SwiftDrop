package com.swiftdrop.delivery.domain;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

import com.swiftdrop.delivery.api.DeliveryContact;
import com.swiftdrop.delivery.api.NewMerchantParcel;

/**
 * Fingerprint of a merchant order: a SHA-256 of its fields, with the order reference left out (the
 * reference is the identity; the fingerprint says whether the same order was sent again). Fields are
 * joined with a separator that cannot appear in a field, so different orders cannot share one text.
 * Only the digest is stored, never the fields.
 */
public final class OrderFingerprint {

    private static final char SEPARATOR = '\u001F'; // unit separator, not valid in the order fields

    private OrderFingerprint() {
    }

    public static String of(NewMerchantParcel order) {
        Objects.requireNonNull(order, "order");
        StringBuilder text = new StringBuilder();
        appendContact(text, order.pickup());
        appendContact(text, order.dropoff());
        text.append(order.size() == null ? "" : order.size().name()).append(SEPARATOR);
        text.append(order.weightGrams()).append(SEPARATOR);
        text.append(order.description() == null ? "" : order.description().trim());
        return sha256Hex(text.toString());
    }

    private static void appendContact(StringBuilder text, DeliveryContact contact) {
        if (contact == null) {
            text.append(SEPARATOR).append(SEPARATOR).append(SEPARATOR).append(SEPARATOR)
                    .append(SEPARATOR).append(SEPARATOR).append(SEPARATOR);
            return;
        }
        text.append(trim(contact.contactName())).append(SEPARATOR)
                .append(trim(contact.phone())).append(SEPARATOR)
                .append(trim(contact.line1())).append(SEPARATOR)
                .append(trim(contact.line2())).append(SEPARATOR)
                .append(trim(contact.city())).append(SEPARATOR)
                .append(contact.zoneId() == null ? "" : contact.zoneId()).append(SEPARATOR)
                .append(trim(contact.postalCode())).append(SEPARATOR);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    static String sha256Hex(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
