package com.swiftdrop.delivery.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.application.CourierParcelService.CourierParcel;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.pricing.api.ParcelSize;

/**
 * One delivery on the courier's list (COU-2). Contains the contacts and addresses the courier needs.
 *
 * @param nextStatuses statuses the courier may set next, in lifecycle order; empty when finished
 * @param arrivalHubId the hub to confirm arrival at (pickup zone's hub) once the parcel is picked up; else null
 */
record CourierParcelResponse(UUID id, String trackingNumber, ParcelStatus status, List<ParcelStatus> nextStatuses,
        AddressResponse pickup, AddressResponse dropoff, ParcelSize size, int weightGrams, String description,
        boolean proofCaptured, Instant assignedAt, Instant updatedAt, UUID arrivalHubId) {

    static CourierParcelResponse from(CourierParcel item) {
        Parcel parcel = item.parcel();
        return new CourierParcelResponse(parcel.getId(), parcel.getTrackingNumber(), parcel.getStatus(),
                item.nextStatuses().stream().sorted().toList(), AddressResponse.from(parcel.getPickup()),
                AddressResponse.from(parcel.getDropoff()), parcel.getSize(), parcel.getWeightGrams(),
                parcel.getDescription(), item.proofCaptured(), parcel.getAssignedAt(), parcel.getUpdatedAt(),
                item.arrivalHubId());
    }

    /** Never includes personal data. */
    @Override
    public String toString() {
        return "CourierParcelResponse[id=" + id + ", trackingNumber=" + trackingNumber + ", status=" + status + "]";
    }
}
