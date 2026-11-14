package com.swiftdrop.dispatch.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    @ValueSource(strings = {"CUSTOMER", "MERCHANT", "DISPATCHER", "ADMIN"})
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

    @Test
    void listsAllProfilesWithStableSortWhenNoFilter() {
        Page<CourierProfile> result = new PageImpl<>(List.of());
        when(profiles.findAll(any(Pageable.class))).thenReturn(result);

        assertThat(service.listProfiles(null, 2, 10)).isSameAs(result);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(profiles).findAll(pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by("createdAt", "id"));
        verify(profiles, never()).findByActive(anyBoolean(), any());
    }

    @Test
    void filtersByActiveFlagAndClampsPaging() {
        Page<CourierProfile> result = new PageImpl<>(List.of());
        when(profiles.findByActive(eq(true), any(Pageable.class))).thenReturn(result);

        assertThat(service.listProfiles(true, -5, 1000)).isSameAs(result);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(profiles).findByActive(eq(true), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        verify(profiles, never()).findAll(any(Pageable.class));
    }

    @Test
    void getProfileThrowsWhenMissing() {
        when(profiles.findById(PROFILE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProfile(PROFILE_ID))
                .isInstanceOf(CourierProfileNotFoundException.class);
    }

    @Test
    void updatesPhoneAndVehicleType() {
        CourierProfile existing = new CourierProfile(USER_ID, "+94771234567", VehicleType.BIKE, NOW.minusSeconds(60));
        when(profiles.findById(PROFILE_ID)).thenReturn(Optional.of(existing));

        CourierProfile updated = service.updateDetails(PROFILE_ID, " +94770000000 ", VehicleType.VAN);

        assertThat(updated.getPhone()).isEqualTo("+94770000000");
        assertThat(updated.getVehicleType()).isEqualTo(VehicleType.VAN);
        assertThat(updated.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsInvalidPhoneOnUpdateWithoutChangingProfile() {
        CourierProfile existing = new CourierProfile(USER_ID, "+94771234567", VehicleType.BIKE, NOW.minusSeconds(60));
        when(profiles.findById(PROFILE_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.updateDetails(PROFILE_ID, "0771234567", VehicleType.VAN))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(existing.getPhone()).isEqualTo("+94771234567");
        assertThat(existing.getVehicleType()).isEqualTo(VehicleType.BIKE);
    }

    @Test
    void deactivatesAndReactivatesProfile() {
        CourierProfile existing = new CourierProfile(USER_ID, "+94771234567", VehicleType.BIKE, NOW.minusSeconds(60));
        when(profiles.findById(PROFILE_ID)).thenReturn(Optional.of(existing));

        assertThat(service.setActive(PROFILE_ID, false).isActive()).isFalse();
        assertThat(service.setActive(PROFILE_ID, true).isActive()).isTrue();
        assertThat(existing.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void partialUpdateKeepsOmittedFields() {
        CourierProfile existing = new CourierProfile(USER_ID, "+94771234567", VehicleType.BIKE, NOW.minusSeconds(60));
        when(profiles.findById(PROFILE_ID)).thenReturn(Optional.of(existing));

        CourierProfile updated = service.updateProfile(PROFILE_ID, null, VehicleType.CAR, false);

        assertThat(updated.getPhone()).isEqualTo("+94771234567");
        assertThat(updated.getVehicleType()).isEqualTo(VehicleType.CAR);
        assertThat(updated.isActive()).isFalse();
        assertThat(updated.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void setActiveThrowsWhenProfileMissing() {
        when(profiles.findById(PROFILE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setActive(PROFILE_ID, false))
                .isInstanceOf(CourierProfileNotFoundException.class);
    }
}

