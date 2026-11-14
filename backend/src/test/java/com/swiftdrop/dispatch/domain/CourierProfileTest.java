package com.swiftdrop.dispatch.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class CourierProfileTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final Instant CREATED_AT = Instant.parse("2026-11-11T10:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-11-11T10:30:00Z");

    @Test
    void createsActiveProfileWithTrimmedPhoneAndMatchingTimestamps() {
        CourierProfile profile = new CourierProfile(USER_ID, " +94771234567 ", VehicleType.BIKE, CREATED_AT);

        assertThat(profile.getUserId()).isEqualTo(USER_ID);
        assertThat(profile.getPhone()).isEqualTo("+94771234567");
        assertThat(profile.getVehicleType()).isEqualTo(VehicleType.BIKE);
        assertThat(profile.isActive()).isTrue();
        assertThat(profile.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(profile.getUpdatedAt()).isEqualTo(CREATED_AT);
        assertThat(profile.toString()).doesNotContain("+94771234567");
    }

    @Test
    void rejectsNullRequiredArguments() {
        assertThatThrownBy(() -> new CourierProfile(null, "+94771234567", VehicleType.CAR, CREATED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CourierProfile(USER_ID, null, VehicleType.CAR, CREATED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CourierProfile(USER_ID, "+94771234567", null, CREATED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CourierProfile(USER_ID, "+94771234567", VehicleType.CAR, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsInvalidPhoneFormatsWithoutEchoingPhoneValue() {
        assertThatThrownBy(() -> new CourierProfile(USER_ID, "   ", VehicleType.VAN, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("phone must not be blank");

        String badLocalPhone = "0771234567";
        assertThatThrownBy(() -> new CourierProfile(USER_ID, badLocalPhone, VehicleType.VAN, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining(badLocalPhone);

        String tooLongPhone = "+94771234567890123456";
        assertThatThrownBy(() -> new CourierProfile(USER_ID, tooLongPhone, VehicleType.VAN, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining(tooLongPhone);
    }

    @Test
    void updatesDetailsAndActiveStateWhilePreservingCreatedAt() {
        CourierProfile profile = new CourierProfile(USER_ID, "+94771234567", VehicleType.BIKE, CREATED_AT);

        profile.updateDetails("+94711234567", VehicleType.VAN, UPDATED_AT);
        assertThat(profile.getPhone()).isEqualTo("+94711234567");
        assertThat(profile.getVehicleType()).isEqualTo(VehicleType.VAN);
        assertThat(profile.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(profile.getUpdatedAt()).isEqualTo(UPDATED_AT);

        Instant deactivatedAt = UPDATED_AT.plusSeconds(60);
        profile.deactivate(deactivatedAt);
        assertThat(profile.isActive()).isFalse();
        assertThat(profile.getUpdatedAt()).isEqualTo(deactivatedAt);

        Instant reactivatedAt = deactivatedAt.plusSeconds(60);
        profile.activate(reactivatedAt);
        assertThat(profile.isActive()).isTrue();
        assertThat(profile.getUpdatedAt()).isEqualTo(reactivatedAt);
    }
}

