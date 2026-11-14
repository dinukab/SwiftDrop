package com.swiftdrop.merchant.web;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.delivery.api.CreatedParcel;
import com.swiftdrop.merchant.application.MerchantOrderService;

/**
 * Secured API endpoint for merchant e-commerce platforms to automatically create delivery orders (MER-2).
 *
 * <p>Authenticated exclusively via {@code X-API-Key} header through {@link com.swiftdrop.merchant.api.MerchantApiKeyFilter}.
 */
@RestController
@Lazy
@RequestMapping("/api/v1/merchant-api/orders")
@PreAuthorize("hasRole('MERCHANT_API')")
class MerchantApiController {

    private final MerchantOrderService orderService;

    MerchantApiController(MerchantOrderService orderService) {
        this.orderService = orderService;
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

