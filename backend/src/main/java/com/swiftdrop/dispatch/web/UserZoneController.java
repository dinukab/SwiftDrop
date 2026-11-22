package com.swiftdrop.dispatch.web;

import java.util.Optional;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.swiftdrop.dispatch.application.CourierProfileService;
import com.swiftdrop.dispatch.application.HubNotFoundException;
import com.swiftdrop.dispatch.application.HubService;
import com.swiftdrop.dispatch.domain.CourierProfile;
import com.swiftdrop.dispatch.domain.Hub;
import com.swiftdrop.identity.api.UserDirectory;
import com.swiftdrop.identity.api.UserSummary;

/**
 * Administrator view of the zone a user is assigned to: a dispatcher's hub, or a courier's home hub.
 * Other roles have no zone and get {@code NOT_APPLICABLE}.
 */
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMIN')")
class UserZoneController {

    private static final String DISPATCHER_HUB = "DISPATCHER_HUB";
    private static final String COURIER_HOME_HUB = "COURIER_HOME_HUB";
    private static final String NOT_APPLICABLE = "NOT_APPLICABLE";

    private final UserDirectory users;
    /** Looked up lazily, as in CourierProfileController, so this controller does not need the hub repositories at startup. */
    private final ObjectFactory<HubService> hubs;
    private final CourierProfileService profiles;

    UserZoneController(UserDirectory users, ObjectFactory<HubService> hubs, CourierProfileService profiles) {
        this.users = users;
        this.hubs = hubs;
        this.profiles = profiles;
    }

    @GetMapping("/{userId}/zone")
    UserZoneResponse zone(@PathVariable UUID userId) {
        UserSummary user = users.findUser(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.hasRole("DISPATCHER")) {
            return UserZoneResponse.of(userId, DISPATCHER_HUB, hubs.getObject().hubForDispatcher(userId));
        }
        if (user.hasRole("COURIER")) {
            Optional<Hub> homeHub = profiles.findByUserId(userId)
                    .map(CourierProfile::getHomeHubId)
                    .map(hubId -> hubs.getObject().requireHub(hubId));
            return UserZoneResponse.of(userId, COURIER_HOME_HUB, homeHub);
        }
        return UserZoneResponse.of(userId, NOT_APPLICABLE, Optional.empty());
    }

    /**
     * Changes the zone of a courier (their home hub) or a dispatcher (their hub). Other roles have no zone
     * and get 409. Active parcel assignments are not changed; only where the user is based from now on.
     */
    @PutMapping("/{userId}/zone")
    UserZoneResponse assign(@PathVariable UUID userId, @Valid @RequestBody AssignUserZoneRequest request) {
        UserSummary user = users.findUser(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.hasRole("DISPATCHER")) {
            Hub hub = hubs.getObject().assignDispatcher(userId, request.hubCode());
            return UserZoneResponse.of(userId, DISPATCHER_HUB, Optional.of(hub));
        }
        if (user.hasRole("COURIER")) {
            CourierProfile profile = profiles.findByUserId(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                            "This courier has no dispatch profile yet"));
            Hub hub = hubs.getObject().findActiveByCode(request.hubCode())
                    .orElseThrow(HubNotFoundException::new);
            profiles.assignHomeHub(profile.getId(), hub.getId());
            return UserZoneResponse.of(userId, COURIER_HOME_HUB, Optional.of(hub));
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Only couriers and dispatchers have a zone");
    }
}
