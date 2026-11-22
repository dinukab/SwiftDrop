package com.swiftdrop.delivery.web;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** Courier confirms arrival at a hub with a parcel. */
record HubArrivalRequest(
        @NotNull(message = "hub ID required") UUID hubId) {
}
