package com.swiftdrop.dispatch.web;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code POST /api/v1/hub-transfers} and of the receipt call. The dispatcher and their hub come from the
 * token; the destination is the leg's planned next hub, so the caller cannot name one.
 */
record HubTransferRequest(@NotNull UUID parcelId, @NotNull UUID legId) {
}
