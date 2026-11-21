package com.swiftdrop.notification.web;

import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import com.swiftdrop.notification.application.DeadLetterReplayService;
import com.swiftdrop.shared.web.PageLimit;
import com.swiftdrop.shared.web.PageResponse;

/**
 * Admin recovery of dead-lettered notification events (ADR 0004 section 6). Administrators only; the replaying user is
 * always the caller from the token. Message bodies are never returned, because they name a parcel and its owner.
 */
@RestController
@Lazy
@PreAuthorize("hasRole('ADMIN')")
class DeadLetterController {

    private final DeadLetterReplayService replays;

    DeadLetterController(DeadLetterReplayService replays) {
        this.replays = replays;
    }

    /** What waits in the dead-letter queue: the depth and the oldest messages (no bodies). */
    @GetMapping("/api/v1/admin/dead-letters")
    DeadLetterOverviewResponse overview() {
        return DeadLetterOverviewResponse.from(replays.overview());
    }

    /**
     * Moves the oldest dead letters back to the source queue. A replay that stopped early (for example the broker did
     * not confirm) still answers 200: {@code stopReason} says why and {@code replayed} what did move.
     */
    @PostMapping("/api/v1/admin/dead-letters/replay")
    ReplayOutcomeResponse replay(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ReplayRequest request) {
        return ReplayOutcomeResponse.from(replays.replay(Callers.userId(jwt), request.limit()));
    }

    /** The audit trail of replays, newest first. */
    @GetMapping("/api/v1/admin/dead-letters/replays")
    PageResponse<ReplayAuditResponse> history(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        int pageNum = page != null ? PageLimit.clampPage(page) : PageLimit.DEFAULT_PAGE_NUMBER;
        int pageSize = size != null ? PageLimit.clampSize(size) : PageLimit.DEFAULT_PAGE_SIZE;
        var result = replays.history(pageNum, pageSize);
        return PageResponse.of(result.getContent().stream().map(ReplayAuditResponse::from).toList(), pageNum,
                pageSize, result.getTotalElements());
    }

    record ReplayRequest(
            @NotNull @Min(1) @Max(DeadLetterReplayService.MAX_REPLAY) Integer limit) {
    }
}
