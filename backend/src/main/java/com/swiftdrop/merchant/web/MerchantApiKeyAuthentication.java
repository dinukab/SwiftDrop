package com.swiftdrop.merchant.web;

import java.util.List;
import java.util.UUID;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Authentication token for external merchant systems authenticated via an API key.
 */
public class MerchantApiKeyAuthentication extends AbstractAuthenticationToken {

    private final UUID merchantId;

    public MerchantApiKeyAuthentication(UUID merchantId) {
        super(List.of(new SimpleGrantedAuthority("ROLE_MERCHANT_API")));
        this.merchantId = merchantId;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public UUID getPrincipal() {
        return merchantId;
    }

    public UUID getMerchantId() {
        return merchantId;
    }
}

