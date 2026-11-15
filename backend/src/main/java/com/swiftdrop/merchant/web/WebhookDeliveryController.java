package com.swiftdrop.merchant.web;

import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.merchant.application.MerchantOnboardingService;
import com.swiftdrop.merchant.application.WebhookDeliveryHistoryService;
import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.shared.web.PageResponse;

/**
 * A merchant's webhook delivery history and manual redelivery (ADR 0004 section 7, Phase 4 item 6).
 * Orders are a merchant capability, so only an approved merchant may use these endpoints.
 */
@RestController
@Lazy
@RequestMapping("/api/v1/merchants/me/webhook-deliveries")
@PreAuthorize("hasRole('MERCHANT')")
class WebhookDeliveryController {

    private final WebhookDeliveryHistoryService history;
    private final MerchantOnboardingService onboarding;

    WebhookDeliveryController(WebhookDeliveryHistoryService history, MerchantOnboardingService onboarding) {
        this.history = history;
        this.onboarding = onboarding;
    }

    @GetMapping
    PageResponse<WebhookDeliveryResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size
    ) {
        Merchant merchant = getMerchant(jwt);
        var result = history.history(merchant.getId(), page, size);
        return PageResponse.of(result.map(WebhookDeliveryResponse::from).getContent(), result.getNumber(),
                result.getSize(), result.getTotalElements());
    }

    /**
     * Sends a failed delivery again. 409 if it has not failed, its endpoint is inactive, or it has used
     * its three manual redeliveries. 404 if it is not this merchant's.
     */
    @PostMapping("/{id}/redeliveries")
    WebhookDeliveryResponse redeliver(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        Merchant merchant = getMerchant(jwt);
        return WebhookDeliveryResponse.from(history.redeliver(id, merchant.getId()));
    }

    private Merchant getMerchant(Jwt jwt) {
        return onboarding.requireApproved(UUID.fromString(jwt.getSubject()));
    }
}
