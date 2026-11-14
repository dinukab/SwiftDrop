package com.swiftdrop.dispatch.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Operational profile of a courier linked 1:1 by {@code userId} to an Identity user holding the
 * {@code COURIER} role (ADR 0003, contract §5.4, D-28). Authentication, name, email and roles stay
 * in {@code identity}.
 */
@Entity
@Table(name = "couriers")
public class CourierProfile {

    /** E.164 international format matching {@code ParcelAddress.PHONE}, e.g. {@code +94771234567}. */
    public static final Pattern PHONE = Pattern.compile("^\\+[1-9][0-9]{7,14}$");
    public static final int MAX_PHONE_LENGTH = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    private UUID userId;

    /** Personal data: keep out of logs and {@link #toString()}. */
    @Column(name = "phone", nullable = false, length = MAX_PHONE_LENGTH)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private VehicleType vehicleType;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected CourierProfile() {
        // for JPA
    }

    /** Creates an active courier profile initialized at {@code now}. */
    public CourierProfile(UUID userId, String phone, VehicleType vehicleType, Instant now) {
        this(userId, phone, vehicleType, true, now);
    }

    /** Creates a courier profile with explicit initial active state. */
    public CourierProfile(UUID userId, String phone, VehicleType vehicleType, boolean active, Instant now) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.phone = normalizePhone(phone);
        this.vehicleType = Objects.requireNonNull(vehicleType, "vehicleType must not be null");
        this.active = active;
        Instant timestamp = Objects.requireNonNull(now, "now must not be null");
        this.createdAt = timestamp;
        this.updatedAt = timestamp;
    }

    /** Validates and trims an E.164 phone number without echoing rejected personal data. */
    public static String normalizePhone(String phone) {
        String value = Objects.requireNonNull(phone, "phone must not be null").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("phone must not be blank");
        }
        if (value.length() > MAX_PHONE_LENGTH) {
            throw new IllegalArgumentException("phone must be at most " + MAX_PHONE_LENGTH + " characters");
        }
        if (!PHONE.matcher(value).matches()) {
            throw new IllegalArgumentException("phone must be in international format, for example +94771234567");
        }
        return value;
    }

    /** Updates contact phone and vehicle type at {@code now}. */
    public void updateDetails(String phone, VehicleType vehicleType, Instant now) {
        Instant timestamp = Objects.requireNonNull(now, "now must not be null");
        this.phone = normalizePhone(phone);
        this.vehicleType = Objects.requireNonNull(vehicleType, "vehicleType must not be null");
        this.updatedAt = timestamp;
    }

    /** Marks the courier operationally active for dispatch at {@code now}. */
    public void activate(Instant now) {
        setActive(true, now);
    }

    /** Marks the courier operationally inactive for dispatch at {@code now}. */
    public void deactivate(Instant now) {
        setActive(false, now);
    }

    /** Sets the operational active flag at {@code now}. */
    public void setActive(boolean active, Instant now) {
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getPhone() {
        return phone;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    /** Never includes phone numbers or other personal data. */
    @Override
    public String toString() {
        return "CourierProfile[id=" + id
                + ", userId=" + userId
                + ", vehicleType=" + vehicleType
                + ", active=" + active
                + "]";
    }
}

