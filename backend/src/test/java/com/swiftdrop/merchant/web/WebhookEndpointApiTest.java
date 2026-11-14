package com.swiftdrop.merchant.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.OutboxEventRepository;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository;
import com.swiftdrop.identity.domain.ExternalIdentityRepository;
import com.swiftdrop.identity.domain.GoogleLoginCodeRepository;
import com.swiftdrop.identity.domain.UserRepository;
import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantApiKeyRepository;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.merchant.domain.WebhookDeliveryRepository;
import com.swiftdrop.merchant.domain.WebhookEndpoint;
import com.swiftdrop.merchant.domain.WebhookEndpointRepository;
import com.swiftdrop.pricing.domain.PricingRuleRepository;
import com.swiftdrop.pricing.domain.ZoneRepository;
import com.swiftdrop.test.security.TestPrincipal;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id="})
@AutoConfigureMockMvc
class WebhookEndpointApiTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MerchantRepository merchants;
    @MockitoBean
    private MerchantApiKeyRepository apiKeys;
    @MockitoBean
    private WebhookEndpointRepository webhookEndpoints;
    @MockitoBean
    private WebhookDeliveryRepository webhookDeliveries;

    @MockitoBean
    private UserRepository users;
    @MockitoBean
    private ExternalIdentityRepository identities;
    @MockitoBean
    private GoogleLoginCodeRepository googleCodes;
    @MockitoBean
    private ParcelRepository parcels;
    @MockitoBean
    private ParcelStatusHistoryRepository history;
    @MockitoBean
    private DeliveryProofRepository proofs;
    @MockitoBean
    private OutboxEventRepository outbox;
    @MockitoBean
    private ZoneRepository zones;
    @MockitoBean
    private PricingRuleRepository pricingRules;

    @Test
    void listWebhooksReturns200ForMerchant() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = new Merchant(userId, "Store", "REG-1", "s@s.com", "123");
        ReflectionTestUtils.setField(merchant, "id", merchantId);

        WebhookEndpoint ep = new WebhookEndpoint(merchantId, "https://example.com/wh", "enc");

        when(merchants.findByUserId(userId)).thenReturn(Optional.of(merchant));
        when(webhookEndpoints.findByMerchantIdAndActiveTrue(merchantId)).thenReturn(List.of(ep));

        mvc.perform(get("/api/v1/merchants/me/webhooks")
                        .with(TestPrincipal.merchant(userId).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].url").value("https://example.com/wh"))
                .andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    void registerWebhookReturns201ForApprovedMerchant() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = new Merchant(userId, "Store", "REG-1", "s@s.com", "123");
        ReflectionTestUtils.setField(merchant, "id", merchantId);
        merchant.approve(UUID.randomUUID());

        when(merchants.findByUserId(userId)).thenReturn(Optional.of(merchant));
        when(merchants.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(webhookEndpoints.countByMerchantIdAndActiveTrue(merchantId)).thenReturn(0);
        when(webhookEndpoints.save(any(WebhookEndpoint.class))).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(post("/api/v1/merchants/me/webhooks")
                        .with(TestPrincipal.merchant(userId).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "url": "https://callback.store.com/webhooks",
                                    "secret": "super-secret-key-12345"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value("https://callback.store.com/webhooks"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void deleteWebhookReturns204NoContent() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        UUID epId = UUID.randomUUID();
        Merchant merchant = new Merchant(userId, "Store", "REG-1", "s@s.com", "123");
        ReflectionTestUtils.setField(merchant, "id", merchantId);
        WebhookEndpoint ep = new WebhookEndpoint(merchantId, "https://example.com/wh", "enc");

        when(merchants.findByUserId(userId)).thenReturn(Optional.of(merchant));
        when(webhookEndpoints.findByIdAndMerchantId(epId, merchantId)).thenReturn(Optional.of(ep));

        mvc.perform(delete("/api/v1/merchants/me/webhooks/" + epId)
                        .with(TestPrincipal.merchant(userId).postProcessor()))
                .andExpect(status().isNoContent());

        assertThat(ep.isActive()).isFalse();
        verify(webhookEndpoints).save(ep);
    }
}
