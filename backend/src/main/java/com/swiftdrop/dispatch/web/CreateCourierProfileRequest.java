package com.swiftdrop.dispatch.web;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.swiftdrop.dispatch.domain.CourierProfile;
import com.swiftdrop.dispatch.domain.VehicleType;

/** Creates a courier profile for an existing {@code COURIER} user (task 7.1). */
record CreateCourierProfileRequest(
        @NotNull UUID userId,
        @NotBlank @Size(max = CourierProfile.MAX_PHONE_LENGTH) String phone,
        @NotNull VehicleType vehicleType
) {

    /** Never includes the phone number: it is personal data. */
    @Override
    public String toString() {
        return "CreateCourierProfileRequest[userId=" + userId + ", vehicleType=" + vehicleType + ", ***]";
    }
}
