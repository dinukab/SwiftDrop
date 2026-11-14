package com.swiftdrop.identity.application;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.swiftdrop.identity.domain.CourierApproval;
import com.swiftdrop.identity.domain.CourierApprovalRepository;
import com.swiftdrop.identity.domain.CourierApprovalStatus;
import com.swiftdrop.identity.domain.User;
import com.swiftdrop.identity.domain.UserRepository;
import com.swiftdrop.shared.web.PageLimit;

/**
 * Public courier registration and admin review. Registration creates a COURIER user plus a
 * {@link CourierApprovalStatus#PENDING} approval row atomically; the COURIER authority is
 * withheld from access tokens until an administrator approves (see {@code AccessTokenIssuer}).
 * Admin-created couriers have no approval row and are treated as approved.
 */
@Service
public class CourierApprovalService {

    private final CourierApprovalRepository approvals;
    private final AccountService accounts;
    private final UserRepository users;

    CourierApprovalService(CourierApprovalRepository approvals, AccountService accounts,
            UserRepository users) {
        this.approvals = approvals;
        this.accounts = accounts;
        this.users = users;
    }

    /** Registers a courier account pending admin approval. */
    @Transactional
    public CourierApproval register(String fullName, String email, String rawPassword) {
        User user = accounts.registerCourier(fullName, email, rawPassword);
        try {
            return approvals.saveAndFlush(new CourierApproval(user.getId()));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Courier registration already exists");
        }
    }

    /** Returns the approval row for {@code userId}, or empty for admin-created couriers. */
    @Transactional(readOnly = true)
    public java.util.Optional<CourierApproval> findByUserId(UUID userId) {
        return approvals.findByUserId(userId);
    }

    /** Whether the courier may hold the COURIER authority: approved, or admin-created (no row). */
    @Transactional(readOnly = true)
    public boolean isApproved(UUID userId) {
        return approvals.findByUserId(userId)
                .map(CourierApproval::isApproved)
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public Page<CourierApproval> list(int page, int size, CourierApprovalStatus statusFilter) {
        int clampedSize = PageLimit.clampSize(size);
        int safePage = Math.max(0, page);
        Pageable pageable = PageRequest.of(safePage, clampedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (statusFilter != null) {
            return approvals.findByStatus(statusFilter, pageable);
        }
        return approvals.findAll(pageable);
    }

    @Transactional
    public CourierApproval approve(UUID userId, UUID adminId) {
        CourierApproval approval = requirePending(userId);
        approval.approve(adminId);
        return approvals.saveAndFlush(approval);
    }

    @Transactional
    public CourierApproval reject(UUID userId, UUID adminId, String reason) {
        CourierApproval approval = requirePending(userId);
        approval.reject(adminId, reason);
        return approvals.saveAndFlush(approval);
    }

    private CourierApproval requirePending(UUID userId) {
        CourierApproval approval = approvals.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Courier registration not found"));
        if (approval.getStatus() != CourierApprovalStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Courier registration is already " + approval.getStatus().name().toLowerCase());
        }
        return approval;
    }

    /** Looks up the user behind an approval row for admin display. */
    @Transactional(readOnly = true)
    public User findUser(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
