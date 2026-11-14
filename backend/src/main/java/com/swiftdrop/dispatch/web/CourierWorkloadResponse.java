package com.swiftdrop.dispatch.web;

import java.util.UUID;

import com.swiftdrop.dispatch.application.CourierWorkload;
import com.swiftdrop.dispatch.domain.VehicleType;

/** Workload row for the dispatcher's courier picker. */
record CourierWorkloadResponse(UUID courierUserId, String fullName, VehicleType vehicleType, long activeParcels) {

    static CourierWorkloadResponse from(CourierWorkload workload) {
        return new CourierWorkloadResponse(workload.courierUserId(), workload.fullName(),
                workload.vehicleType(), workload.activeParcels());
    }
}
