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

    /**
     * Pending deliveries that are due, oldest first, locked for this sender. Rows locked by another
     * sender are skipped, so two senders never send the same delivery. Must run inside a transaction.
     */
    @Query(value = "SELECT * FROM merchant_webhook_deliveries WHERE status = 'PENDING' AND next_attempt_at <= :now "
            + "ORDER BY next_attempt_at LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<WebhookDelivery> lockDueDeliveries(@Param("now") Instant now, @Param("limit") int limit);
}
