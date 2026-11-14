package com.swiftdrop.merchant.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.security.crypto.password.PasswordEncoder;
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
import com.swiftdrop.merchant.domain.MerchantApiKey;
import com.swiftdrop.merchant.domain.MerchantApiKeyRepository;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.merchant.domain.WebhookDeliveryRepository;
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
class MerchantApiKeyApiTest {
    @MockitoBean
    private com.swiftdrop.dispatch.domain.CourierProfileRepository courierProfiles;


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
    void listKeysReturns200ForMerchant() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = new Merchant(userId, "Acme", "REG-1", "a@a.com", "123");
        ReflectionTestUtils.setField(merchant, "id", merchantId);

        MerchantApiKey key = new MerchantApiKey(merchantId, "12345678", "hash", "Shopify");

        when(merchants.findByUserId(userId)).thenReturn(Optional.of(merchant));
        when(apiKeys.findByMerchantIdAndActiveTrue(merchantId)).thenReturn(List.of(key));

        mvc.perform(get("/api/v1/merchants/me/api-keys")
                        .with(TestPrincipal.merchant(userId).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label").value("Shopify"))
                .andExpect(jsonPath("$[0].keyPrefix").value("12345678"));
    }

    @Test
    void createKeyReturns201WithRawKeyForApprovedMerchant() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = new Merchant(userId, "Acme", "REG-1", "a@a.com", "123");
        ReflectionTestUtils.setField(merchant, "id", merchantId);
        merchant.approve(UUID.randomUUID());

        when(merchants.findByUserId(userId)).thenReturn(Optional.of(merchant));
        when(merchants.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(apiKeys.countByMerchantIdAndActiveTrue(merchantId)).thenReturn(0);
        when(apiKeys.save(any(MerchantApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(post("/api/v1/merchants/me/api-keys")
                        .with(TestPrincipal.merchant(userId).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "label": "Production Key"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value("Production Key"))
                .andExpect(jsonPath("$.rawKey").value(org.hamcrest.Matchers.startsWith("sd_live_")));
    }

    @Test
    void createKeyReturns403WhenMerchantNotApproved() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = new Merchant(userId, "Acme", "REG-1", "a@a.com", "123");
        ReflectionTestUtils.setField(merchant, "id", merchantId);

        when(merchants.findByUserId(userId)).thenReturn(Optional.of(merchant));
        when(merchants.findById(merchantId)).thenReturn(Optional.of(merchant));

        mvc.perform(post("/api/v1/merchants/me/api-keys")
                        .with(TestPrincipal.merchant(userId).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "label": "Production Key"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void revokeKeyReturns204NoContent() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();
        Merchant merchant = new Merchant(userId, "Acme", "REG-1", "a@a.com", "123");
        ReflectionTestUtils.setField(merchant, "id", merchantId);
        MerchantApiKey key = new MerchantApiKey(merchantId, "12345678", "hash", "Old Key");

        when(merchants.findByUserId(userId)).thenReturn(Optional.of(merchant));
        when(apiKeys.findByIdAndMerchantId(keyId, merchantId)).thenReturn(Optional.of(key));

        mvc.perform(delete("/api/v1/merchants/me/api-keys/" + keyId)
                        .with(TestPrincipal.merchant(userId).postProcessor()))
                .andExpect(status().isNoContent());

        verify(apiKeys).save(key);
    }
}

