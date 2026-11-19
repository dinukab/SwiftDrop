package com.swiftdrop.merchant.api;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.swiftdrop.merchant.application.ApiRequestSignature;
import com.swiftdrop.merchant.application.MerchantApiKeyService;
import com.swiftdrop.merchant.application.MerchantApiKeyService.Authentication;

/**
 * Filter that authenticates external merchant API requests via {@code X-API-Key} header (MER-2), and
 * checks the request signature (Phase 5).
 *
 * <p>The key is checked first. Only then is the body read (up to {@link #MAX_BODY_BYTES}), because the
 * signature covers it; the controller gets the same bytes again through {@link CachedBodyRequest}.
 * A signature is checked when the request carries one, and is required for every request when
 * {@code swiftdrop.merchant-api.require-signature=true}. A key created before signing has no signing
 * secret, so under the requirement it is refused.
 *
 * <p>Exposed in the {@code api} package so the composition root ({@code config.SecurityConfiguration})
 * can register it into the Spring Security filter chain without violating ADR 0003 boundaries.
 */
@Component
@Lazy
public class MerchantApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-API-Key";
    public static final String API_PATH_PREFIX = "/api/v1/merchant-api/";
    /** Larger bodies are refused before the signature is even checked. Orders are a few hundred bytes. */
    public static final int MAX_BODY_BYTES = 64 * 1024;
    private static final int STATUS_PAYLOAD_TOO_LARGE = 413;

    private final ObjectProvider<MerchantApiKeyService> apiKeyServiceProvider;
    private final boolean requireSignature;

    public MerchantApiKeyFilter(
            ObjectProvider<MerchantApiKeyService> apiKeyServiceProvider,
            @Value("${swiftdrop.merchant-api.require-signature:false}") boolean requireSignature
    ) {
        this.apiKeyServiceProvider = apiKeyServiceProvider;
        this.requireSignature = requireSignature;
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
            writeProblem(response, HttpServletResponse.SC_UNAUTHORIZED, "Missing X-API-Key header");
            return;
        }

        MerchantApiKeyService apiKeyService = apiKeyServiceProvider.getIfAvailable();
        if (apiKeyService == null) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }

        Optional<Authentication> authentication = apiKeyService.authenticate(rawKey.trim());
        if (authentication.isEmpty()) {
            writeProblem(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid or revoked API key");
            return;
        }

        byte[] body = readBody(request);
        if (body == null) {
            writeProblem(response, STATUS_PAYLOAD_TOO_LARGE, "Request body is too large");
            return;
        }

        String timestamp = request.getHeader(ApiRequestSignature.TIMESTAMP_HEADER);
        String signature = request.getHeader(ApiRequestSignature.SIGNATURE_HEADER);
        boolean signed = timestamp != null || signature != null;
        if (requireSignature || signed) {
            String secret = authentication.get().signingSecret();
            boolean valid = secret != null && ApiRequestSignature.verify(secret, timestamp, signature,
                    request.getMethod(), request.getRequestURI(), body, Instant.now());
            if (!valid) {
                // Generic on purpose: the response does not say which part was wrong
                writeProblem(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid request signature");
                return;
            }
        }

        MerchantApiKeyAuthenticationToken token =
                new MerchantApiKeyAuthenticationToken(authentication.get().merchantId());
        SecurityContextHolder.getContext().setAuthentication(token);

        try {
            filterChain.doFilter(new CachedBodyRequest(request, body), response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /** The body, or {@code null} if it is larger than {@link #MAX_BODY_BYTES}. */
    private static byte[] readBody(HttpServletRequest request) throws IOException {
        byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        return body.length > MAX_BODY_BYTES ? null : body;
    }

    private static void writeProblem(HttpServletResponse response, int status, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.getWriter().write("{\"status\":" + status + ",\"title\":\"" + title(status)
                + "\",\"detail\":\"" + detail + "\"}");
    }

    private static String title(int status) {
        return switch (status) {
            case HttpServletResponse.SC_UNAUTHORIZED -> "Unauthorized";
            case STATUS_PAYLOAD_TOO_LARGE -> "Payload Too Large";
            default -> "Error";
        };
    }

    /** Serves a request body that has already been read, so the signature and the controller see the same bytes. */
    static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream stream = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return stream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("Asynchronous reads are not used on this endpoint");
                }

                @Override
                public int read() {
                    return stream.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
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
