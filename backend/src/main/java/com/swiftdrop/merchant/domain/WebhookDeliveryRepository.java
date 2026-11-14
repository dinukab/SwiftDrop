package com.swiftdrop.merchant.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    Optional<WebhookDelivery> findByEventIdAndEndpointId(UUID eventId, UUID endpointId);

    List<WebhookDelivery> findByMerchantId(UUID merchantId);

    @Query("SELECT d FROM WebhookDelivery d WHERE d.status = 'PENDING' AND d.nextAttemptAt <= :now ORDER BY d.nextAttemptAt ASC")
    List<WebhookDelivery> findDueDeliveries(@Param("now") Instant now);
}

