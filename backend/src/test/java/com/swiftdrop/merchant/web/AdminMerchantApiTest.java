package com.swiftdrop.merchant.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
import com.swiftdrop.merchant.domain.MerchantStatus;
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
class AdminMerchantApiTest {

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
    void listMerchantsReturns200ForDispatcherAdmin() throws Exception {
        Merchant merchant = new Merchant(UUID.randomUUID(), "Alpha Store", "REG-A1", "alpha@store.com", "077123");
        ReflectionTestUtils.setField(merchant, "id", UUID.randomUUID());
        Page<Merchant> page = new PageImpl<>(List.of(merchant));
        when(merchants.findAll(any(Pageable.class))).thenReturn(page);

        mvc.perform(get("/api/v1/admin/merchants")
                        .with(TestPrincipal.dispatcherAdmin().postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].businessName").value("Alpha Store"))
                .andExpect(jsonPath("$.items[0].status").value("PENDING_APPROVAL"));
    }

    @Test
    void listMerchantsForbiddenForMerchantRole() throws Exception {
        mvc.perform(get("/api/v1/admin/merchants")
                        .with(TestPrincipal.merchant().postProcessor()))
                .andExpect(status().isForbidden());
    }

    @Test
    void listMerchantsUnauthorizedForAnonymous() throws Exception {
        mvc.perform(get("/api/v1/admin/merchants"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMerchantDetailReturns200ForDispatcherAdmin() throws Exception {
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = new Merchant(UUID.randomUUID(), "Beta Store", "REG-B2", "beta@store.com", "077456");
        ReflectionTestUtils.setField(merchant, "id", merchantId);
        when(merchants.findById(merchantId)).thenReturn(Optional.of(merchant));

        mvc.perform(get("/api/v1/admin/merchants/" + merchantId)
                        .with(TestPrincipal.dispatcherAdmin().postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Beta Store"));
    }

    @Test
    void reviewMerchantApproveReturns200WithApprovedStatus() throws Exception {
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = new Merchant(UUID.randomUUID(), "Beta Store", "REG-B2", "beta@store.com", "077456");
        ReflectionTestUtils.setField(merchant, "id", merchantId);
        when(merchants.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(merchants.save(any(Merchant.class))).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(patch("/api/v1/admin/merchants/" + merchantId + "/status")
                        .with(TestPrincipal.dispatcherAdmin().postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "action": "APPROVE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void reviewMerchantRejectReturns200WithRejectionReason() throws Exception {
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = new Merchant(UUID.randomUUID(), "Gamma Store", "REG-G3", "gamma@store.com", "077789");
        ReflectionTestUtils.setField(merchant, "id", merchantId);
        when(merchants.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(merchants.save(any(Merchant.class))).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(patch("/api/v1/admin/merchants/" + merchantId + "/status")
                        .with(TestPrincipal.dispatcherAdmin().postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "action": "REJECT",
                                    "reason": "Business registration invalid"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Business registration invalid"));
    }
}

