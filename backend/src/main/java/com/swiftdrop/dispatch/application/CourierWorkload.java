package com.swiftdrop.dispatch.application;

import java.util.UUID;

import com.swiftdrop.dispatch.domain.VehicleType;

/** One eligible courier and how many non-terminal parcels they hold (SD-307). */
public record CourierWorkload(UUID courierUserId, String fullName, VehicleType vehicleType, long activeParcels) {
}
