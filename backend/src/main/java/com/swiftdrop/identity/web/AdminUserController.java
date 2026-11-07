package com.swiftdrop.identity.web;

import com.swiftdrop.identity.api.MerchantApprovalRequest;
import com.swiftdrop.identity.application.AdminUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('DISPATCHER_ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    // Constructor (No return type)
    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    // Merchant Registration Approve / Reject (PATCH /api/v1/admin/merchants/{merchantId}/approve)
    @PatchMapping("/merchants/{merchantId}/approve")
    public ResponseEntity<Void> approveMerchant(
            @PathVariable UUID merchantId,
            @RequestBody MerchantApprovalRequest request) {
        adminUserService.approveMerchant(merchantId, request);
        return ResponseEntity.noContent().build();
    }
}