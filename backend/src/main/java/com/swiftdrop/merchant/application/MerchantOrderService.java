package com.swiftdrop.merchant.application;

import java.util.Objects;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.swiftdrop.delivery.api.CreatedParcel;
import com.swiftdrop.delivery.api.DeliveryContact;
import com.swiftdrop.delivery.api.NewMerchantParcel;
import com.swiftdrop.delivery.api.ParcelCreation;
import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantPickupAddress;
import com.swiftdrop.merchant.domain.MerchantRepository;

/**
 * Handles automated order placement for merchants via secured API (MER-2).
 *
 * <p>Delegates parcel aggregate creation and outbox recording to {@link ParcelCreation} in the
 * {@code delivery} module (ADR 0003).
 */
@Service
@Lazy
public class MerchantOrderService {

    private final ParcelCreation parcelCreation;
    private final MerchantRepository merchants;

    public MerchantOrderService(ParcelCreation parcelCreation, MerchantRepository merchants) {
        this.parcelCreation = parcelCreation;
        this.merchants = merchants;
    }

    /**
     * Places an API order. The pickup is resolved against the merchant's registered pickup address:
     * <ul>
     *   <li>registered address, no {@code pickup} sent: the registered address is used;</li>
     *   <li>registered address, {@code pickup} sent: its zone must be the registered zone (the text may differ, for
     *       example another gate of the same warehouse);</li>
     *   <li>no registered address (merchants registered before it was required): {@code pickup} is required and taken
     *       as sent, as before.</li>
     * </ul>
     * Not transactional itself: {@link ParcelCreation} writes in its own transaction.
     *
     * @param pickup the pickup sent with the order, or {@code null} to use the registered address
     * @throws PickupZoneMismatchException if the pickup is in another province than the registered address
     * @throws PickupAddressRequiredException if no pickup is sent and none is registered
     */
    public CreatedParcel placeOrder(
            UUID merchantId,
            String merchantOrderReference,
            DeliveryContact pickup,
            DeliveryContact dropoff,
            String sizeName,
            int weightGrams,
            String description
    ) {
        Objects.requireNonNull(merchantId, "merchantId");
        Objects.requireNonNull(merchantOrderReference, "merchantOrderReference");
        Objects.requireNonNull(dropoff, "dropoff");
        Objects.requireNonNull(sizeName, "sizeName");
        // The API key filter only authenticates keys of existing merchants
        Merchant merchant = merchants.findById(merchantId)
                .orElseThrow(() -> new MerchantNotFoundException("Merchant not found"));

        NewMerchantParcel order = createOrder(
                merchantId,
                merchantOrderReference.trim(),
                resolvePickup(merchant, pickup),
                dropoff,
                sizeName.trim().toUpperCase(),
                weightGrams,
                description
        );

        return parcelCreation.createMerchantParcel(order);
    }

    private static DeliveryContact resolvePickup(Merchant merchant, DeliveryContact sent) {
        MerchantPickupAddress registered = merchant.getPickupAddress();
        if (registered == null) {
            if (sent == null) {
                throw new PickupAddressRequiredException();
            }
            return sent;
        }
        if (sent == null) {
            return new DeliveryContact(registered.getContactName(), registered.getPhone(), registered.getLine1(),
                    registered.getLine2(), registered.getCity(), registered.getZoneId(), registered.getPostalCode());
        }
        if (!registered.getZoneId().equals(sent.zoneId())) {
            throw new PickupZoneMismatchException();
        }
        return sent;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static NewMerchantParcel createOrder(
            UUID merchantId,
            String merchantOrderReference,
            DeliveryContact pickup,
            DeliveryContact dropoff,
            String sizeName,
            int weightGrams,
            String description
    ) {
        try {
            var constructor = NewMerchantParcel.class.getDeclaredConstructors()[0];
            Class<?> sizeType = constructor.getParameterTypes()[4];
            Object size = Enum.valueOf((Class<Enum>) sizeType.asSubclass(Enum.class), sizeName);
            return (NewMerchantParcel) constructor.newInstance(
                    merchantId,
                    merchantOrderReference,
                    pickup,
                    dropoff,
                    size,
                    weightGrams,
                    description
            );
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalArgumentException("Invalid order parameters: " + e.getMessage(), e);
        }
    }
}
