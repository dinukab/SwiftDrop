package com.swiftdrop.dispatch.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.swiftdrop.dispatch.domain.CourierProfile;
import com.swiftdrop.dispatch.domain.CourierProfileRepository;
import com.swiftdrop.dispatch.domain.VehicleType;
import com.swiftdrop.identity.api.UserDirectory;
import com.swiftdrop.identity.api.UserSummary;

class CourierEligibilityTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");

    private UserDirectory users;
    private CourierProfileRepository profiles;
    private CourierEligibility eligibility;

    @BeforeEach
    void setUp() {
        users = mock(UserDirectory.class);
        profiles = mock(CourierProfileRepository.class);
        eligibility = new CourierEligibility(users, profiles);
    }

    private static UserSummary user(boolean enabled, String... roles) {
        return new UserSummary(USER_ID, "Kasun Perera", "kasun@swiftdrop.com", Set.of(roles), enabled);
    }

    @Test
    void acceptsEnabledCourierWithActiveProfile() {
        when(users.findUser(USER_ID)).thenReturn(Optional.of(user(true, "COURIER")));
        when(profiles.findByUserId(USER_ID))
                .thenReturn(Optional.of(new CourierProfile(USER_ID, "+94771234567", VehicleType.BIKE, NOW)));

        assertThatCode(() -> eligibility.requireEligible(USER_ID)).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnknownUser() {
        when(users.findUser(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eligibility.requireEligible(USER_ID))
                .isInstanceOf(CourierNotEligibleException.class).hasMessage("Courier not found");
    }

    @Test
    void rejectsDisabledAccount() {
        when(users.findUser(USER_ID)).thenReturn(Optional.of(user(false, "COURIER")));

        assertThatThrownBy(() -> eligibility.requireEligible(USER_ID))
                .isInstanceOf(CourierNotEligibleException.class).hasMessage("Courier account is disabled");
    }

    @Test
    void rejectsUserWithoutCourierRole() {
        when(users.findUser(USER_ID)).thenReturn(Optional.of(user(true, "CUSTOMER")));

        assertThatThrownBy(() -> eligibility.requireEligible(USER_ID))
                .isInstanceOf(CourierNotEligibleException.class).hasMessage("User is not a courier");
    }

    @Test
    void rejectsCourierWithoutProfile() {
        when(users.findUser(USER_ID)).thenReturn(Optional.of(user(true, "COURIER")));
        when(profiles.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eligibility.requireEligible(USER_ID))
                .isInstanceOf(CourierNotEligibleException.class).hasMessage("Courier has no profile");
    }

    @Test
    void rejectsCourierWithInactiveProfile() {
        when(users.findUser(USER_ID)).thenReturn(Optional.of(user(true, "COURIER")));
        when(profiles.findByUserId(USER_ID)).thenReturn(Optional.of(
                new CourierProfile(USER_ID, "+94771234567", VehicleType.BIKE, false, NOW)));

        assertThatThrownBy(() -> eligibility.requireEligible(USER_ID))
                .isInstanceOf(CourierNotEligibleException.class).hasMessage("Courier profile is inactive");
    }
}
