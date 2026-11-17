package com.swiftdrop.dispatch.web;

import java.util.UUID;

/** A dispatcher and the hub they work for; {@code hub} is null until an admin binds one. */
record DispatcherHubResponse(UUID userId, HubResponse hub) {
}
