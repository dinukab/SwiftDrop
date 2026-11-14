package com.swiftdrop.dispatch.web;

import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.dispatch.domain.CourierProfile;
import com.swiftdrop.dispatch.domain.VehicleType;
import com.swiftdrop.identity.api.UserSummary;

/** Courier profile with the account name and email; both are null if the account is gone. */
record CourierProfileResponse(
        UUID id,
        UUID userId,
        String fullName,
        String email,
        String phone,
        VehicleType vehicleType,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    static CourierProfileResponse from(CourierProfile profile, UserSummary user) {
        return new CourierProfileResponse(
                profile.getId(),
                profile.getUserId(),
                user == null ? null : user.fullName(),
                user == null ? null : user.email(),
                profile.getPhone(),
                profile.getVehicleType(),
                profile.isActive(),
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }
}
