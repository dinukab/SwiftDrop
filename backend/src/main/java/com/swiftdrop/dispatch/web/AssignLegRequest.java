package com.swiftdrop.dispatch.web;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** Body of {@code POST /api/v1/leg-assignments}; the dispatcher comes from the token. */
record AssignLegRequest(@NotNull UUID parcelId, @NotNull UUID legId, @NotNull UUID courierUserId) {
}
