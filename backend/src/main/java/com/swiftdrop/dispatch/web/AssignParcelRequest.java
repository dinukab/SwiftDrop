package com.swiftdrop.dispatch.web;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** Body of {@code POST /api/v1/parcel-assignments}; the dispatcher comes from the token. */
record AssignParcelRequest(@NotNull UUID parcelId, @NotNull UUID courierUserId) {
}
