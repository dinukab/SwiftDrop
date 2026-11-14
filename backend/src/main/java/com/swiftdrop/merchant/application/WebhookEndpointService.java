package com.swiftdrop.merchant.application;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.merchant.domain.WebhookEndpoint;
import com.swiftdrop.merchant.domain.WebhookEndpointRepository;

/**
 * Manages webhook endpoint subscriptions for approved merchants (MER-3).
 */
@Service
@Lazy
public class WebhookEndpointService {

    public static final int MAX_WEBHOOK_ENDPOINTS = 5;

    private final WebhookEndpointRepository webhookRepository;
    private final MerchantRepository merchantRepository;

    public WebhookEndpointService(
            WebhookEndpointRepository webhookRepository,
            MerchantRepository merchantRepository
    ) {
        this.webhookRepository = webhookRepository;
        this.merchantRepository = merchantRepository;
    }

    @Transactional
    public WebhookEndpoint registerEndpoint(UUID merchantId, String url, String rawSecret) {
        Objects.requireNonNull(merchantId, "merchantId");
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(rawSecret, "rawSecret");

        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new MerchantNotFoundException(merchantId));

        if (!merchant.isApproved()) {
            throw new MerchantNotApprovedException(merchantId);
        }

        validateUrl(url.trim());

        if (webhookRepository.countByMerchantIdAndActiveTrue(merchantId) >= MAX_WEBHOOK_ENDPOINTS) {
            throw new IllegalArgumentException("Maximum of " + MAX_WEBHOOK_ENDPOINTS + " active webhook endpoints reached");
        }

        // Encode secret for storage (ADR 0004 §7.2)
        String secretEncrypted = Base64.getEncoder().encodeToString(rawSecret.trim().getBytes(StandardCharsets.UTF_8));

        WebhookEndpoint endpoint = new WebhookEndpoint(merchantId, url.trim(), secretEncrypted);
        return webhookRepository.save(endpoint);
    }

    @Transactional
    public void deactivateEndpoint(UUID endpointId, UUID merchantId) {
        Objects.requireNonNull(endpointId, "endpointId");
        Objects.requireNonNull(merchantId, "merchantId");

        WebhookEndpoint endpoint = webhookRepository.findByIdAndMerchantId(endpointId, merchantId)
                .orElseThrow(() -> new MerchantNotFoundException("Webhook endpoint not found: " + endpointId));

        endpoint.deactivate();
        webhookRepository.save(endpoint);
    }

    @Transactional(readOnly = true)
    public List<WebhookEndpoint> listActiveEndpoints(UUID merchantId) {
        Objects.requireNonNull(merchantId, "merchantId");
        return webhookRepository.findByMerchantIdAndActiveTrue(merchantId);
    }

    private void validateUrl(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException("Webhook URL must have http or https scheme");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException("Webhook URL must contain a valid host");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid webhook URL: " + e.getMessage());
        }
    }
}

