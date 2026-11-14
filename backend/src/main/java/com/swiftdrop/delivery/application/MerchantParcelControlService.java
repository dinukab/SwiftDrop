package com.swiftdrop.delivery.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.delivery.api.MerchantParcelControl;
import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.domain.ActorRole;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelRepository;

/**
 * {@link MerchantParcelControl} for {@code merchant}. Looks the parcel up by the merchant's own
 * order reference, then changes it under the parcel lock so a hold or cancel cannot race a courier
 * pickup or a dispatcher assignment (ADR 0006 §5).
 */
@Service
class MerchantParcelControlService implements MerchantParcelControl {

    private static final Logger log = LoggerFactory.getLogger(MerchantParcelControlService.class);

    static final int MAX_REASON_LENGTH = 500;

    private final ParcelRepository parcels;
    private final ParcelEventRecorder recorder;
    private final Clock clock;

    @Autowired
    MerchantParcelControlService(ParcelRepository parcels, ParcelEventRecorder recorder) {
        this(parcels, recorder, Clock.systemUTC());
    }

    MerchantParcelControlService(ParcelRepository parcels, ParcelEventRecorder recorder, Clock clock) {
        this.parcels = parcels;
        this.recorder = recorder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ParcelStatus pause(UUID merchantId, String merchantOrderReference, UUID merchantUserId, String reason) {
        return apply(merchantId, merchantOrderReference, merchantUserId, reason, Parcel::pause);
    }

    @Override
    @Transactional
    public ParcelStatus resume(UUID merchantId, String merchantOrderReference, UUID merchantUserId, String reason) {
        return apply(merchantId, merchantOrderReference, merchantUserId, reason, Parcel::resume);
    }

    @Override
    @Transactional
    public ParcelStatus cancel(UUID merchantId, String merchantOrderReference, UUID merchantUserId, String reason) {
        return apply(merchantId, merchantOrderReference, merchantUserId, reason, Parcel::cancel);
    }

    private ParcelStatus apply(UUID merchantId, String merchantOrderReference, UUID merchantUserId, String reason,
            HoldMove move) {
        Objects.requireNonNull(merchantId, "merchantId");
        Objects.requireNonNull(merchantOrderReference, "merchantOrderReference");
        Objects.requireNonNull(merchantUserId, "merchantUserId");
        String note = cleanReason(reason);

        // One locked read by the merchant's own reference (another merchant's order is simply not
        // found), so a concurrent pickup or assignment finishes before this change starts.
        Parcel parcel = parcels
                .findByMerchantIdAndMerchantOrderReferenceForUpdate(merchantId, merchantOrderReference.trim())
                .orElseThrow(ParcelNotFoundException::new);

        ParcelStatus previous = parcel.getStatus();
        Instant now = clock.instant();
        move.apply(parcel, merchantId, now);
        // The reason is free text from the merchant: stored in history only, never in the event
        recorder.record(parcel, previous, new Actor(merchantUserId, ActorRole.MERCHANT), note, null, now);
        // IDs only: no personal data in logs
        log.info("Parcel {} moved {} -> {} by merchant user {}", parcel.getId(), previous, parcel.getStatus(),
                merchantUserId);
        return parcel.getStatus();
    }

    private static String cleanReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String trimmed = reason.trim();
        if (trimmed.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("reason must be at most " + MAX_REASON_LENGTH + " characters");
        }
        return trimmed;
    }

    @FunctionalInterface
    private interface HoldMove {
        void apply(Parcel parcel, UUID merchantId, Instant now);
    }
}
