package com.swiftdrop.merchant.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.swiftdrop.delivery.api.PageRequest;
import com.swiftdrop.delivery.api.PageResult;
import com.swiftdrop.delivery.api.ParcelQueries;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelSummary;
import com.swiftdrop.shared.web.PageLimit;
import com.swiftdrop.shared.web.PageResponse;

/**
 * Handles dashboard data queries for merchants to view their shipments and tracking states (MER-4).
 *
 * <p>Delegates cross-module queries to {@link ParcelQueries} in the {@code delivery} module (ADR 0003).
 */
@Service
@Lazy
public class MerchantDashboardService {

    private final ParcelQueries parcelQueries;

    public MerchantDashboardService(ParcelQueries parcelQueries) {
        this.parcelQueries = parcelQueries;
    }

    public PageResult<ParcelSummary> findOrders(UUID merchantId, ParcelStatus statusFilter, int page, int size) {
        Objects.requireNonNull(merchantId, "merchantId");
        int safePage = PageLimit.clampPage(page);
        int safeSize = PageLimit.clampSize(size);

        return parcelQueries.findParcelsByMerchant(
                merchantId,
                statusFilter,
                new PageRequest(safePage, safeSize)
        );
    }

    public Optional<ParcelSummary> findByReference(UUID merchantId, String orderReference) {
        Objects.requireNonNull(merchantId, "merchantId");
        Objects.requireNonNull(orderReference, "orderReference");

        return parcelQueries.findByMerchantReference(merchantId, orderReference.trim());
    }
}

