package com.swiftdrop.merchant.web;

import java.util.UUID;

import org.springframework.context.annotation.Lazy;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.merchant.application.MerchantApprovalService;
import com.swiftdrop.merchant.domain.Merchant;

/**
 * Endpoints for the logged-in merchant to manage their own profile and view status.
 */
@RestController
@Lazy
@RequestMapping("/api/v1/merchants/me")
@PreAuthorize("hasRole('MERCHANT')")
class MerchantPortalController {

    private final MerchantApprovalService approvalService;

    MerchantPortalController(MerchantApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping
    MerchantResponse me(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        Merchant merchant = approvalService.getMerchantByUserId(userId);
        return MerchantResponse.from(merchant);
    }
}

