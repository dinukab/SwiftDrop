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
import org.springframework.web.bind.annotation.PathVariable;
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
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        int pageNum = page != null ? PageLimit.clampPage(page) : PageLimit.DEFAULT_PAGE_NUMBER;
        int pageSize = size != null ? PageLimit.clampSize(size) : PageLimit.DEFAULT_PAGE_SIZE;
        var result = dispatch.unassigned(Callers.userId(jwt), pageNum, pageSize);
        List<UnassignedParcelResponse> items = result.items().stream()
                .map(UnassignedParcelResponse::from)
                .toList();
        return PageResponse.of(items, result.page(), result.size(), result.totalItems());
    }

    /**
     * Assigns a courier to a parcel that is still {@code CREATED}. The dispatcher is the caller from the
     * token, never a body field. 409 if the courier is not eligible, the parcel is past pickup or the dispatcher
     * has no hub; 404 if the parcel does not exist or is not waiting for pickup in the dispatcher's hub (the
     * two cases look the same on purpose).
     */
    @PostMapping("/api/v1/parcel-assignments")
    AssignmentResponse assign(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AssignmentRequest request
    ) {
        dispatch.assign(request.parcelId(), request.courierUserId(), Callers.userId(jwt));
        return new AssignmentResponse(request.parcelId(), request.courierUserId());
    }

    /**
     * The parcel's assignment history, oldest first. Only for a dispatcher of the hub the parcel is picked up in:
     * another hub's parcel gives 404, the same as a missing one; 409 if the dispatcher has no hub.
     */
    @GetMapping("/api/v1/parcels/{id}/assignment-history")
    List<AssignmentHistoryResponse> assignmentHistory(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return dispatch.assignmentHistory(id, Callers.userId(jwt)).stream()
                .map(AssignmentHistoryResponse::from)
                .toList();
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
