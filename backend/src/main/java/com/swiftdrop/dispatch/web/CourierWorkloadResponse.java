package com.swiftdrop.dispatch.web;

import java.util.UUID;

/**
 * One courier's workload for the dispatcher. No phone number: the dispatcher assigns by name and
 * sees the load, and the phone stays with admins (PROGRESS 7.2).
 */
public record CourierWorkloadResponse(
        UUID courierUserId,
        String name,
        String vehicleType,
        long activeParcels
) {
}
