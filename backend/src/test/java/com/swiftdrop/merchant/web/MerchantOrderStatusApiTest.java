package com.swiftdrop.merchant.web;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.swiftdrop.delivery.api.FailureReason;
import com.swiftdrop.delivery.api.ParcelEvents;
import com.swiftdrop.delivery.api.ParcelQueries;
import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.api.ParcelStatusChangedData;
import com.swiftdrop.delivery.api.ParcelSummary;
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
import com.swiftdrop.pricing.api.ParcelSize;
import com.swiftdrop.pricing.domain.PricingRuleRepository;
import com.swiftdrop.pricing.domain.ZoneRepository;
import com.swiftdrop.test.security.TestPrincipal;

/**
 * The store-facing status API: the merchant's e-commerce site reads the status and sequenced history
 * of its own orders with its API key (MER-2). Runs the real API-key filter and security rules.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id="})
@AutoConfigureMockMvc
class MerchantOrderStatusApiTest {

    private static final String VALID_KEY = "sd_live_abcdef1234567890abcdef1234567890abcdef1234567890";
    private static final String PREFIX = "abcdef12";
    private static final String URL = "/api/v1/merchant-api/orders/REF-1";

    @MockitoBean
    private com.swiftdrop.dispatch.domain.CourierProfileRepository courierProfiles;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private ParcelQueries parcelQueries;
    @MockitoBean
    private ParcelEvents parcelEvents;

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

    private UUID merchantId;
    private UUID parcelId;

    @BeforeEach
    void aMerchantWithAnActiveKey() {
        merchantId = UUID.randomUUID();
        parcelId = UUID.randomUUID();
        Merchant merchant = new Merchant(UUID.randomUUID(), "Shop", "REG-1", "s@s.com", "123");
        ReflectionTestUtils.setField(merchant, "id", merchantId);
        merchant.approve(UUID.randomUUID());
        MerchantApiKey key = new MerchantApiKey(merchantId, PREFIX, passwordEncoder.encode(VALID_KEY), "Live Key");
        when(apiKeys.findByKeyPrefixAndActiveTrue(PREFIX)).thenReturn(Optional.of(key));
        when(merchants.findById(merchantId)).thenReturn(Optional.of(merchant));
    }

    @Test
    void theStoreReadsTheStatusAndTheSequencedHistoryOfItsOwnOrder() throws Exception {
        when(parcelQueries.findByMerchantReference(merchantId, "REF-1")).thenReturn(Optional.of(summary(ParcelStatus.PAUSED)));
        when(parcelEvents.eventsBetween(eq(parcelId), eq(1), anyInt())).thenReturn(List.of(
                event(1, null, ParcelStatus.CREATED, null),
                event(2, ParcelStatus.CREATED, ParcelStatus.PAUSED, null)));

        mvc.perform(get(URL).header("X-API-Key", VALID_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parcelId").value(parcelId.toString()))
                .andExpect(jsonPath("$.trackingNumber").value("SD1234567890"))
                .andExpect(jsonPath("$.merchantOrderReference").value("REF-1"))
                .andExpect(jsonPath("$.status").value("PAUSED"))
                .andExpect(jsonPath("$.lastSequence").value(2))
                .andExpect(jsonPath("$.events.length()").value(2))
                .andExpect(jsonPath("$.events[0].sequence").value(1))
                .andExpect(jsonPath("$.events[0].status").value("CREATED"))
                .andExpect(jsonPath("$.events[0].previousStatus").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.events[1].sequence").value(2))
                .andExpect(jsonPath("$.events[1].status").value("PAUSED"))
                .andExpect(jsonPath("$.events[1].previousStatus").value("CREATED"))
                .andExpect(jsonPath("$.events[1].eventId").exists());
        // Scoped by the merchant the API key belongs to
        verify(parcelQueries).findByMerchantReference(merchantId, "REF-1");
    }

    @Test
    void aFailedDeliveryCarriesItsReasonCode() throws Exception {
        when(parcelQueries.findByMerchantReference(merchantId, "REF-1")).thenReturn(Optional.of(summary(ParcelStatus.FAILED)));
        when(parcelEvents.eventsBetween(eq(parcelId), eq(1), anyInt())).thenReturn(List.of(
                event(1, null, ParcelStatus.CREATED, null),
                event(2, ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.FAILED, FailureReason.ADDRESS_NOT_FOUND)));

        mvc.perform(get(URL).header("X-API-Key", VALID_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[1].failureReasonCode").value("ADDRESS_NOT_FOUND"));
    }

    @Test
    void theResponseNeverContainsRecipientDetails() throws Exception {
        when(parcelQueries.findByMerchantReference(merchantId, "REF-1")).thenReturn(Optional.of(summary(ParcelStatus.CREATED)));
        when(parcelEvents.eventsBetween(eq(parcelId), eq(1), anyInt()))
                .thenReturn(List.of(event(1, null, ParcelStatus.CREATED, null)));

        mvc.perform(get(URL).header("X-API-Key", VALID_KEY))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.not(Matchers.containsStringIgnoringCase("phone"))))
                .andExpect(content().string(Matchers.not(Matchers.containsStringIgnoringCase("contactName"))))
                .andExpect(content().string(Matchers.not(Matchers.containsStringIgnoringCase("line1"))));
    }

    @Test
    void anOrderThatIsNotThisMerchantsIsNotFound() throws Exception {
        when(parcelQueries.findByMerchantReference(merchantId, "REF-1")).thenReturn(Optional.empty());

        mvc.perform(get(URL).header("X-API-Key", VALID_KEY))
                .andExpect(status().isNotFound());
        verifyNoInteractions(parcelEvents);
    }

    @Test
    void withoutAValidKeyTheStatusIsNotAvailable() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mvc.perform(get(URL).header("X-API-Key", "sd_live_invalidkey1234567890abcdef1234567890"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(parcelQueries, parcelEvents);
    }

    @Test
    void aLoggedInUserCannotUseTheStoreApi() throws Exception {
        // The store API is for API keys only: the key filter refuses a request without one before
        // any login token is considered, so even a merchant or an admin is turned away.
        mvc.perform(get(URL).with(TestPrincipal.merchant(UUID.randomUUID()).postProcessor()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(URL).with(TestPrincipal.admin().postProcessor()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(parcelQueries, parcelEvents);
    }

    private ParcelSummary summary(ParcelStatus status) {
        return new ParcelSummary(parcelId, "SD1234567890", status, ParcelSize.SMALL, 500, ParcelSource.MERCHANT,
                null, merchantId, "REF-1", null, Instant.parse("2026-11-15T10:00:00Z"));
    }

    private ParcelStatusChanged event(int sequence, ParcelStatus previous, ParcelStatus status, FailureReason reason) {
        return ParcelStatusChanged.of(UUID.randomUUID(), Instant.parse("2026-11-15T10:00:00Z").plusSeconds(sequence),
                new ParcelStatusChangedData(parcelId, "SD1234567890", previous, status, sequence,
                        ParcelSource.MERCHANT, null, merchantId, "REF-1", reason));
    }
}
