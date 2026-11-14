package com.swiftdrop.config;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fixed-window per-IP rate limiter for credential and registration endpoints. Only the
 * sensitive POST routes are limited; all other traffic is untouched. In-memory state is
 * per application instance, which is sufficient for the single-instance modular monolith;
 * a shared store would be needed for multi-instance deployments.
 */
@Component
class RateLimitingFilter extends OncePerRequestFilter {

    private static final int WINDOW_MILLIS = 60_000;
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final int authLimit;
    private final int registrationLimit;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    RateLimitingFilter(
            @Value("${swiftdrop.rate-limit.auth-requests-per-minute:10}") int authLimit,
            @Value("${swiftdrop.rate-limit.registration-requests-per-minute:5}") int registrationLimit) {
        this.authLimit = authLimit;
        this.registrationLimit = registrationLimit;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        Integer limit = limitFor(request);
        if (limit == null) {
            chain.doFilter(request, response);
            return;
        }
        String key = limitGroup(request) + "|" + clientIp(request);
        if (!allow(key, limit)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("""
                    {"type":"about:blank","title":"Too Many Requests","status":429,
                     "detail":"Too many requests. Please wait before trying again."}
                    """);
            return;
        }
        chain.doFilter(request, response);
    }

    private Integer limitFor(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) {
            return null;
        }
        String path = request.getRequestURI();
        if (path.equals("/api/v1/auth/tokens") || path.equals("/api/v1/auth/google/exchanges")) {
            return authLimit;
        }
        if (path.equals("/api/v1/users") || path.equals("/api/v1/merchants")
                || path.equals("/api/v1/merchants/register")) {
            return registrationLimit;
        }
        return null;
    }

    private String limitGroup(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/v1/auth") ? "auth" : "registration";
    }

    private boolean allow(String key, int limit) {
        long now = System.currentTimeMillis();
        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.startedAt >= WINDOW_MILLIS) {
                return new Window(now);
            }
            existing.count.incrementAndGet();
            return existing;
        });
        if (windows.size() > MAX_TRACKED_KEYS) {
            windows.entrySet().removeIf(e -> now - e.getValue().startedAt >= WINDOW_MILLIS);
        }
        return window.count.get() <= limit;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static final class Window {
        private final long startedAt;
        private final AtomicInteger count = new AtomicInteger();

        private Window(long startedAt) {
            this.startedAt = startedAt;
            this.count.set(1);
        }
    }
}
