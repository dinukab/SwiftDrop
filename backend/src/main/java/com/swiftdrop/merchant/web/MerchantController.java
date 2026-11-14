package com.swiftdrop.merchant.web;

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

import com.swiftdrop.merchant.application.MerchantOnboardingService;
import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantStatus;
import com.swiftdrop.shared.web.PageLimit;
import com.swiftdrop.shared.web.PageResponse;

/**
 * Merchant registration and admin review endpoints (contract §4 / SD-401 / SD-402).
 *
 * <ul>
 *   <li>{@code POST /api/v1/merchants} — public registration (PENDING)</li>
 *   <li>{@code GET /api/v1/merchants/me} — own profile (MERCHANT)</li>
 *   <li>{@code GET /api/v1/merchants} — admin directory</li>
 *   <li>{@code POST /api/v1/merchants/{id}/approval} — admin approve</li>
 *   <li>{@code POST /api/v1/merchants/{id}/rejection} — admin reject</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/merchants")
class MerchantController {

    private final MerchantOnboardingService onboarding;

    MerchantController(MerchantOnboardingService onboarding) {
        this.onboarding = onboarding;
    }

    /** Public merchant registration. Creates MERCHANT user + PENDING merchant atomically. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    MerchantResponse register(@Valid @RequestBody RegisterMerchantRequest request) {
        return MerchantResponse.from(onboarding.register(
                request.fullName(),
                request.email(),
                request.password(),
                request.businessName(),
                request.contactPhone()));
    }

    /** Authenticated merchant's own profile and approval status. */
    @GetMapping("/me")
    @PreAuthorize("hasRole('MERCHANT')")
    MerchantResponse me(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return MerchantResponse.from(onboarding.requireOwnMerchant(userId));
    }

    /** Admin directory of merchants, optionally filtered by status. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    PageResponse<MerchantResponse> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String status) {
        MerchantStatus statusFilter = parseStatus(status);
        int pageNum = page != null ? Math.max(0, page) : PageLimit.DEFAULT_PAGE_NUMBER;
        int pageSize = size != null ? PageLimit.clampSize(size) : PageLimit.DEFAULT_PAGE_SIZE;
        Page<Merchant> merchantPage = onboarding.list(pageNum, pageSize, statusFilter);
        List<MerchantResponse> items = merchantPage.getContent().stream()
                .map(MerchantResponse::from)
                .toList();
        return PageResponse.of(items, merchantPage.getNumber(), pageSize, merchantPage.getTotalElements());
    }

    /** Approves a PENDING merchant. Restricted to ADMIN. */
    @PostMapping("/{id}/approval")
    @PreAuthorize("hasRole('ADMIN')")
    MerchantResponse approve(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID adminId = UUID.fromString(jwt.getSubject());
        return MerchantResponse.from(onboarding.approve(id, adminId));
    }

    /** Rejects a PENDING merchant with a reason. Restricted to ADMIN. */
    @PostMapping("/{id}/rejection")
    @PreAuthorize("hasRole('ADMIN')")
    MerchantResponse reject(
            @PathVariable UUID id,
            @Valid @RequestBody RejectMerchantRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID adminId = UUID.fromString(jwt.getSubject());
        return MerchantResponse.from(onboarding.reject(id, adminId, request.reason()));
    }

    private static MerchantStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return MerchantStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status filter: " + status);
        }
    }
}
