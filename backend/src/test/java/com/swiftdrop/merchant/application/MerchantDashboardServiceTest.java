package com.swiftdrop.merchant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.swiftdrop.delivery.api.PageRequest;
import com.swiftdrop.delivery.api.PageResult;
import com.swiftdrop.delivery.api.ParcelQueries;
import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelSummary;
import com.swiftdrop.pricing.api.ParcelSize;

@ExtendWith(MockitoExtension.class)
class MerchantDashboardServiceTest {

    @Mock
    private ParcelQueries parcelQueries;

    private MerchantDashboardService service;

    @BeforeEach
    void setUp() {
        service = new MerchantDashboardService(parcelQueries);
    }

    @Test
    void findOrdersDelegatesToParcelQueries() {
        UUID merchantId = UUID.randomUUID();
        ParcelSummary summary = new ParcelSummary(
                UUID.randomUUID(), "SD1234567890", ParcelStatus.IN_TRANSIT,
                ParcelSize.SMALL, 500, ParcelSource.MERCHANT, null, merchantId, "REF-1",
                UUID.randomUUID(), Instant.now()
        );
        PageResult<ParcelSummary> pageResult = new PageResult<>(List.of(summary), 0, 20, 1, 1);

        when(parcelQueries.findParcelsByMerchant(eq(merchantId), eq(ParcelStatus.IN_TRANSIT), any(PageRequest.class)))
                .thenReturn(pageResult);

        PageResult<ParcelSummary> result = service.findOrders(merchantId, ParcelStatus.IN_TRANSIT, 0, 20);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).trackingNumber()).isEqualTo("SD1234567890");
        verify(parcelQueries).findParcelsByMerchant(eq(merchantId), eq(ParcelStatus.IN_TRANSIT), any(PageRequest.class));
    }

    @Test
    void findByReferenceDelegatesToParcelQueries() {
        UUID merchantId = UUID.randomUUID();
        ParcelSummary summary = new ParcelSummary(
                UUID.randomUUID(), "SD1234567890", ParcelStatus.CREATED,
                ParcelSize.LARGE, 2500, ParcelSource.MERCHANT, null, merchantId, "REF-100",
                null, Instant.now()
        );

        when(parcelQueries.findByMerchantReference(merchantId, "REF-100"))
                .thenReturn(Optional.of(summary));

        Optional<ParcelSummary> result = service.findByReference(merchantId, "  REF-100  ");

        assertThat(result).isPresent();
        assertThat(result.get().merchantOrderReference()).isEqualTo("REF-100");
    }
}

