package com.swiftdrop.delivery.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.swiftdrop.delivery.api.DeliveryContact;
import com.swiftdrop.delivery.api.NewMerchantParcel;
import com.swiftdrop.pricing.api.ParcelSize;

class OrderFingerprintTest {

    private static final UUID MERCHANT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID ZONE = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    @Test
    void theSameOrderHasTheSameFingerprintEvenWithStrayWhitespace() {
        NewMerchantParcel first = order("ORD-1", "Nimal", "Colombo", 500, "Watch");
        NewMerchantParcel again = order("ORD-1", "  Nimal ", "Colombo", 500, "  Watch  ");

        assertThat(OrderFingerprint.of(again)).isEqualTo(OrderFingerprint.of(first));
    }

    @Test
    void theOrderReferenceIsNotPartOfTheFingerprint() {
        // The reference is the identity; the fingerprint is about the content
        assertThat(OrderFingerprint.of(order("ORD-1", "Nimal", "Colombo", 500, "Watch")))
                .isEqualTo(OrderFingerprint.of(order("ORD-2", "Nimal", "Colombo", 500, "Watch")));
    }

    @Test
    void anyChangedFieldChangesTheFingerprint() {
        String base = OrderFingerprint.of(order("ORD-1", "Nimal", "Colombo", 500, "Watch"));

        assertThat(OrderFingerprint.of(order("ORD-1", "Kamal", "Colombo", 500, "Watch"))).isNotEqualTo(base);
        assertThat(OrderFingerprint.of(order("ORD-1", "Nimal", "Kandy", 500, "Watch"))).isNotEqualTo(base);
        assertThat(OrderFingerprint.of(order("ORD-1", "Nimal", "Colombo", 501, "Watch"))).isNotEqualTo(base);
        assertThat(OrderFingerprint.of(order("ORD-1", "Nimal", "Colombo", 500, "Shoes"))).isNotEqualTo(base);
    }

    @Test
    void fieldBoundariesCannotBeMovedToCollideTwoOrders() {
        // "ab" + "c" must not equal "a" + "bc" once the fields are joined
        assertThat(OrderFingerprint.of(order("ORD-1", "ab", "c", 500, "x")))
                .isNotEqualTo(OrderFingerprint.of(order("ORD-1", "a", "bc", 500, "x")));
    }

    @Test
    void theFingerprintIsASha256HexDigest() {
        String fingerprint = OrderFingerprint.of(order("ORD-1", "Nimal", "Colombo", 500, "Watch"));

        assertThat(fingerprint).hasSize(64).matches("[0-9a-f]{64}");
    }

    private static NewMerchantParcel order(String reference, String contact, String city, int grams,
            String description) {
        DeliveryContact pickup = new DeliveryContact("Store", "+94771234567", "1 Main St", null, "Colombo", ZONE,
                null);
        DeliveryContact dropoff = new DeliveryContact(contact, "+94779876543", "45 Park Rd", null, city, ZONE, null);
        return new NewMerchantParcel(MERCHANT, reference, pickup, dropoff, ParcelSize.SMALL, grams, description);
    }
}
