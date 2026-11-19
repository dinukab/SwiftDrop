package com.swiftdrop.merchant.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantApiKeyRepository extends JpaRepository<MerchantApiKey, UUID> {

    List<MerchantApiKey> findByMerchantIdAndActiveTrue(UUID merchantId);

    Optional<MerchantApiKey> findByKeyPrefixAndActiveTrue(String keyPrefix);

    Optional<MerchantApiKey> findByIdAndMerchantId(UUID id, UUID merchantId);

    int countByMerchantIdAndActiveTrue(UUID merchantId);
}

