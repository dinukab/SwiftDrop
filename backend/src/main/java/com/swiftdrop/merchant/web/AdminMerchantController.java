package com.swiftdrop.merchant.web;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.merchant.application.MerchantApprovalService;
import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantStatus;
import com.swiftdrop.shared.web.PageLimit;
import com.swiftdrop.shared.web.PageResponse;

/**
 * Dispatcher/Admin endpoints for managing and approving merchant registrations (MER-1, ADM-3).
 */
@RestController
@Lazy
@RequestMapping("/api/v1/admin/merchants")
@PreAuthorize("hasRole('DISPATCHER_ADMIN')")
class AdminMerchantController {

    private final MerchantApprovalService approvalService;

    AdminMerchantController(MerchantApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping
    PageResponse<MerchantResponse> listMerchants(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String status
    ) {
        MerchantStatus statusFilter = null;
        if (status != null && !status.isBlank()) {
            statusFilter = MerchantStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        }

        int pageNum = page != null ? PageLimit.clampPage(page) : PageLimit.DEFAULT_PAGE_NUMBER;
        int pageSize = size != null ? PageLimit.clampSize(size) : PageLimit.DEFAULT_PAGE_SIZE;

        Page<Merchant> merchantPage = approvalService.findMerchants(pageNum, pageSize, statusFilter);
        List<MerchantResponse> items = merchantPage.getContent().stream()
                .map(MerchantResponse::from)
                .toList();

        return PageResponse.of(items, pageNum, pageSize, merchantPage.getTotalElements());
    }

    @GetMapping("/{id}")
    MerchantResponse getMerchant(@PathVariable UUID id) {
        return MerchantResponse.from(approvalService.getMerchant(id));
    }

    @PatchMapping("/{id}/status")
    MerchantResponse reviewMerchant(
            @PathVariable UUID id,
            @Valid @RequestBody ReviewMerchantRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID adminUserId = UUID.fromString(jwt.getSubject());
        Merchant updated;
        switch (request.action()) {
            case "APPROVE" -> updated = approvalService.approve(id, adminUserId);
            case "REJECT" -> updated = approvalService.reject(id, adminUserId, request.reason());
            case "SUSPEND" -> updated = approvalService.suspend(id, adminUserId);
            default -> throw new IllegalArgumentException("Unknown review action: " + request.action());
        }
        return MerchantResponse.from(updated);
    }
}

