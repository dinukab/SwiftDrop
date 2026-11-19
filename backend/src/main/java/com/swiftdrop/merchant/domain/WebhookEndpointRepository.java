package com.swiftdrop.merchant.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {

    List<WebhookEndpoint> findByMerchantIdAndActiveTrue(UUID merchantId);

    Optional<WebhookEndpoint> findByIdAndMerchantId(UUID id, UUID merchantId);

    int countByMerchantIdAndActiveTrue(UUID merchantId);
}

