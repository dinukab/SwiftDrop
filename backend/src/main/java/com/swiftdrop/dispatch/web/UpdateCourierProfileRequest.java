package com.swiftdrop.dispatch.web;

import jakarta.validation.constraints.Size;

import com.swiftdrop.dispatch.domain.CourierProfile;
import com.swiftdrop.dispatch.domain.VehicleType;

/** Partial update of a courier profile; every field is optional and {@code null} means "keep". */
record UpdateCourierProfileRequest(
        @Size(max = CourierProfile.MAX_PHONE_LENGTH) String phone,
        VehicleType vehicleType,
        Boolean active
) {

    /** Never includes the phone number: it is personal data. */
    @Override
    public String toString() {
        return "UpdateCourierProfileRequest[vehicleType=" + vehicleType + ", active=" + active + ", ***]";
    }
}
