package com.swiftdrop.merchant.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
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

import com.swiftdrop.delivery.api.CreatedParcel;
import com.swiftdrop.delivery.api.ParcelCreation;
import com.swiftdrop.delivery.api.ParcelStatus;
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

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id="})
@AutoConfigureMockMvc
class MerchantOrderApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private ParcelCreation parcelCreation;

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

    private static final String VALID_KEY = "sd_live_abcdef1234567890abcdef1234567890abcdef1234567890";
    private static final String PREFIX = "abcdef12";

    @Test
    void placeOrderWithValidApiKeyReturns201Created() throws Exception {
        UUID merchantId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        String hash = passwordEncoder.encode(VALID_KEY);

        Merchant merchant = new Merchant(UUID.randomUUID(), "Shop", "REG-1", "s@s.com", "123");
        ReflectionTestUtils.setField(merchant, "id", merchantId);
        merchant.approve(UUID.randomUUID());

        MerchantApiKey apiKey = new MerchantApiKey(merchantId, PREFIX, hash, "Live Key");

        when(apiKeys.findByKeyPrefixAndActiveTrue(PREFIX)).thenReturn(Optional.of(apiKey));
        when(merchants.findById(merchantId)).thenReturn(Optional.of(merchant));

        CreatedParcel created = new CreatedParcel(
                parcelId, "SD1234567890", ParcelStatus.CREATED, new BigDecimal("450.00"), "LKR", Instant.now(), true
        );
        when(parcelCreation.createMerchantParcel(any())).thenReturn(created);

        mvc.perform(post("/api/v1/merchant-api/orders")
                        .header("X-API-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "merchantOrderReference": "REF-9901",
                                    "pickup": {
                                        "contactName": "Warehouse Hub",
                                        "phone": "0771234567",
                                        "line1": "123 Main St",
                                        "city": "Colombo",
                                        "zoneId": "00000000-0000-0000-0000-000000000001"
                                    },
                                    "dropoff": {
                                        "contactName": "Customer Name",
                                        "phone": "0779876543",
                                        "line1": "45 Park Rd",
                                        "city": "Kandy",
                                        "zoneId": "00000000-0000-0000-0000-000000000002"
                                    },
                                    "size": "SMALL",
                                    "weightGrams": 500,
                                    "description": "Phone case"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/parcels/" + parcelId))
                .andExpect(jsonPath("$.trackingNumber").value("SD1234567890"))
                .andExpect(jsonPath("$.newlyCreated").value(true));
    }

    @Test
    void placeOrderWithoutApiKeyReturns401Unauthorized() throws Exception {
        mvc.perform(post("/api/v1/merchant-api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "merchantOrderReference": "REF-9901"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void placeOrderWithInvalidApiKeyReturns401Unauthorized() throws Exception {
        mvc.perform(post("/api/v1/merchant-api/orders")
                        .header("X-API-Key", "sd_live_invalidkey1234567890abcdef1234567890")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "merchantOrderReference": "REF-9901"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }
}

