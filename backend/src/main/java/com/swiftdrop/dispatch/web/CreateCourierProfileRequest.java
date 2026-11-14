package com.swiftdrop.dispatch.web;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.swiftdrop.dispatch.domain.CourierProfile;
import com.swiftdrop.dispatch.domain.VehicleType;

/** Body of {@code POST /api/v1/couriers}; the phone format is checked by the domain. */
record CreateCourierProfileRequest(
        @NotNull UUID userId,
        @NotBlank @Size(max = CourierProfile.MAX_PHONE_LENGTH) String phone,
        @NotNull VehicleType vehicleType) {
}
