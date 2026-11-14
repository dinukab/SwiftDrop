package com.swiftdrop.dispatch.web;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.delivery.api.PageResult;
import com.swiftdrop.delivery.api.ParcelSummary;
import com.swiftdrop.dispatch.application.DispatchService;
import com.swiftdrop.shared.web.PageLimit;
import com.swiftdrop.shared.web.PageResponse;

/**
 * Dispatcher operations (SD-306). {@code DISPATCHER} only: per the RBAC model an admin gets 403
 * on delivery operations.
 */
@RestController
@PreAuthorize("hasRole('DISPATCHER')")
class DispatchController {

    private final DispatchService dispatch;

    DispatchController(DispatchService dispatch) {
        this.dispatch = dispatch;
    }

    @GetMapping("/api/v1/unassigned-parcels")
    PageResponse<UnassignedParcelResponse> unassigned(
            @RequestParam(defaultValue = PageLimit.DEFAULT_PAGE_STRING) int page,
            @RequestParam(defaultValue = PageLimit.DEFAULT_SIZE_STRING) int size) {
        PageResult<ParcelSummary> result = dispatch.unassigned(page, size);
        return PageResponse.of(
                result.items().stream().map(UnassignedParcelResponse::from).toList(),
                result.page(), result.size(), result.totalItems());
    }

    /** The dispatcher is the token subject, never a request field. */
    @PostMapping("/api/v1/parcel-assignments")
    ParcelAssignmentResponse assign(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AssignParcelRequest request) {
        dispatch.assign(request.parcelId(), request.courierUserId(), Callers.userId(jwt));
        return new ParcelAssignmentResponse(request.parcelId(), request.courierUserId());
    }
}
