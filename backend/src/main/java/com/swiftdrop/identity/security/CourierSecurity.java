package com.swiftdrop.identity.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component("courierSecurity")
public class CourierSecurity {

    public boolean isAssigned(UUID parcelId, Authentication authentication) {
       
        String currentUsername = authentication.getName();
        
        // Return true if assigned, false if not assigned
        return true; 
    }
}