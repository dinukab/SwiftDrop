package com.swiftdrop.merchant.web;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.swiftdrop.merchant.infrastructure.webhook.WebhookStore;

@WebMvcTest(controllers = WebhookController.class, properties = "swiftdrop.webhooks.enabled=true")
@Import(WebhookAuthorizationTest.Security.class)
class WebhookAuthorizationTest {
    @Autowired MockMvc mvc;
    @MockitoBean WebhookStore store;
    @MockitoBean JwtDecoder decoder;
    final UUID user = UUID.randomUUID();

    @Test void anonymousIsUnauthorizedAndOtherRolesCannotManageWebhooks() throws Exception {
        mvc.perform(get("/api/v1/webhook-deliveries")).andExpect(status().isUnauthorized());
        for (String role : List.of("CUSTOMER", "COURIER")) {
            mvc.perform(get("/api/v1/webhook-deliveries").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/merchants/me/webhook-endpoints")
                    .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role)))
                    .contentType("application/json").content("{\"url\":\"https://example.com\"}"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(store);
    }

    @Test void merchantScopeComesFromPrincipalAndSecretIsShownOnlyOnCreation() throws Exception {
        UUID id = UUID.randomUUID();
        when(store.create(eq(user), anyString())).thenReturn(new WebhookStore.Created(id, "https://example.com", "one-time-test-secret"));
        var principal = jwt().jwt(j -> j.subject(user.toString())).authorities(new SimpleGrantedAuthority("ROLE_MERCHANT"));
        mvc.perform(post("/api/v1/merchants/me/webhook-endpoints").with(principal)
                .contentType("application/json").content("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.secret").value("one-time-test-secret"));
        when(store.endpoints(user)).thenReturn(List.of(new WebhookStore.Endpoint(id, "https://example.com", true)));
        mvc.perform(get("/api/v1/merchants/me/webhook-endpoints").with(principal))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].secret").doesNotExist());
        mvc.perform(delete("/api/v1/webhook-endpoints/" + id).with(principal)).andExpect(status().isNoContent());
        verify(store).disable(user, id, false);
    }

    @Test void adminCanViewAndDisableButCannotCreateMerchantEndpoint() throws Exception {
        UUID id = UUID.randomUUID();
        var principal = jwt().jwt(j -> j.subject(user.toString())).authorities(new SimpleGrantedAuthority("ROLE_DISPATCHER_ADMIN"));
        mvc.perform(get("/api/v1/webhook-deliveries").with(principal)).andExpect(status().isOk());
        verify(store).deliveries(user, true, 0);
        mvc.perform(delete("/api/v1/webhook-endpoints/" + id).with(principal)).andExpect(status().isNoContent());
        verify(store).disable(user, id, true);
        mvc.perform(post("/api/v1/merchants/me/webhook-endpoints").with(principal)
                .contentType("application/json").content("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isForbidden());
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class Security {
        @Bean SecurityFilterChain filter(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(r -> r.anyRequest().authenticated()).csrf(c -> c.disable())
                    .oauth2ResourceServer(r -> r.jwt(j -> {})).build();
        }
    }
}
