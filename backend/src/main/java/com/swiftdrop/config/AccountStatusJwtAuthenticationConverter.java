package com.swiftdrop.config;

import java.util.UUID;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import com.swiftdrop.identity.api.UserDirectory;
import com.swiftdrop.identity.api.UserSummary;

/**
 * Wraps the standard {@link JwtAuthenticationConverter} and re-validates account state on every
 * request, so disabling a user takes effect immediately instead of waiting for token expiry.
 * Costs one indexed user lookup per authenticated request.
 */
class AccountStatusJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtAuthenticationConverter delegate;
    private final UserDirectory users;

    AccountStatusJwtAuthenticationConverter(JwtAuthenticationConverter delegate, UserDirectory users) {
        this.delegate = delegate;
        this.users = users;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        AbstractAuthenticationToken authentication = delegate.convert(jwt);
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (RuntimeException e) {
            throw new BadCredentialsException("Malformed token subject");
        }
        UserSummary user = users.findUser(userId)
                .orElseThrow(() -> new BadCredentialsException("Unknown account"));
        if (!user.enabled()) {
            throw new BadCredentialsException("Account is disabled");
        }
        return authentication;
    }
}
