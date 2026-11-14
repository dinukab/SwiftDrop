package com.swiftdrop.merchant.web;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import com.swiftdrop.merchant.infrastructure.webhook.WebhookStore;

@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(name = "swiftdrop.webhooks.enabled", havingValue = "true")
class WebhookController {
    private final WebhookStore store;
    WebhookController(WebhookStore store) { this.store = store; }

    @PostMapping("/merchants/me/webhook-endpoints")
    @PreAuthorize("hasRole('MERCHANT')")
    ResponseEntity<WebhookStore.Created> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody EndpointRequest request) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .body(store.create(UUID.fromString(jwt.getSubject()), request.url()));
    }

    @GetMapping("/merchants/me/webhook-endpoints")
    @PreAuthorize("hasRole('MERCHANT')")
    List<WebhookStore.Endpoint> endpoints(@AuthenticationPrincipal Jwt jwt) {
        return store.endpoints(UUID.fromString(jwt.getSubject()));
    }

    @DeleteMapping("/webhook-endpoints/{id}")
    @PreAuthorize("hasAnyRole('MERCHANT','DISPATCHER_ADMIN')")
    ResponseEntity<Void> disable(@AuthenticationPrincipal Jwt jwt, Authentication authentication, @PathVariable UUID id) {
        store.disable(UUID.fromString(jwt.getSubject()), id, admin(authentication));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/webhook-deliveries")
    @PreAuthorize("hasAnyRole('MERCHANT','DISPATCHER_ADMIN')")
    List<WebhookStore.Delivery> deliveries(@AuthenticationPrincipal Jwt jwt, Authentication authentication,
            @RequestParam(defaultValue = "0") int page) {
        return store.deliveries(UUID.fromString(jwt.getSubject()), admin(authentication), Math.max(0, page));
    }

    @GetMapping("/webhook-deliveries/{id}/attempts")
    @PreAuthorize("hasAnyRole('MERCHANT','DISPATCHER_ADMIN')")
    List<WebhookStore.Attempt> attempts(@AuthenticationPrincipal Jwt jwt, Authentication authentication, @PathVariable UUID id) {
        return store.attempts(UUID.fromString(jwt.getSubject()), admin(authentication), id);
    }

    private static boolean admin(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_DISPATCHER_ADMIN"));
    }

    record EndpointRequest(@NotBlank @Size(max = 2048) String url) {
        @Override public String toString() { return "EndpointRequest[redacted]"; }
    }
}
