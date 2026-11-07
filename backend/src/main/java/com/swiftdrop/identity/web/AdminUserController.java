package com.swiftdrop.identity.web;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('DISPATCHER_ADMIN')") 
public class AdminUserController {


    @GetMapping
    public ResponseEntity<String> getAllUsers() {
        return ResponseEntity.ok("List of all users for Admin");
    }

    @PatchMapping("/{userId}/status")
    public ResponseEntity<Void> updateUserStatus(
            @PathVariable UUID userId,
            @RequestParam boolean enabled) {
        // User status update logic
        return ResponseEntity.noContent().build();
    }
}