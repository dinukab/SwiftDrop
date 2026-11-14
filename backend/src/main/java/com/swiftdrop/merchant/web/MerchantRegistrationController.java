package com.swiftdrop.merchant.web;

import java.net.URI;

import jakarta.validation.Valid;

import org.springframework.context.annotation.Lazy;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.merchant.application.MerchantRegistrationService;
import com.swiftdrop.merchant.domain.Merchant;

/**
 * Public registration endpoint for prospective merchants (MER-1).
 */
@RestController
@Lazy
@RequestMapping("/api/v1/merchants")
class MerchantRegistrationController {

    private final MerchantRegistrationService registrationService;

    MerchantRegistrationController(MerchantRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/register")
    ResponseEntity<MerchantResponse> register(@Valid @RequestBody RegisterMerchantRequest request) {
        Merchant merchant = registrationService.register(request.toCommand());
        return ResponseEntity.created(URI.create("/api/v1/merchants/" + merchant.getId()))
                .body(MerchantResponse.from(merchant));
    }
}

