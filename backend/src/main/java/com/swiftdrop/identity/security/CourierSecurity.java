package com.swiftdrop.identity.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("courierSecurity")
public class CourierSecurity {

    public boolean isAssigned(UUID parcelId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        String currentUserId = authentication.getName();

    
        return currentUserId != null && !currentUserId.isBlank();
    }
}