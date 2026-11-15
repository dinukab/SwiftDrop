package com.swiftdrop.merchant.web;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.swiftdrop.delivery.api.PageResult;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelSummary;
import com.swiftdrop.merchant.application.MerchantDashboardService;
import com.swiftdrop.merchant.application.MerchantOnboardingService;
import com.swiftdrop.merchant.application.MerchantOrderActionService;
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
    private final MerchantOnboardingService onboarding;
    private final MerchantOrderActionService actionService;

    MerchantDashboardController(MerchantDashboardService dashboardService, MerchantOnboardingService onboarding,
            MerchantOrderActionService actionService) {
        this.dashboardService = dashboardService;
        this.onboarding = onboarding;
        this.actionService = actionService;
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

    /**
     * Holds the order before pickup, for example while the goods are still being prepared. Returns the
     * order as it is afterwards. 409 if it is past {@code CREATED}; 404 if it is not this merchant's.
     */
    @PostMapping("/{orderReference}/holds")
    MerchantDashboardItemResponse pause(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String orderReference,
            @Valid @RequestBody(required = false) OrderActionRequest body
    ) {
        Merchant merchant = getMerchant(jwt);
        actionService.pause(merchant.getId(), userId(jwt), orderReference, reason(body));
        return currentOrder(merchant, orderReference);
    }

    /** Releases a hold, returning the order to {@code CREATED}. 409 unless it is {@code PAUSED}. */
    @DeleteMapping("/{orderReference}/holds")
    MerchantDashboardItemResponse resume(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String orderReference,
            @Valid @RequestBody(required = false) OrderActionRequest body
    ) {
        Merchant merchant = getMerchant(jwt);
        actionService.resume(merchant.getId(), userId(jwt), orderReference, reason(body));
        return currentOrder(merchant, orderReference);
    }

    /** Cancels the order before pickup. Final. 409 if it is past {@code CREATED}/{@code PAUSED}. */
    @PostMapping("/{orderReference}/cancellations")
    MerchantDashboardItemResponse cancel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String orderReference,
            @Valid @RequestBody(required = false) OrderActionRequest body
    ) {
        Merchant merchant = getMerchant(jwt);
        actionService.cancel(merchant.getId(), userId(jwt), orderReference, reason(body));
        return currentOrder(merchant, orderReference);
    }

    private MerchantDashboardItemResponse currentOrder(Merchant merchant, String orderReference) {
        return dashboardService.findByReference(merchant.getId(), orderReference)
                .map(MerchantDashboardItemResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private static String reason(OrderActionRequest body) {
        return (body != null ? body : OrderActionRequest.NONE).reason();
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    /** Orders are a merchant capability, so they require an APPROVED merchant (D-22). */
    private Merchant getMerchant(Jwt jwt) {
        return onboarding.requireApproved(UUID.fromString(jwt.getSubject()));
    }
}

