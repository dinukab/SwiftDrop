package com.swiftdrop.delivery.application;

import java.util.Objects;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.swiftdrop.delivery.api.CreatedParcel;
import com.swiftdrop.delivery.api.NewMerchantParcel;
import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelCreation;
import com.swiftdrop.delivery.domain.OrderFingerprint;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelRepository;

/**
 * {@link ParcelCreation} for the {@code merchant} module. Idempotent per merchant and order
 * reference (D-25): a repeated order returns the existing parcel and emits no second event, also
 * when two identical orders arrive at the same time.
 *
 * <p>Deliberately not transactional itself: the booking runs in its own transaction, so after a
 * unique-constraint violation the existing parcel can be read in a fresh one.
 */
@Service
class MerchantParcelCreation implements ParcelCreation {

    private final ParcelBookingService booking;
    private final ParcelRepository parcels;

    MerchantParcelCreation(ParcelBookingService booking, ParcelRepository parcels) {
        this.booking = booking;
        this.parcels = parcels;
    }

    @Override
    public CreatedParcel createMerchantParcel(NewMerchantParcel order) {
        Objects.requireNonNull(order, "order");
        if (order.merchantId() == null || order.merchantOrderReference() == null
                || order.merchantOrderReference().isBlank()) {
            throw new IllegalArgumentException("merchantId and merchantOrderReference are required");
        }
        Optional<Parcel> existing = findExisting(order);
        if (existing.isPresent()) {
            return toCreated(sameOrder(existing.get(), order), false);
        }
        try {
            return toCreated(booking.bookForMerchant(order), true);
        } catch (DataIntegrityViolationException e) {
            // Lost a race with an identical order; return the winner's parcel if it is the same order
            return findExisting(order)
                    .map(parcel -> toCreated(sameOrder(parcel, order), false))
                    .orElseThrow(() -> e);
        }
    }

    /**
     * A repeated reference with the same content returns the parcel (idempotent retry). With different
     * content it is refused: the reference was already used for another order, and silently returning
     * the first one would hide the change. Parcels created before fingerprints keep the old behaviour.
     */
    private static Parcel sameOrder(Parcel parcel, NewMerchantParcel order) {
        String stored = parcel.getOrderFingerprint();
        if (stored != null && !stored.equals(OrderFingerprint.of(order))) {
            throw new ParcelConflictException("This order reference was already used for a different order");
        }
        return parcel;
    }

    private Optional<Parcel> findExisting(NewMerchantParcel order) {
        // Parcel.forMerchant stores the reference trimmed
        return parcels.findByMerchantIdAndMerchantOrderReference(order.merchantId(),
                order.merchantOrderReference().trim());
    }

    private static CreatedParcel toCreated(Parcel parcel, boolean newlyCreated) {
        return new CreatedParcel(parcel.getId(), parcel.getTrackingNumber(), parcel.getStatus(),
                parcel.getFeeAmount(), parcel.getFeeCurrency(), parcel.getCreatedAt(), newlyCreated);
    }
}
