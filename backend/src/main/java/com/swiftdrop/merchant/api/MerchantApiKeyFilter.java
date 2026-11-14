package com.swiftdrop.merchant.api;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.swiftdrop.merchant.application.MerchantApiKeyService;
import com.swiftdrop.merchant.domain.MerchantApiKeyRepository;

/**
 * Filter that authenticates external merchant API requests via {@code X-API-Key} header (MER-2).
 *
 * <p>Exposed in the {@code api} package so the composition root ({@code config.SecurityConfiguration})
 * can register it into the Spring Security filter chain without violating ADR 0003 boundaries.
 */
@Component
@Lazy
public class MerchantApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-API-Key";
    public static final String API_PATH_PREFIX = "/api/v1/merchant-api/";

    private final ObjectProvider<MerchantApiKeyService> apiKeyServiceProvider;

    public MerchantApiKeyFilter(ObjectProvider<MerchantApiKeyService> apiKeyServiceProvider) {
        this.apiKeyServiceProvider = apiKeyServiceProvider;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith(API_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String rawKey = request.getHeader(HEADER_NAME);
        if (rawKey == null || rawKey.isBlank()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/problem+json");
            response.getWriter().write("{\"status\":401,\"title\":\"Unauthorized\",\"detail\":\"Missing X-API-Key header\"}");
            return;
        }

        MerchantApiKeyService apiKeyService = apiKeyServiceProvider.getIfAvailable();
        if (apiKeyService == null) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }

        Optional<UUID> merchantId = apiKeyService.verifyKey(rawKey.trim());
        if (merchantId.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/problem+json");
            response.getWriter().write("{\"status\":401,\"title\":\"Unauthorized\",\"detail\":\"Invalid or revoked API key\"}");
            return;
        }

        MerchantApiKeyAuthenticationToken authentication = new MerchantApiKeyAuthenticationToken(merchantId.get());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    public static class MerchantApiKeyAuthenticationToken extends AbstractAuthenticationToken {
        private final UUID merchantId;

        public MerchantApiKeyAuthenticationToken(UUID merchantId) {
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
}

