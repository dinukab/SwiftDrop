package com.swiftdrop.dispatch.web;

import jakarta.validation.constraints.Size;

import com.swiftdrop.dispatch.domain.CourierProfile;
import com.swiftdrop.dispatch.domain.VehicleType;

/** Body of {@code PATCH /api/v1/couriers/{id}}; a null field keeps the current value. */
record UpdateCourierProfileRequest(
        @Size(max = CourierProfile.MAX_PHONE_LENGTH) String phone,
        VehicleType vehicleType,
        Boolean active) {
}
