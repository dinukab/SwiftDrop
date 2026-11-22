package com.swiftdrop.merchant.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.swiftdrop.delivery.api.MerchantParcelControl;
import com.swiftdrop.delivery.api.PageRequest;
import com.swiftdrop.delivery.api.PageResult;
import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelNotFoundException;
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

/** Hold, release and cancel an order from the merchant portal (MER-4). */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id="})
@AutoConfigureMockMvc
class MerchantOrderActionsApiTest {

    private static final String BASE = "/api/v1/merchants/me/orders/REF-1";

    @MockitoBean
    private com.swiftdrop.dispatch.domain.CourierProfileRepository courierProfiles;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ParcelQueries parcelQueries;
    @MockitoBean
    private MerchantParcelControl parcelControl;

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

    private UUID userId;
    private UUID merchantId;

    @BeforeEach
    void approvedMerchant() {
        userId = UUID.randomUUID();
        merchantId = UUID.randomUUID();
        Merchant merchant = new Merchant(userId, "Shop", "REG-1", "s@s.com", "123");
        ReflectionTestUtils.setField(merchant, "id", merchantId);
        merchant.approve(UUID.randomUUID());
        when(merchants.findByUserId(userId)).thenReturn(Optional.of(merchant));
    }

    @Test
    void pauseHoldsTheOrderAndReturnsItWithTheActionsThatNowApply() throws Exception {
        when(parcelControl.pause(merchantId, "REF-1", userId, "Packing")).thenReturn(ParcelStatus.PAUSED);
        orderIs(ParcelStatus.PAUSED);

        mvc.perform(post(BASE + "/holds")
                        .with(TestPrincipal.merchant(userId).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Packing\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"))
                .andExpect(jsonPath("$.merchantOrderReference").value("REF-1"))
                .andExpect(jsonPath("$.allowedActions[0]").value("RESUME"))
                .andExpect(jsonPath("$.allowedActions[1]").value("CANCEL"));
        verify(parcelControl).pause(merchantId, "REF-1", userId, "Packing");
    }

    @Test
    void resumeReleasesTheHold() throws Exception {
        when(parcelControl.resume(eq(merchantId), eq("REF-1"), eq(userId), isNull())).thenReturn(ParcelStatus.CREATED);
        orderIs(ParcelStatus.CREATED);

        mvc.perform(delete(BASE + "/holds").with(TestPrincipal.merchant(userId).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.allowedActions[0]").value("PAUSE"))
                .andExpect(jsonPath("$.allowedActions[1]").value("CANCEL"));
    }

    @Test
    void cancelWorksWithoutABodyAndLeavesNoFurtherActions() throws Exception {
        when(parcelControl.cancel(eq(merchantId), eq("REF-1"), eq(userId), isNull())).thenReturn(ParcelStatus.CANCELLED);
        orderIs(ParcelStatus.CANCELLED);

        mvc.perform(post(BASE + "/cancellations").with(TestPrincipal.merchant(userId).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.allowedActions").isEmpty());
    }

    @Test
    void anOrderPastPickupIsAConflictWithTheRuleInTheMessage() throws Exception {
        when(parcelControl.pause(eq(merchantId), eq("REF-1"), eq(userId), any()))
                .thenThrow(new ParcelConflictException("A delivery can only be paused before pickup"));

        mvc.perform(post(BASE + "/holds").with(TestPrincipal.merchant(userId).postProcessor()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A delivery can only be paused before pickup"));
    }

    @Test
    void anUnknownOrAnotherMerchantsOrderIsNotFound() throws Exception {
        when(parcelControl.cancel(eq(merchantId), eq("REF-1"), eq(userId), any()))
                .thenThrow(new ParcelNotFoundException());

        mvc.perform(post(BASE + "/cancellations").with(TestPrincipal.merchant(userId).postProcessor()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Order not found"));
    }

    @Test
    void aReasonOver500CharactersIsRejectedBeforeAnythingChanges() throws Exception {
        mvc.perform(post(BASE + "/holds")
                        .with(TestPrincipal.merchant(userId).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"" + "x".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(parcelControl);
    }

    @Test
    void theListShowsWhichActionsApplyToEachOrder() throws Exception {
        ParcelSummary created = summary(ParcelStatus.CREATED, "REF-A");
        ParcelSummary paused = summary(ParcelStatus.PAUSED, "REF-B");
        ParcelSummary inTransit = summary(ParcelStatus.IN_TRANSIT, "REF-C");
        when(parcelQueries.findParcelsByMerchant(eq(merchantId), any(), any(PageRequest.class)))
                .thenReturn(new PageResult<>(List.of(created, paused, inTransit), 0, 20, 3, 1));

        mvc.perform(get("/api/v1/merchants/me/orders").with(TestPrincipal.merchant(userId).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].allowedActions[0]").value("PAUSE"))
                .andExpect(jsonPath("$.items[0].allowedActions[1]").value("CANCEL"))
                .andExpect(jsonPath("$.items[1].allowedActions[0]").value("RESUME"))
                .andExpect(jsonPath("$.items[2].allowedActions").isEmpty());
    }

    @Test
    void onlyAMerchantMayUseTheActions() throws Exception {
        mvc.perform(post(BASE + "/holds").with(TestPrincipal.customer().postProcessor()))
                .andExpect(status().isForbidden());
        mvc.perform(delete(BASE + "/holds").with(TestPrincipal.courier().postProcessor()))
                .andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/cancellations").with(TestPrincipal.dispatcher().postProcessor()))
                .andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/cancellations").with(TestPrincipal.admin().postProcessor()))
                .andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/holds").with(TestPrincipal.anonymous().postProcessor()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(parcelControl);
    }

    @Test
    void aMerchantThatIsNotApprovedCannotUseTheActions() throws Exception {
        UUID pendingUser = UUID.randomUUID();
        Merchant pending = new Merchant(pendingUser, "Shop", "REG-2", "p@s.com", "123");
        ReflectionTestUtils.setField(pending, "id", UUID.randomUUID());
        when(merchants.findByUserId(pendingUser)).thenReturn(Optional.of(pending));

        mvc.perform(post(BASE + "/holds").with(TestPrincipal.merchant(pendingUser).postProcessor()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(parcelControl);
    }

    private void orderIs(ParcelStatus status) {
        when(parcelQueries.findByMerchantReference(merchantId, "REF-1")).thenReturn(Optional.of(summary(status, "REF-1")));
    }

    private ParcelSummary summary(ParcelStatus status, String reference) {
        return new ParcelSummary(UUID.randomUUID(), "SD1234567890", status, ParcelSize.SMALL, 500,
                ParcelSource.MERCHANT, null, merchantId, reference, null, Instant.now());
    }
}
