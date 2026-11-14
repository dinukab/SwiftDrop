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

import com.swiftdrop.merchant.application.GeneratedApiKey;
import com.swiftdrop.merchant.application.MerchantApiKeyService;
import com.swiftdrop.merchant.application.MerchantApprovalService;
import com.swiftdrop.merchant.domain.Merchant;

/**
 * Endpoints for merchants to view, generate, and revoke their API keys (MER-2).
 */
@RestController
@Lazy
@RequestMapping("/api/v1/merchants/me/api-keys")
@PreAuthorize("hasRole('MERCHANT')")
class MerchantApiKeyController {

    private final MerchantApiKeyService apiKeyService;
    private final MerchantApprovalService approvalService;

    MerchantApiKeyController(MerchantApiKeyService apiKeyService, MerchantApprovalService approvalService) {
        this.apiKeyService = apiKeyService;
        this.approvalService = approvalService;
    }

    @GetMapping
    List<ApiKeyResponse> listKeys(@AuthenticationPrincipal Jwt jwt) {
        Merchant merchant = getMerchant(jwt);
        return apiKeyService.listActiveKeys(merchant.getId()).stream()
                .map(ApiKeyResponse::from)
                .toList();
    }

    @PostMapping
    ResponseEntity<GeneratedApiKeyResponse> createKey(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateApiKeyRequest request
    ) {
        Merchant merchant = getMerchant(jwt);
        GeneratedApiKey key = apiKeyService.generateKey(merchant.getId(), request.label());
        return ResponseEntity.created(URI.create("/api/v1/merchants/me/api-keys/" + key.id()))
                .body(GeneratedApiKeyResponse.from(key));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> revokeKey(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        Merchant merchant = getMerchant(jwt);
        apiKeyService.revokeKey(id, merchant.getId());
        return ResponseEntity.noContent().build();
    }

    private Merchant getMerchant(Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return approvalService.getMerchantByUserId(userId);
    }
}

