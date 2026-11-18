package com.swiftdrop.dispatch.web;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.dispatch.application.HubService;

/**
 * Hub reference data and dispatcher-to-hub bindings. A dispatcher learns their hub here, from the
 * token, never from a request field; an admin assigns it.
 */
@RestController
@Lazy
class HubController {

    private final HubService hubs;

    HubController(HubService hubs) {
        this.hubs = hubs;
    }

    @GetMapping("/api/v1/hubs")
    @PreAuthorize("hasAnyRole('DISPATCHER','ADMIN')")
    List<HubResponse> list() {
        return hubs.listHubs().stream().map(HubResponse::from).toList();
    }

    /** The caller's own hub, or {@code hub: null} when none has been assigned yet. */
    @GetMapping("/api/v1/dispatchers/me")
    @PreAuthorize("hasRole('DISPATCHER')")
    DispatcherHubResponse me(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = Callers.userId(jwt);
        return new DispatcherHubResponse(userId, hubs.hubForDispatcher(userId).map(HubResponse::from).orElse(null));
    }

    /** Every dispatcher that has a hub, for the admin screen; unbound dispatchers are not listed. */
    @GetMapping("/api/v1/dispatcher-hubs")
    @PreAuthorize("hasRole('ADMIN')")
    List<DispatcherHubResponse> dispatcherHubs() {
        return hubs.listDispatcherHubs().entrySet().stream()
                .map(entry -> new DispatcherHubResponse(entry.getKey(), HubResponse.from(entry.getValue())))
                .toList();
    }

    @PutMapping("/api/v1/dispatchers/{userId}/hub")
    @PreAuthorize("hasRole('ADMIN')")
    DispatcherHubResponse assign(@PathVariable UUID userId, @Valid @RequestBody AssignDispatcherHubRequest request) {
        return new DispatcherHubResponse(userId, HubResponse.from(hubs.assignDispatcher(userId, request.hubCode())));
    }
}
