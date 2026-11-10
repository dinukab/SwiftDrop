package com.swiftdrop.merchant.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantRepository extends JpaRepository<Merchant, UUID> {

    Optional<Merchant> findByUserId(UUID userId);

    boolean existsByRegistrationNumber(String registrationNumber);

    boolean existsByContactEmail(String contactEmail);

    Page<Merchant> findByStatus(MerchantStatus status, Pageable pageable);
}

