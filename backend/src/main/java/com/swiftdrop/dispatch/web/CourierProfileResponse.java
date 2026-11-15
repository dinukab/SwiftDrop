package com.swiftdrop.dispatch.web;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.swiftdrop.dispatch.domain.CourierProfile;
import com.swiftdrop.dispatch.domain.VehicleType;
import com.swiftdrop.identity.api.UserSummary;

/**
 * A courier profile with the courier's name and email from {@code identity}. Returned only to
 * administrators, so it includes the phone number.
 */
record CourierProfileResponse(
        UUID id,
        UUID userId,
        String fullName,
        String email,
        boolean accountEnabled,
        String phone,
        VehicleType vehicleType,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {

    /** {@code user} is empty only if the account was removed; the profile is still shown. */
    static CourierProfileResponse from(CourierProfile profile, Optional<UserSummary> user) {
        return new CourierProfileResponse(
                profile.getId(),
                profile.getUserId(),
                user.map(UserSummary::fullName).orElse(null),
                user.map(UserSummary::email).orElse(null),
                user.map(UserSummary::enabled).orElse(false),
                profile.getPhone(),
                profile.getVehicleType(),
                profile.isActive(),
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }
}
