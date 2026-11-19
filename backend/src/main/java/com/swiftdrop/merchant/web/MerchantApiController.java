package com.swiftdrop.merchant.web;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.swiftdrop.delivery.api.CreatedParcel;
import com.swiftdrop.merchant.application.MerchantOrderService;
import com.swiftdrop.merchant.application.MerchantOrderStatusService;

/**
 * Secured API for merchant e-commerce platforms: create delivery orders and read their status (MER-2).
 * This is the store-facing API; it is separate from the customer API, because the store's own buyers
 * have no SwiftDrop account. The store links a delivery back to its buyer by the order reference.
 *
 * <p>Authenticated exclusively via {@code X-API-Key} header through {@link com.swiftdrop.merchant.api.MerchantApiKeyFilter}.
 */
@RestController
@Lazy
@RequestMapping("/api/v1/merchant-api/orders")
@PreAuthorize("hasRole('MERCHANT_API')")
class MerchantApiController {

    private final MerchantOrderService orderService;
    private final MerchantOrderStatusService statusService;

    MerchantApiController(MerchantOrderService orderService, MerchantOrderStatusService statusService) {
        this.orderService = orderService;
        this.statusService = statusService;
    }

    /**
     * Current status and full sequenced history of one of the calling merchant's orders. 404 if the
     * reference is not one of this merchant's orders (it cannot be used to probe other merchants).
     */
    @GetMapping("/{merchantOrderReference}")
    MerchantOrderStatusResponse getOrder(
            @AuthenticationPrincipal UUID merchantId,
            @PathVariable String merchantOrderReference
    ) {
        return statusService.find(merchantId, merchantOrderReference)
                .map(MerchantOrderStatusResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    @PostMapping
    ResponseEntity<MerchantOrderResponse> createOrder(
            @AuthenticationPrincipal UUID merchantId,
            @Valid @RequestBody CreateMerchantOrderRequest request
    ) {
        CreatedParcel created = orderService.placeOrder(
                merchantId,
                request.merchantOrderReference(),
                request.pickup().toDeliveryContact(),
                request.dropoff().toDeliveryContact(),
                request.size(),
                request.weightGrams(),
                request.description()
        );

        MerchantOrderResponse responseBody = MerchantOrderResponse.from(created);

        if (created.newlyCreated()) {
            return ResponseEntity.created(URI.create("/api/v1/parcels/" + created.parcelId()))
                    .body(responseBody);
        } else {
            // Idempotent retry of an existing reference returns 200 OK
            return ResponseEntity.ok(responseBody);
        }
    }
}

