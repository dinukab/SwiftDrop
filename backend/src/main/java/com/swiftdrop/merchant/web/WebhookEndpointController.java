package com.swiftdrop.merchant.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.context.annotation.Lazy;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.merchant.application.MerchantApprovalService;
import com.swiftdrop.merchant.application.WebhookEndpointService;
import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.WebhookEndpoint;

/**
 * Endpoints for merchants to configure real-time webhook endpoints (MER-3).
 */
@RestController
@Lazy
@RequestMapping("/api/v1/merchants/me/webhooks")
@PreAuthorize("hasRole('MERCHANT')")
class WebhookEndpointController {

    private final WebhookEndpointService webhookService;
    private final MerchantApprovalService approvalService;

    WebhookEndpointController(WebhookEndpointService webhookService, MerchantApprovalService approvalService) {
        this.webhookService = webhookService;
        this.approvalService = approvalService;
    }

    @GetMapping
    List<WebhookEndpointResponse> listEndpoints(@AuthenticationPrincipal Jwt jwt) {
        Merchant merchant = getMerchant(jwt);
        return webhookService.listActiveEndpoints(merchant.getId()).stream()
                .map(WebhookEndpointResponse::from)
                .toList();
    }

    @PostMapping
    ResponseEntity<WebhookEndpointResponse> registerEndpoint(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RegisterWebhookRequest request
    ) {
        Merchant merchant = getMerchant(jwt);
        WebhookEndpoint endpoint = webhookService.registerEndpoint(merchant.getId(), request.url(), request.secret());
        return ResponseEntity.created(URI.create("/api/v1/merchants/me/webhooks/" + endpoint.getId()))
                .body(WebhookEndpointResponse.from(endpoint));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> deleteEndpoint(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        Merchant merchant = getMerchant(jwt);
        webhookService.deactivateEndpoint(id, merchant.getId());
        return ResponseEntity.noContent().build();
    }

    private Merchant getMerchant(Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return approvalService.getMerchantByUserId(userId);
    }
}

