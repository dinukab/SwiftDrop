package com.swiftdrop.merchant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
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
import com.swiftdrop.pricing.api.ParcelSize;

@ExtendWith(MockitoExtension.class)
class MerchantOrderServiceTest {

    @Mock
    private ParcelCreation parcelCreation;

    private MerchantOrderService service;

    @BeforeEach
    void setUp() {
        service = new MerchantOrderService(parcelCreation);
    }

    @Test
    void placeOrderDelegatesToParcelCreation() {
        UUID merchantId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        DeliveryContact pickup = new DeliveryContact("Sender", "077123", "Line1", null, "Colombo", UUID.randomUUID(), "00100");
        DeliveryContact dropoff = new DeliveryContact("Recipient", "077456", "Line2", null, "Kandy", UUID.randomUUID(), "20000");

        CreatedParcel created = new CreatedParcel(
                parcelId, "SD1234567890", ParcelStatus.CREATED, new BigDecimal("650.00"), "LKR", Instant.now(), true
        );

        when(parcelCreation.createMerchantParcel(any())).thenReturn(created);

        CreatedParcel result = service.placeOrder(
                merchantId, "ORDER-101", pickup, dropoff, "MEDIUM", 1500, "Books"
        );

        assertThat(result.parcelId()).isEqualTo(parcelId);
        assertThat(result.trackingNumber()).isEqualTo("SD1234567890");
        assertThat(result.newlyCreated()).isTrue();

        ArgumentCaptor<NewMerchantParcel> captor = ArgumentCaptor.forClass(NewMerchantParcel.class);
        verify(parcelCreation).createMerchantParcel(captor.capture());
        NewMerchantParcel sent = captor.getValue();
        assertThat(sent.merchantId()).isEqualTo(merchantId);
        assertThat(sent.merchantOrderReference()).isEqualTo("ORDER-101");
        assertThat(sent.size()).isEqualTo(ParcelSize.MEDIUM);
        assertThat(sent.weightGrams()).isEqualTo(1500);
        assertThat(sent.description()).isEqualTo("Books");
    }
}

