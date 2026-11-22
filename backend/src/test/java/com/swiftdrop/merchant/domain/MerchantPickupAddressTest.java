package com.swiftdrop.merchant.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class MerchantPickupAddressTest {

    private static final UUID ZONE = UUID.randomUUID();

    @Test
    void keepsTrimmedPartsAndTurnsBlankOptionalPartsIntoNull() {
        MerchantPickupAddress address = new MerchantPickupAddress(
                "  StrideLK Warehouse ", " +94912234567 ", " 12 Warehouse Rd ", "   ", " Galle ", "  ", ZONE);

        assertThat(address.getContactName()).isEqualTo("StrideLK Warehouse");
        assertThat(address.getPhone()).isEqualTo("+94912234567");
        assertThat(address.getLine1()).isEqualTo("12 Warehouse Rd");
        assertThat(address.getLine2()).isNull();
        assertThat(address.getCity()).isEqualTo("Galle");
        assertThat(address.getPostalCode()).isNull();
        assertThat(address.getZoneId()).isEqualTo(ZONE);
    }

    @Test
    void requiresContactLine1CityAndZone() {
        assertThatThrownBy(() -> new MerchantPickupAddress(" ", "+94912234567", "Rd", null, "Galle", null, ZONE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("contactName");
        assertThatThrownBy(() -> new MerchantPickupAddress("W", "+94912234567", " ", null, "Galle", null, ZONE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("line1");
        assertThatThrownBy(() -> new MerchantPickupAddress("W", "+94912234567", "Rd", null, "", null, ZONE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("city");
        assertThatThrownBy(() -> new MerchantPickupAddress("W", "+94912234567", "Rd", null, "Galle", null, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("zoneId");
    }

    @Test
    void requiresAnInternationalPhoneNumberAsOrderAddressesDo() {
        assertThatThrownBy(() -> new MerchantPickupAddress("W", "0912234567", "Rd", null, "Galle", null, ZONE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("international format")
                .hasMessageNotContaining("0912234567");
    }

    @Test
    void enforcesTheColumnLimits() {
        assertThatThrownBy(() -> new MerchantPickupAddress("W", "+94912234567", "Rd", null, "x".repeat(101), null,
                ZONE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("city must be at most 100");
        assertThatThrownBy(() -> new MerchantPickupAddress("W", "+94912234567", "Rd", null, "Galle", "12345678901",
                ZONE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("postalCode must be at most 10");
    }

    @Test
    void toStringHidesThePersonalData() {
        MerchantPickupAddress address = new MerchantPickupAddress(
                "StrideLK Warehouse", "+94912234567", "12 Warehouse Rd", null, "Galle", null, ZONE);

        assertThat(address.toString()).doesNotContain("Warehouse", "+9491", "Galle");
    }

    @Test
    void merchantStartsWithoutAnAddressAndCanSetAndReplaceIt() {
        Merchant merchant = new Merchant(UUID.randomUUID(), "Acme", "REG-1", "a@a.com", "+94771234567");
        assertThat(merchant.getPickupAddress()).isNull();

        MerchantPickupAddress first = new MerchantPickupAddress("W", "+94912234567", "Rd 1", null, "Galle", null, ZONE);
        MerchantPickupAddress second = new MerchantPickupAddress("W", "+94912234567", "Rd 2", null, "Matara", null,
                ZONE);
        merchant.changePickupAddress(first);
        merchant.changePickupAddress(second);

        assertThat(merchant.getPickupAddress()).isSameAs(second);
        assertThatThrownBy(() -> merchant.changePickupAddress(null)).isInstanceOf(NullPointerException.class);
    }
}
