package com.swiftdrop.merchant.application;

import java.util.Objects;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.swiftdrop.delivery.api.CreatedParcel;
import com.swiftdrop.delivery.api.DeliveryContact;
import com.swiftdrop.delivery.api.NewMerchantParcel;
import com.swiftdrop.delivery.api.ParcelCreation;

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

    public MerchantOrderService(ParcelCreation parcelCreation) {
        this.parcelCreation = parcelCreation;
    }

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
        Objects.requireNonNull(pickup, "pickup");
        Objects.requireNonNull(dropoff, "dropoff");
        Objects.requireNonNull(sizeName, "sizeName");

        NewMerchantParcel order = createOrder(
                merchantId,
                merchantOrderReference.trim(),
                pickup,
                dropoff,
                sizeName.trim().toUpperCase(),
                weightGrams,
                description
        );

        return parcelCreation.createMerchantParcel(order);
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
