package com.swiftdrop.dispatch.web;

import java.util.Optional;
import java.util.UUID;

import com.swiftdrop.dispatch.domain.Hub;

/**
 * The zone a user is assigned to. {@code basis} says where it comes from: {@code DISPATCHER_HUB} (the
 * dispatcher's hub), {@code COURIER_HOME_HUB} (the courier's home hub) or {@code NOT_APPLICABLE} (the
 * role has no zone). {@code zoneCode} and {@code zoneName} are null when a zone applies but none is set.
 */
record UserZoneResponse(UUID userId, String basis, String zoneCode, String zoneName) {

    static UserZoneResponse of(UUID userId, String basis, Optional<Hub> hub) {
        return new UserZoneResponse(
                userId,
                basis,
                hub.map(Hub::getCode).orElse(null),
                hub.map(Hub::getName).orElse(null));
    }
}
