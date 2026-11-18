package com.swiftdrop.dispatch.web;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.dispatch.application.DispatchService;
import com.swiftdrop.shared.web.PageLimit;
import com.swiftdrop.shared.web.PageResponse;

/**
 * Dispatcher operations (ADM-1, ADM-2, contract §4.2): unassigned parcels, courier assignment and
 * courier workload. Operational endpoints belong to the {@code DISPATCHER} role (rbac-authorization.md).
 */
@RestController
@Lazy
@PreAuthorize("hasRole('DISPATCHER')")
class DispatchController {

    private final DispatchService dispatch;

    DispatchController(DispatchService dispatch) {
        this.dispatch = dispatch;
    }

    @GetMapping("/api/v1/unassigned-parcels")
    PageResponse<UnassignedParcelResponse> unassigned(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        int pageNum = page != null ? PageLimit.clampPage(page) : PageLimit.DEFAULT_PAGE_NUMBER;
        int pageSize = size != null ? PageLimit.clampSize(size) : PageLimit.DEFAULT_PAGE_SIZE;
        var result = dispatch.unassigned(pageNum, pageSize);
        List<UnassignedParcelResponse> items = result.items().stream()
                .map(UnassignedParcelResponse::from)
                .toList();
        return PageResponse.of(items, result.page(), result.size(), result.totalItems());
    }

    /**
     * Assigns a courier to a parcel that is still {@code CREATED}. The dispatcher is the caller from the
     * token, never a body field. 409 if the courier is not eligible or the parcel is past pickup; 404 if
     * the parcel does not exist.
     */
    @PostMapping("/api/v1/parcel-assignments")
    AssignmentResponse assign(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AssignmentRequest request
    ) {
        dispatch.assign(request.parcelId(), request.courierUserId(), UUID.fromString(jwt.getSubject()));
        return new AssignmentResponse(request.parcelId(), request.courierUserId());
    }

    /** Every active courier with their active parcel count, least busy first. */
    @GetMapping("/api/v1/courier-workloads")
    List<CourierWorkloadResponse> workloads() {
        return dispatch.workloads().stream()
                .map(w -> new CourierWorkloadResponse(w.courierUserId(), w.name(), w.vehicleType(), w.activeParcels()))
                .toList();
    }

    /** Request body of an assignment. */
    record AssignmentRequest(@NotNull UUID parcelId, @NotNull UUID courierUserId) {
    }

    /** The assignment that was recorded. */
    record AssignmentResponse(UUID parcelId, UUID courierUserId) {
    }
}
