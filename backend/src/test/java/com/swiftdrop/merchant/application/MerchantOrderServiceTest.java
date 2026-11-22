package com.swiftdrop.merchant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.swiftdrop.delivery.api.CreatedParcel;
import com.swiftdrop.delivery.api.DeliveryContact;
import com.swiftdrop.delivery.api.NewMerchantParcel;
import com.swiftdrop.delivery.api.ParcelCreation;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantPickupAddress;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.pricing.api.ParcelSize;

@ExtendWith(MockitoExtension.class)
class MerchantOrderServiceTest {

    private static final UUID MERCHANT_ID = UUID.randomUUID();
    private static final UUID SOUTHERN = UUID.randomUUID();
    private static final UUID WESTERN = UUID.randomUUID();
    private static final DeliveryContact DROPOFF =
            new DeliveryContact("Recipient", "+94779876543", "Line2", null, "Kandy", UUID.randomUUID(), "20000");

    @Mock
    private ParcelCreation parcelCreation;

    @Mock
    private MerchantRepository merchants;

    private MerchantOrderService service;

    @BeforeEach
    void setUp() {
        service = new MerchantOrderService(parcelCreation, merchants);
    }

    private static Merchant merchant(MerchantPickupAddress address) {
        Merchant merchant = new Merchant(UUID.randomUUID(), "Shop", "REG-1", "s@s.com", "+94771234567");
        if (address != null) {
            merchant.changePickupAddress(address);
        }
        return merchant;
    }

    private static MerchantPickupAddress galleWarehouse() {
        return new MerchantPickupAddress("StrideLK Warehouse", "+94912234567", "12 Warehouse Rd", "Gate 2", "Galle",
                "80000", SOUTHERN);
    }

    private static CreatedParcel created() {
        return new CreatedParcel(UUID.randomUUID(), "SD1234567890", ParcelStatus.CREATED, new BigDecimal("650.00"),
                "LKR", Instant.now(), true);
    }

    private NewMerchantParcel sentToDelivery() {
        ArgumentCaptor<NewMerchantParcel> captor = ArgumentCaptor.forClass(NewMerchantParcel.class);
        verify(parcelCreation).createMerchantParcel(captor.capture());
        return captor.getValue();
    }

    @Test
    void placeOrderDelegatesToParcelCreation() {
        DeliveryContact pickup =
                new DeliveryContact("Sender", "+94771230000", "Line1", null, "Colombo", WESTERN, "00100");
        when(merchants.findById(MERCHANT_ID)).thenReturn(Optional.of(merchant(null)));
        when(parcelCreation.createMerchantParcel(any())).thenReturn(created());

        CreatedParcel result = service.placeOrder(MERCHANT_ID, "ORDER-101", pickup, DROPOFF, "MEDIUM", 1500, "Books");

        assertThat(result.trackingNumber()).isEqualTo("SD1234567890");
        NewMerchantParcel sent = sentToDelivery();
        assertThat(sent.merchantId()).isEqualTo(MERCHANT_ID);
        assertThat(sent.merchantOrderReference()).isEqualTo("ORDER-101");
        assertThat(sent.size()).isEqualTo(ParcelSize.MEDIUM);
        assertThat(sent.weightGrams()).isEqualTo(1500);
        assertThat(sent.description()).isEqualTo("Books");
    }

    @Test
    void omittedPickupUsesTheRegisteredAddress() {
        when(merchants.findById(MERCHANT_ID)).thenReturn(Optional.of(merchant(galleWarehouse())));
        when(parcelCreation.createMerchantParcel(any())).thenReturn(created());

        service.placeOrder(MERCHANT_ID, "ORDER-102", null, DROPOFF, "SMALL", 500, null);

        assertThat(sentToDelivery().pickup()).isEqualTo(new DeliveryContact("StrideLK Warehouse", "+94912234567",
                "12 Warehouse Rd", "Gate 2", "Galle", SOUTHERN, "80000"));
    }

    @Test
    void pickupInTheRegisteredProvinceIsTakenAsSent() {
        DeliveryContact otherGate =
                new DeliveryContact("Night desk", "+94912239999", "14 Warehouse Rd", null, "Galle", SOUTHERN, null);
        when(merchants.findById(MERCHANT_ID)).thenReturn(Optional.of(merchant(galleWarehouse())));
        when(parcelCreation.createMerchantParcel(any())).thenReturn(created());

        service.placeOrder(MERCHANT_ID, "ORDER-103", otherGate, DROPOFF, "SMALL", 500, null);

        assertThat(sentToDelivery().pickup()).isEqualTo(otherGate);
    }

    @Test
    void pickupInAnotherProvinceIsRefused() {
        DeliveryContact colombo =
                new DeliveryContact("Sender", "+94771230000", "Line1", null, "Colombo", WESTERN, null);
        when(merchants.findById(MERCHANT_ID)).thenReturn(Optional.of(merchant(galleWarehouse())));

        assertThatThrownBy(() -> service.placeOrder(MERCHANT_ID, "ORDER-104", colombo, DROPOFF, "SMALL", 500, null))
                .isInstanceOf(PickupZoneMismatchException.class);
        verifyNoInteractions(parcelCreation);
    }

    @Test
    void merchantWithoutAddressMustSendAPickup() {
        when(merchants.findById(MERCHANT_ID)).thenReturn(Optional.of(merchant(null)));

        assertThatThrownBy(() -> service.placeOrder(MERCHANT_ID, "ORDER-105", null, DROPOFF, "SMALL", 500, null))
                .isInstanceOf(PickupAddressRequiredException.class);
        verifyNoInteractions(parcelCreation);
    }

    @Test
    void merchantWithoutAddressIsNotCheckedYet() {
        DeliveryContact anywhere =
                new DeliveryContact("Sender", "+94771230000", "Line1", null, "Jaffna", UUID.randomUUID(), null);
        when(merchants.findById(MERCHANT_ID)).thenReturn(Optional.of(merchant(null)));
        when(parcelCreation.createMerchantParcel(any())).thenReturn(created());

        service.placeOrder(MERCHANT_ID, "ORDER-106", anywhere, DROPOFF, "SMALL", 500, null);

        assertThat(sentToDelivery().pickup()).isEqualTo(anywhere);
    }

    @Test
    void unknownMerchantIsRefused() {
        when(merchants.findById(MERCHANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.placeOrder(MERCHANT_ID, "ORDER-107", null, DROPOFF, "SMALL", 500, null))
                .isInstanceOf(MerchantNotFoundException.class)
                .hasMessageNotContaining(MERCHANT_ID.toString());
        verifyNoInteractions(parcelCreation);
    }
}
