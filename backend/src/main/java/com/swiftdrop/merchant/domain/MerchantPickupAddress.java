package com.swiftdrop.merchant.domain;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * The merchant's one pickup (warehouse) address: where couriers collect its parcels. Embedded in {@link Merchant}
 * with {@code pickup_} columns. The same limits and phone format as a parcel address (delivery's
 * {@code ParcelAddress}), so a registered address is always a valid order pickup. Contains personal data; error
 * messages name the field, never the value.
 *
 * <p>The zone is a pricing zone; its existence is guaranteed by a foreign key, not by a call into pricing
 * (ADR 0003: merchant may not depend on pricing).
 */
@Embeddable
public class MerchantPickupAddress {

    /** E.164, for example {@code +94771234567}; the same rule delivery applies to order addresses. */
    static final Pattern PHONE = Pattern.compile("^\\+[1-9][0-9]{7,14}$");
    static final int MAX_NAME_LENGTH = 200;
    static final int MAX_PHONE_LENGTH = 20;
    static final int MAX_LINE_LENGTH = 200;
    static final int MAX_CITY_LENGTH = 100;
    static final int MAX_POSTAL_CODE_LENGTH = 10;

    @Column(name = "pickup_contact_name", length = 200)
    private String contactName;

    @Column(name = "pickup_phone", length = 20)
    private String phone;

    @Column(name = "pickup_line1", length = 200)
    private String line1;

    @Column(name = "pickup_line2", length = 200)
    private String line2;

    @Column(name = "pickup_city", length = 100)
    private String city;

    @Column(name = "pickup_postal_code", length = 10)
    private String postalCode;

    @Column(name = "pickup_zone_id")
    private UUID zoneId;

    protected MerchantPickupAddress() {
        // for JPA
    }

    /**
     * @throws IllegalArgumentException if a required part is blank, a part is too long or the phone is malformed
     */
    public MerchantPickupAddress(String contactName, String phone, String line1, String line2, String city,
            String postalCode, UUID zoneId) {
        this.contactName = requireText(contactName, "contactName", MAX_NAME_LENGTH);
        this.phone = requireText(phone, "phone", MAX_PHONE_LENGTH);
        if (!PHONE.matcher(this.phone).matches()) {
            throw new IllegalArgumentException("phone must be in international format, for example +94771234567");
        }
        this.line1 = requireText(line1, "line1", MAX_LINE_LENGTH);
        this.line2 = optionalText(line2, "line2", MAX_LINE_LENGTH);
        this.city = requireText(city, "city", MAX_CITY_LENGTH);
        this.postalCode = optionalText(postalCode, "postalCode", MAX_POSTAL_CODE_LENGTH);
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId");
    }

    private static String requireText(String value, String name, int maxLength) {
        Objects.requireNonNull(value, name);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return checkLength(trimmed, name, maxLength);
    }

    private static String optionalText(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return checkLength(value.trim(), name, maxLength);
    }

    private static String checkLength(String value, String name, int maxLength) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(name + " must be at most " + maxLength + " characters");
        }
        return value;
    }

    public String getContactName() {
        return contactName;
    }

    public String getPhone() {
        return phone;
    }

    public String getLine1() {
        return line1;
    }

    public String getLine2() {
        return line2;
    }

    public String getCity() {
        return city;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public UUID getZoneId() {
        return zoneId;
    }

    /** Never includes personal data. */
    @Override
    public String toString() {
        return "MerchantPickupAddress[zoneId=" + zoneId + ", ***]";
    }
}
