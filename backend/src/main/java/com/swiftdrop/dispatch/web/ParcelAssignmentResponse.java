package com.swiftdrop.dispatch.web;

import java.util.UUID;

/** Confirms who now holds the parcel. */
record ParcelAssignmentResponse(UUID parcelId, UUID courierUserId) {
}
