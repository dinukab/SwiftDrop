package com.swiftdrop.identity.web;

import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.identity.domain.CourierApproval;
import com.swiftdrop.identity.domain.User;

/** REST representation of a courier approval record. */
public record CourierApprovalResponse(
        UUID userId,
        String email,
        String fullName,
        String status,
        String rejectionReason,
        Instant createdAt,
        Instant reviewedAt,
        UUID reviewedBy) {

    static CourierApprovalResponse from(CourierApproval approval, User user) {
        return new CourierApprovalResponse(
                approval.getUserId(),
                user.getEmail(),
                user.getFullName(),
                approval.getStatus().name(),
                approval.getRejectionReason(),
                approval.getCreatedAt(),
                approval.getReviewedAt(),
                approval.getReviewedBy());
    }
}
