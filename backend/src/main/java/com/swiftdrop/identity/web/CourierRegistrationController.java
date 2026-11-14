package com.swiftdrop.identity.web;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.swiftdrop.identity.application.CourierApprovalService;
import com.swiftdrop.identity.domain.CourierApproval;
import com.swiftdrop.identity.domain.CourierApprovalStatus;
import com.swiftdrop.shared.web.PageLimit;
import com.swiftdrop.shared.web.PageResponse;

/**
 * Public courier registration and admin review. A public registration creates a COURIER user
 * with a PENDING approval; the COURIER authority is granted only after a DISPATCHER_ADMIN
 * approves. Admin-created couriers have no approval row.
 */
@RestController
@RequestMapping("/api/v1/courier-registrations")
class CourierRegistrationController {

    private final CourierApprovalService approvals;

    CourierRegistrationController(CourierApprovalService approvals) {
        this.approvals = approvals;
    }

    /** Public courier registration. The role is always COURIER; client-supplied roles are ignored. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CourierApprovalResponse register(@Valid @RequestBody RegisterUserRequest request) {
        return response(approvals.register(request.fullName(), request.email(), request.password()));
    }

    /** The authenticated courier's own approval status. 404 for admin-created couriers. */
    @GetMapping("/me")
    CourierApprovalResponse me(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        CourierApproval approval = approvals.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No courier approval record"));
        return response(approval);
    }

    /** Admin directory of courier registrations, optionally filtered by status. */
    @GetMapping
    @PreAuthorize("hasRole('DISPATCHER_ADMIN')")
    PageResponse<CourierApprovalResponse> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String status) {
        CourierApprovalStatus statusFilter = parseStatus(status);
        int pageNum = page != null ? Math.max(0, page) : PageLimit.DEFAULT_PAGE_NUMBER;
        int pageSize = size != null ? PageLimit.clampSize(size) : PageLimit.DEFAULT_PAGE_SIZE;
        Page<CourierApproval> approvalPage = approvals.list(pageNum, pageSize, statusFilter);
        List<CourierApprovalResponse> items = approvalPage.getContent().stream()
                .map(this::response)
                .toList();
        return PageResponse.of(items, approvalPage.getNumber(), pageSize, approvalPage.getTotalElements());
    }

    /** Approves a PENDING courier registration. Restricted to DISPATCHER_ADMIN. */
    @PostMapping("/{userId}/approval")
    @PreAuthorize("hasRole('DISPATCHER_ADMIN')")
    CourierApprovalResponse approve(@PathVariable UUID userId, @AuthenticationPrincipal Jwt jwt) {
        UUID adminId = UUID.fromString(jwt.getSubject());
        return response(approvals.approve(userId, adminId));
    }

    /** Rejects a PENDING courier registration with a reason. Restricted to DISPATCHER_ADMIN. */
    @PostMapping("/{userId}/rejection")
    @PreAuthorize("hasRole('DISPATCHER_ADMIN')")
    CourierApprovalResponse reject(
            @PathVariable UUID userId,
            @Valid @RequestBody RejectCourierRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID adminId = UUID.fromString(jwt.getSubject());
        return response(approvals.reject(userId, adminId, request.reason()));
    }

    private CourierApprovalResponse response(CourierApproval approval) {
        return CourierApprovalResponse.from(approval, approvals.findUser(approval.getUserId()));
    }

    private static CourierApprovalStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return CourierApprovalStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status filter: " + status);
        }
    }
}
