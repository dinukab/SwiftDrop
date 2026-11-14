package com.swiftdrop.dispatch.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.dispatch.domain.CourierProfile;
import com.swiftdrop.dispatch.domain.CourierProfileRepository;
import com.swiftdrop.dispatch.domain.VehicleType;
import com.swiftdrop.identity.api.UserDirectory;
import com.swiftdrop.identity.api.UserSummary;

class CourierProfileServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID PROFILE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000p1".replace('p', 'a'));
    private static final Instant NOW = Instant.parse("2026-11-11T15:30:00Z");

    private CourierProfileRepository profiles;
    private UserDirectory userDirectory;
    private CourierProfileService service;

    @BeforeEach
    void setUp() {
        profiles = mock(CourierProfileRepository.class);
        userDirectory = mock(UserDirectory.class);
        service = new CourierProfileService(profiles, userDirectory, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsCourierProfileForEnabledUserWithCourierRole() {
        UserSummary courierUser = new UserSummary(USER_ID, "Kasun Perera", "kasun@swiftdrop.com", Set.of("COURIER"), true);
        when(userDirectory.findUser(USER_ID)).thenReturn(Optional.of(courierUser));
        when(profiles.existsByUserId(USER_ID)).thenReturn(false);
        when(profiles.saveAndFlush(any(CourierProfile.class))).thenAnswer(call -> {
            CourierProfile profile = call.getArgument(0);
            ReflectionTestUtils.setField(profile, "id", PROFILE_ID);
            return profile;
        });

        CourierProfile created = service.createProfile(USER_ID, " +94771234567 ", VehicleType.BIKE);

        assertThat(created.getId()).isEqualTo(PROFILE_ID);
        assertThat(created.getUserId()).isEqualTo(USER_ID);
        assertThat(created.getPhone()).isEqualTo("+94771234567");
        assertThat(created.getVehicleType()).isEqualTo(VehicleType.BIKE);
        assertThat(created.isActive()).isTrue();
        assertThat(created.getCreatedAt()).isEqualTo(NOW);
        assertThat(created.getUpdatedAt()).isEqualTo(NOW);
        verify(profiles).saveAndFlush(any(CourierProfile.class));
    }

    @Test
    void rejectsDuplicateCourierProfileWhenUserAlreadyHasOne() {
        UserSummary courierUser = new UserSummary(USER_ID, "Kasun Perera", "kasun@swiftdrop.com", Set.of("COURIER"), true);
        when(userDirectory.findUser(USER_ID)).thenReturn(Optional.of(courierUser));
        when(profiles.existsByUserId(USER_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.createProfile(USER_ID, "+94771234567", VehicleType.CAR))
                .isInstanceOf(DuplicateCourierProfileException.class);
        verify(profiles, never()).saveAndFlush(any());
    }

    @Test
    void mapsConcurrentUniqueViolationToDuplicateCourierProfileException() {
        UserSummary courierUser = new UserSummary(USER_ID, "Kasun Perera", "kasun@swiftdrop.com", Set.of("COURIER"), true);
        when(userDirectory.findUser(USER_ID)).thenReturn(Optional.of(courierUser));
        when(profiles.existsByUserId(USER_ID)).thenReturn(false);
        when(profiles.saveAndFlush(any(CourierProfile.class)))
                .thenThrow(new DataIntegrityViolationException("uq_couriers_user_id"));

        assertThatThrownBy(() -> service.createProfile(USER_ID, "+94771234567", VehicleType.VAN))
                .isInstanceOf(DuplicateCourierProfileException.class);
    }

    @Test
    void rejectsUnknownUser() {
        when(userDirectory.findUser(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createProfile(USER_ID, "+94771234567", VehicleType.BIKE))
                .isInstanceOf(CourierUserNotFoundException.class);
        verify(profiles, never()).saveAndFlush(any());
    }

    @Test
    void rejectsDisabledUser() {
        UserSummary disabledCourier = new UserSummary(USER_ID, "Kasun Perera", "kasun@swiftdrop.com", Set.of("COURIER"), false);
        when(userDirectory.findUser(USER_ID)).thenReturn(Optional.of(disabledCourier));

        assertThatThrownBy(() -> service.createProfile(USER_ID, "+94771234567", VehicleType.BIKE))
                .isInstanceOf(CourierUserNotEligibleException.class)
                .hasMessage("Courier user account is disabled");
        verify(profiles, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "MERCHANT", "DISPATCHER_ADMIN"})
    void rejectsUsersWithoutCourierRole(String role) {
        UserSummary nonCourier = new UserSummary(USER_ID, "Nimal Silva", "nimal@swiftdrop.com", Set.of(role), true);
        when(userDirectory.findUser(USER_ID)).thenReturn(Optional.of(nonCourier));

        assertThatThrownBy(() -> service.createProfile(USER_ID, "+94771234567", VehicleType.BIKE))
                .isInstanceOf(CourierUserNotEligibleException.class)
                .hasMessage("User does not have the COURIER role");
        verify(profiles, never()).saveAndFlush(any());
    }

    @Test
    void looksUpProfileByUserId() {
        CourierProfile existing = new CourierProfile(USER_ID, "+94771234567", VehicleType.CAR, NOW);
        when(profiles.findByUserId(USER_ID)).thenReturn(Optional.of(existing));

        assertThat(service.findByUserId(USER_ID)).contains(existing);
    }
}

