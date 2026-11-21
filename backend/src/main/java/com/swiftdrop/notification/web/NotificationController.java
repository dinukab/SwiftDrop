package com.swiftdrop.notification.web;

import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.notification.application.NotificationService;
import com.swiftdrop.shared.web.PageLimit;
import com.swiftdrop.shared.web.PageResponse;

/**
 * The caller's notification inbox (ADR 0004 section 8). The user is always the caller from the token, never a
 * request value, so nobody can read or change another user's messages. Only customers receive notifications under the
 * current policy, so the endpoints are for the {@code CUSTOMER} role.
 */
@RestController
@Lazy
@PreAuthorize("hasRole('CUSTOMER')")
class NotificationController {

    private final NotificationService notifications;

    NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    /** The caller's notifications, newest first. */
    @GetMapping("/api/v1/notifications")
    PageResponse<NotificationResponse> inbox(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        int pageNum = page != null ? PageLimit.clampPage(page) : PageLimit.DEFAULT_PAGE_NUMBER;
        int pageSize = size != null ? PageLimit.clampSize(size) : PageLimit.DEFAULT_PAGE_SIZE;
        var result = notifications.inbox(Callers.userId(jwt), pageNum, pageSize);
        return PageResponse.of(result.getContent().stream().map(NotificationResponse::from).toList(), pageNum,
                pageSize, result.getTotalElements());
    }

    /** How many of the caller's notifications are unread (the bell). */
    @GetMapping("/api/v1/notifications/unread-count")
    UnreadCountResponse unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return new UnreadCountResponse(notifications.unreadCount(Callers.userId(jwt)));
    }

    /** Marks one notification read. 404 if it does not exist or is someone else's. */
    @PatchMapping("/api/v1/notifications/{id}/read")
    NotificationResponse markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return NotificationResponse.from(notifications.markRead(Callers.userId(jwt), id));
    }

    /** Marks all of the caller's notifications read. */
    @PostMapping("/api/v1/notifications/read-all")
    ReadAllResponse readAll(@AuthenticationPrincipal Jwt jwt) {
        return new ReadAllResponse(notifications.markAllRead(Callers.userId(jwt)));
    }

    record UnreadCountResponse(long count) {
    }

    record ReadAllResponse(int updated) {
    }
}
