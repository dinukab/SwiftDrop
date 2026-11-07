package com.swiftdrop.identity.web;

import com.swiftdrop.identity.api.MerchantApprovalRequest;
import com.swiftdrop.identity.api.UserStatusUpdateRequest;
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

    // Dependency Injection 
    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    // 1. get list of all users
    @GetMapping("/users")
    public ResponseEntity<String> getAllUsers() {
        return ResponseEntity.ok("List of all users for Admin");
    }

    // 2. User Status Change (Enable / Disable Account)
    @PatchMapping("/users/{userId}/status")
    public ResponseEntity<Void> updateUserStatus(
            @PathVariable UUID userId,
            @RequestBody UserStatusUpdateRequest request) {
        adminUserService.updateUserStatus(userId, request);
        return ResponseEntity.noContent().build();
    }

    // 3. Merchant Registration Approve / Reject 
    @PatchMapping("/merchants/{merchantId}/approve")
    public ResponseEntity<Void> approveMerchant(
            @PathVariable UUID merchantId,
            @RequestBody MerchantApprovalRequest request) {
        adminUserService.approveMerchant(merchantId, request);
        return ResponseEntity.noContent().build();
    }
}