package com.swiftdrop.dispatch.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

/** Contract §5.4: enabled, COURIER role, and an active profile. Each rule on its own. */
class CourierEligibilityTest {

    private static final UUID COURIER = UUID.fromString("00000000-0000-0000-0000-0000000000d1");

    private UserDirectory users;
    private CourierProfileRepository profiles;
    private CourierEligibility eligibility;

    @BeforeEach
    void setUp() {
        users = mock(UserDirectory.class);
        profiles = mock(CourierProfileRepository.class);
        eligibility = new CourierEligibility(users, profiles);
    }

    @Test
    void anEnabledCourierWithAnActiveProfileIsEligible() {
        user(true, Set.of("COURIER"));
        profile(true);

        assertThatCode(() -> eligibility.requireEligible(COURIER)).doesNotThrowAnyException();
    }

    @Test
    void anUnknownUserIsNotEligible() {
        when(users.findUser(COURIER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eligibility.requireEligible(COURIER))
                .isInstanceOf(CourierNotEligibleException.class)
                .hasMessage("User is not a courier");
    }

    @Test
    void aDisabledAccountIsNotEligibleEvenWithTheRoleAndAProfile() {
        user(false, Set.of("COURIER"));
        profile(true);

        assertThatThrownBy(() -> eligibility.requireEligible(COURIER))
                .isInstanceOf(CourierNotEligibleException.class)
                .hasMessage("Courier account is disabled");
    }

    @Test
    void aUserWithoutTheCourierRoleIsNotEligible() {
        user(true, Set.of("CUSTOMER"));
        profile(true);

        assertThatThrownBy(() -> eligibility.requireEligible(COURIER))
                .isInstanceOf(CourierNotEligibleException.class)
                .hasMessage("User is not a courier");
    }

    @Test
    void aCourierWithoutAProfileIsNotEligible() {
        user(true, Set.of("COURIER"));
        when(profiles.findByUserId(COURIER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eligibility.requireEligible(COURIER))
                .isInstanceOf(CourierNotEligibleException.class)
                .hasMessage("Courier profile is not active");
    }

    @Test
    void aCourierWithAnInactiveProfileIsNotEligible() {
        user(true, Set.of("COURIER"));
        profile(false);

        assertThatThrownBy(() -> eligibility.requireEligible(COURIER))
                .isInstanceOf(CourierNotEligibleException.class)
                .hasMessage("Courier profile is not active");
    }

    @Test
    void theBooleanRuleMatchesRequireEligible() {
        CourierProfile active = new CourierProfile(COURIER, "+94771234567", VehicleType.BIKE, java.time.Instant.EPOCH);
        CourierProfile inactive = new CourierProfile(COURIER, "+94771234567", VehicleType.BIKE, false,
                java.time.Instant.EPOCH);
        UserSummary courier = new UserSummary(COURIER, "Kasun", "k@example.com", Set.of("COURIER"), true);

        assertThat(CourierEligibility.isEligible(courier, active)).isTrue();
        assertThat(CourierEligibility.isEligible(courier, inactive)).isFalse();
        assertThat(CourierEligibility.isEligible(courier, null)).isFalse();
        assertThat(CourierEligibility.isEligible(null, active)).isFalse();
        assertThat(CourierEligibility.isEligible(
                new UserSummary(COURIER, "Kasun", "k@example.com", Set.of("COURIER"), false), active)).isFalse();
        assertThat(CourierEligibility.isEligible(
                new UserSummary(COURIER, "Kasun", "k@example.com", Set.of("CUSTOMER"), true), active)).isFalse();
    }

    @Test
    void aCourierWithNoHubIsNotRestrictedToOne() {
        profile(true);

        assertThatCode(() -> eligibility.requireAtHub(COURIER, UUID.randomUUID())).doesNotThrowAnyException();
    }

    @Test
    void aCourierBasedAtOtherHubsOnlyIsRefusedButHomeOrCurrentHubIsEnough() {
        UUID uva = UUID.randomUUID();
        UUID colombo = UUID.randomUUID();
        UUID southern = UUID.randomUUID();
        CourierProfile based = new CourierProfile(COURIER, "+94771234567", VehicleType.BIKE,
                java.time.Instant.EPOCH);
        based.assignHomeHub(uva, java.time.Instant.EPOCH);
        when(profiles.findByUserId(COURIER)).thenReturn(Optional.of(based));

        assertThatCode(() -> eligibility.requireAtHub(COURIER, uva)).doesNotThrowAnyException();
        assertThatThrownBy(() -> eligibility.requireAtHub(COURIER, colombo))
                .isInstanceOf(CourierNotEligibleException.class).hasMessage("Courier is not based at this hub");

        based.moveToHub(colombo, java.time.Instant.EPOCH);
        assertThatCode(() -> eligibility.requireAtHub(COURIER, colombo)).doesNotThrowAnyException();
        assertThatCode(() -> eligibility.requireAtHub(COURIER, uva)).doesNotThrowAnyException();
        assertThatThrownBy(() -> eligibility.requireAtHub(COURIER, southern))
                .isInstanceOf(CourierNotEligibleException.class);
    }

    private void user(boolean enabled, Set<String> roles) {
        when(users.findUser(COURIER)).thenReturn(Optional.of(
                new UserSummary(COURIER, "Kamal Silva", "kamal@example.com", roles, enabled)));
    }

    private void profile(boolean active) {
        when(profiles.findByUserId(COURIER)).thenReturn(Optional.of(
                new CourierProfile(COURIER, "+94771234567", VehicleType.BIKE, active,
                        java.time.Instant.parse("2026-11-15T10:00:00Z"))));
    }
}
