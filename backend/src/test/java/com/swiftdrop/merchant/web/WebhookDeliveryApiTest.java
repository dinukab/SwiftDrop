package com.swiftdrop.merchant.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantApiKeyRepository;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.merchant.domain.WebhookDelivery;
import com.swiftdrop.merchant.domain.WebhookDeliveryRepository;
import com.swiftdrop.merchant.domain.WebhookEndpoint;
import com.swiftdrop.merchant.domain.WebhookEndpointRepository;
import com.swiftdrop.test.security.TestPrincipal;

/** A merchant's webhook history and manual redelivery (Phase 4 item 6). */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id="})
@AutoConfigureMockMvc
class WebhookDeliveryApiTest {

    private static final String BASE = "/api/v1/merchants/me/webhook-deliveries";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private com.swiftdrop.dispatch.domain.CourierProfileRepository courierProfiles;
    @MockitoBean
    private MerchantRepository merchants;
    @MockitoBean
    private WebhookDeliveryRepository deliveries;
    @MockitoBean
    private WebhookEndpointRepository endpoints;
    @MockitoBean
    private MerchantApiKeyRepository apiKeys;
    @MockitoBean
    private com.swiftdrop.delivery.api.ParcelQueries parcelQueries;
    @MockitoBean
    private com.swiftdrop.identity.domain.UserRepository users;
    @MockitoBean
    private com.swiftdrop.identity.domain.ExternalIdentityRepository identities;
    @MockitoBean
    private com.swiftdrop.identity.domain.GoogleLoginCodeRepository googleCodes;
    @MockitoBean
    private com.swiftdrop.delivery.domain.ParcelRepository parcels;
    @MockitoBean
    private com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository history;
    @MockitoBean
    private com.swiftdrop.delivery.domain.DeliveryProofRepository proofs;
    @MockitoBean
    private com.swiftdrop.delivery.domain.OutboxEventRepository outbox;
    @MockitoBean
    private com.swiftdrop.pricing.domain.ZoneRepository zones;
    @MockitoBean
    private com.swiftdrop.pricing.domain.PricingRuleRepository pricingRules;

    private UUID userId;
    private UUID merchantId;
    private WebhookEndpoint endpoint;

    @org.junit.jupiter.api.BeforeEach
    void approvedMerchantWithAnEndpoint() {
        userId = UUID.randomUUID();
        merchantId = UUID.randomUUID();
        Merchant merchant = new Merchant(userId, "Shop", "REG-1", "s@s.com", "123");
        ReflectionTestUtils.setField(merchant, "id", merchantId);
        merchant.approve(UUID.randomUUID());
        when(merchants.findByUserId(userId)).thenReturn(Optional.of(merchant));

        endpoint = new WebhookEndpoint(merchantId, "https://api.acme.com/hooks", "encrypted");
        ReflectionTestUtils.setField(endpoint, "id", UUID.randomUUID());
        when(endpoints.findById(endpoint.getId())).thenReturn(Optional.of(endpoint));
    }

    @Test
    void theHistoryListsTheMerchantsDeliveriesWithoutTheEventBody() throws Exception {
        WebhookDelivery failed = new WebhookDelivery(endpoint.getId(), merchantId, UUID.randomUUID(),
                UUID.randomUUID(), "{\"secret-looking-body\":true}");
        failed.recordFailure(404);
        when(deliveries.findByMerchantIdOrderByCreatedAtDesc(any(), any()))
                .thenReturn(new PageImpl<>(List.of(failed), PageRequest.of(0, 20), 1));

        mvc.perform(get(BASE).with(TestPrincipal.merchant(userId).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("FAILED"))
                .andExpect(jsonPath("$.items[0].lastHttpStatus").value(404))
                .andExpect(jsonPath("$.items[0].manualRedeliveries").value(0))
                .andExpect(jsonPath("$.items[0].payload").doesNotExist())
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void aFailedDeliveryIsSentAgain() throws Exception {
        WebhookDelivery failed = failedDelivery();
        when(deliveries.findByIdAndMerchantId(failed.getId(), merchantId)).thenReturn(Optional.of(failed));

        mvc.perform(post(BASE + "/" + failed.getId() + "/redeliveries")
                        .with(TestPrincipal.merchant(userId).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.manualRedeliveries").value(1));
    }

    @Test
    void aDeliveryThatDidNotFailIsAConflict() throws Exception {
        WebhookDelivery sent = new WebhookDelivery(endpoint.getId(), merchantId, UUID.randomUUID(),
                UUID.randomUUID(), "{}");
        sent.recordSuccess(200);
        when(deliveries.findByIdAndMerchantId(sent.getId(), merchantId)).thenReturn(Optional.of(sent));

        mvc.perform(post(BASE + "/" + sent.getId() + "/redeliveries")
                        .with(TestPrincipal.merchant(userId).postProcessor()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Only a failed delivery can be sent again"));
    }

    @Test
    void anotherMerchantsDeliveryIsNotFound() throws Exception {
        UUID delivery = UUID.randomUUID();
        when(deliveries.findByIdAndMerchantId(delivery, merchantId)).thenReturn(Optional.empty());

        mvc.perform(post(BASE + "/" + delivery + "/redeliveries")
                        .with(TestPrincipal.merchant(userId).postProcessor()))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyAnApprovedMerchantUsesTheHistory() throws Exception {
        mvc.perform(get(BASE).with(TestPrincipal.customer().postProcessor())).andExpect(status().isForbidden());
        mvc.perform(get(BASE).with(TestPrincipal.admin().postProcessor())).andExpect(status().isForbidden());
    }

    private WebhookDelivery failedDelivery() {
        WebhookDelivery delivery = new WebhookDelivery(endpoint.getId(), merchantId, UUID.randomUUID(),
                UUID.randomUUID(), "{}");
        delivery.recordFailure(404);
        return delivery;
    }
}
