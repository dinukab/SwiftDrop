package com.swiftdrop.dispatch.web;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.dispatch.application.CourierProfileService;
import com.swiftdrop.dispatch.application.HubNotFoundException;
import com.swiftdrop.dispatch.application.HubService;
import com.swiftdrop.dispatch.domain.CourierProfile;
import com.swiftdrop.identity.api.UserDirectory;
import com.swiftdrop.shared.web.PageLimit;
import com.swiftdrop.shared.web.PageResponse;

/**
 * Administrator API for courier profiles (task 7.1, contract: {@code GET/POST /api/v1/couriers},
 * {@code PATCH /api/v1/couriers/{id}}). The courier's login is created first through
 * {@code POST /api/v1/staff-users}; this API adds the dispatch details (phone, vehicle, active).
 */
@RestController
@RequestMapping("/api/v1/couriers")
@PreAuthorize("hasRole('ADMIN')")
class CourierProfileController {

    private final CourierProfileService profiles;
    private final UserDirectory users;
    private final ObjectFactory<HubService> hubs;

    CourierProfileController(CourierProfileService profiles, UserDirectory users, ObjectFactory<HubService> hubs) {
        this.profiles = profiles;
        this.users = users;
        this.hubs = hubs;
    }

    @GetMapping
    PageResponse<CourierProfileResponse> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Boolean active) {
        int pageNum = page != null ? page : PageLimit.DEFAULT_PAGE_NUMBER;
        int pageSize = size != null ? size : PageLimit.DEFAULT_PAGE_SIZE;
        Page<CourierProfile> result = profiles.listProfiles(pageNum, pageSize, active);
        // Profiles of deleted accounts are hidden. The total still counts them, so a page can hold fewer items.
        List<CourierProfileResponse> items = result.getContent().stream()
                .filter(profile -> users.findUser(profile.getUserId()).isPresent())
                .map(this::toResponse)
                .toList();
        return PageResponse.of(items, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CourierProfileResponse create(@Valid @RequestBody CreateCourierProfileRequest request) {
        if (request.hubCode() == null || request.hubCode().isBlank()) {
            return toResponse(profiles.createProfile(request.userId(), request.phone(), request.vehicleType()));
        }
        UUID homeHubId = hubs.getObject().findActiveByCode(request.hubCode()).orElseThrow(HubNotFoundException::new).getId();
        return toResponse(profiles.createProfile(request.userId(), request.phone(), request.vehicleType(), homeHubId));
    }

    @PatchMapping("/{id}")
    CourierProfileResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateCourierProfileRequest request) {
        CourierProfile profile = null;
        if (request.phone() != null || request.vehicleType() != null) {
            profile = profiles.updateProfile(id, request.phone(), request.vehicleType());
        }
        if (Boolean.TRUE.equals(request.active())) {
            profile = profiles.activate(id);
        } else if (Boolean.FALSE.equals(request.active())) {
            profile = profiles.deactivate(id);
        }
        if (profile == null) {
            // Empty body: nothing to change, but still report a missing profile as 404
            profile = profiles.getProfile(id);
        }
        return toResponse(profile);
    }

    private CourierProfileResponse toResponse(CourierProfile profile) {
        return CourierProfileResponse.from(profile, users.findUser(profile.getUserId()));
    }
}
