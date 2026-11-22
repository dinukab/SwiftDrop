package com.swiftdrop.merchant.application;

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
    private final WebhookSecretCipher cipher;
    private final WebhookUrlPolicy urlPolicy;

    public WebhookEndpointService(
            WebhookEndpointRepository webhookRepository,
            MerchantRepository merchantRepository,
            WebhookSecretCipher cipher,
            WebhookUrlPolicy urlPolicy
    ) {
        this.webhookRepository = webhookRepository;
        this.merchantRepository = merchantRepository;
        this.cipher = cipher;
        this.urlPolicy = urlPolicy;
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

        // Same rules as at send time (ADR 0004 section 7.3); the sender re-checks before every attempt
        String normalizedUrl = urlPolicy.requireAllowed(url).toString();

        if (webhookRepository.countByMerchantIdAndActiveTrue(merchantId) >= MAX_WEBHOOK_ENDPOINTS) {
            throw new IllegalArgumentException("Maximum of " + MAX_WEBHOOK_ENDPOINTS + " active webhook endpoints reached");
        }

        // The sender needs the plain secret to sign, so it is encrypted, never hashed or left in clear
        // (ADR 0004 section 7.4). Refuses to store anything until a key is configured.
        String secretEncrypted = cipher.encrypt(rawSecret.trim(), merchantId);

        WebhookEndpoint endpoint = new WebhookEndpoint(merchantId, normalizedUrl, secretEncrypted);
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

}

