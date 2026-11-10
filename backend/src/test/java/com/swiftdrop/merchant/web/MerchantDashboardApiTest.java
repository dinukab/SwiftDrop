package com.swiftdrop.merchant.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.swiftdrop.delivery.api.PageRequest;
import com.swiftdrop.delivery.api.PageResult;
import com.swiftdrop.delivery.api.ParcelQueries;
import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelSummary;
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
import com.swiftdrop.merchant.domain.WebhookEndpointRepository;
import com.swiftdrop.pricing.api.ParcelSize;
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
class MerchantDashboardApiTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ParcelQueries parcelQueries;

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
    void listOrdersReturns200ForMerchant() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = new Merchant(userId, "Shop", "REG-1", "s@s.com", "123");
        ReflectionTestUtils.setField(merchant, "id", merchantId);

        when(merchants.findByUserId(userId)).thenReturn(Optional.of(merchant));

        ParcelSummary summary = new ParcelSummary(
                UUID.randomUUID(), "SD1234567890", ParcelStatus.IN_TRANSIT,
                ParcelSize.SMALL, 500, ParcelSource.MERCHANT, null, merchantId, "REF-10",
                null, Instant.now()
        );
        PageResult<ParcelSummary> page = new PageResult<>(List.of(summary), 0, 20, 1, 1);

        when(parcelQueries.findParcelsByMerchant(eq(merchantId), any(), any(PageRequest.class)))
                .thenReturn(page);

        mvc.perform(get("/api/v1/merchants/me/orders")
                        .with(TestPrincipal.merchant(userId).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].trackingNumber").value("SD1234567890"))
                .andExpect(jsonPath("$.items[0].merchantOrderReference").value("REF-10"))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void listOrdersForbiddenForCustomer() throws Exception {
        mvc.perform(get("/api/v1/merchants/me/orders")
                        .with(TestPrincipal.customer().postProcessor()))
                .andExpect(status().isForbidden());
    }

    @Test
    void getOrderByReferenceReturns200() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = new Merchant(userId, "Shop", "REG-1", "s@s.com", "123");
        ReflectionTestUtils.setField(merchant, "id", merchantId);

        when(merchants.findByUserId(userId)).thenReturn(Optional.of(merchant));

        ParcelSummary summary = new ParcelSummary(
                UUID.randomUUID(), "SD9988776655", ParcelStatus.DELIVERED,
                ParcelSize.MEDIUM, 1000, ParcelSource.MERCHANT, null, merchantId, "REF-TARGET",
                null, Instant.now()
        );

        when(parcelQueries.findByMerchantReference(merchantId, "REF-TARGET"))
                .thenReturn(Optional.of(summary));

        mvc.perform(get("/api/v1/merchants/me/orders/REF-TARGET")
                        .with(TestPrincipal.merchant(userId).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value("SD9988776655"))
                .andExpect(jsonPath("$.status").value("DELIVERED"));
    }
}

