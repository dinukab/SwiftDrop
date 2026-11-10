package com.swiftdrop.merchant.web;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.swiftdrop.delivery.api.PageResult;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelSummary;
import com.swiftdrop.merchant.application.MerchantApprovalService;
import com.swiftdrop.merchant.application.MerchantDashboardService;
import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.shared.web.PageLimit;
import com.swiftdrop.shared.web.PageResponse;

/**
 * Endpoints for merchants to view and filter their delivery orders in the portal dashboard (MER-4).
 */
@RestController
@Lazy
@RequestMapping("/api/v1/merchants/me/orders")
@PreAuthorize("hasRole('MERCHANT')")
class MerchantDashboardController {

    private final MerchantDashboardService dashboardService;
    private final MerchantApprovalService approvalService;

    MerchantDashboardController(MerchantDashboardService dashboardService, MerchantApprovalService approvalService) {
        this.dashboardService = dashboardService;
        this.approvalService = approvalService;
    }

    @GetMapping
    PageResponse<MerchantDashboardItemResponse> listOrders(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String status
    ) {
        Merchant merchant = getMerchant(jwt);
        ParcelStatus statusFilter = null;
        if (status != null && !status.isBlank()) {
            statusFilter = ParcelStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        }

        int pageNum = page != null ? PageLimit.clampPage(page) : PageLimit.DEFAULT_PAGE_NUMBER;
        int pageSize = size != null ? PageLimit.clampSize(size) : PageLimit.DEFAULT_PAGE_SIZE;

        PageResult<ParcelSummary> result = dashboardService.findOrders(merchant.getId(), statusFilter, pageNum, pageSize);
        List<MerchantDashboardItemResponse> items = result.items().stream()
                .map(MerchantDashboardItemResponse::from)
                .toList();

        return PageResponse.of(items, result.page(), result.size(), result.totalItems());
    }

    @GetMapping("/{orderReference}")
    MerchantDashboardItemResponse getByReference(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String orderReference
    ) {
        Merchant merchant = getMerchant(jwt);
        ParcelSummary parcel = dashboardService.findByReference(merchant.getId(), orderReference)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + orderReference));

        return MerchantDashboardItemResponse.from(parcel);
    }

    private Merchant getMerchant(Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return approvalService.getMerchantByUserId(userId);
    }
}

