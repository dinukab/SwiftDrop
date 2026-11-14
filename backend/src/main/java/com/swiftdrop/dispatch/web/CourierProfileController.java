package com.swiftdrop.dispatch.web;

import java.util.UUID;

import jakarta.validation.Valid;

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
import com.swiftdrop.dispatch.domain.CourierProfile;
import com.swiftdrop.identity.api.UserDirectory;
import com.swiftdrop.shared.web.PageLimit;
import com.swiftdrop.shared.web.PageResponse;

/**
 * Courier profile management (SD-305). Dispatchers and admins only: an admin creates courier
 * accounts, so an admin may also create their profile.
 */
@RestController
@RequestMapping("/api/v1/couriers")
@PreAuthorize("hasAnyRole('DISPATCHER','ADMIN')")
class CourierProfileController {

    private final CourierProfileService profiles;
    private final UserDirectory users;

    CourierProfileController(CourierProfileService profiles, UserDirectory users) {
        this.profiles = profiles;
        this.users = users;
    }

    @GetMapping
    PageResponse<CourierProfileResponse> list(
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = PageLimit.DEFAULT_PAGE_STRING) int page,
            @RequestParam(defaultValue = PageLimit.DEFAULT_SIZE_STRING) int size) {
        Page<CourierProfile> result = profiles.listProfiles(active, page, size);
        return PageResponse.of(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CourierProfileResponse create(@Valid @RequestBody CreateCourierProfileRequest request) {
        return toResponse(profiles.createProfile(request.userId(), request.phone(), request.vehicleType()));
    }

    @PatchMapping("/{id}")
    CourierProfileResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateCourierProfileRequest request) {
        return toResponse(profiles.updateProfile(id, request.phone(), request.vehicleType(), request.active()));
    }

    private CourierProfileResponse toResponse(CourierProfile profile) {
        return CourierProfileResponse.from(profile, users.findUser(profile.getUserId()).orElse(null));
    }
}
