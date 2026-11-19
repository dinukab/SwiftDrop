package com.swiftdrop.pricing.web;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.OutboxEventRepository;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository;
import com.swiftdrop.dispatch.domain.CourierProfileRepository;
import com.swiftdrop.identity.domain.ExternalIdentityRepository;
import com.swiftdrop.identity.domain.GoogleLoginCodeRepository;
import com.swiftdrop.identity.domain.Role;
import com.swiftdrop.identity.domain.User;
import com.swiftdrop.identity.domain.UserRepository;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.pricing.domain.PricingRule;
import com.swiftdrop.pricing.domain.PricingRuleRepository;
import com.swiftdrop.pricing.domain.ZoneRepository;
import com.swiftdrop.test.security.TestPrincipal;

/** The admin pricing-rule endpoints through HTTP; the repositories are mocked. */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id="})
@AutoConfigureMockMvc
class PricingRuleAdminApiTest {

    private static final String RULES = "/api/v1/admin/pricing-rules";
    private static final String ORIGIN = "11111111-1111-1111-1111-111111111111";
    private static final String DESTINATION = "22222222-2222-2222-2222-222222222222";
    private static final String BODY = """
            {"originZoneId":"%s","destinationZoneId":"%s",
             "baseFee":"300.00","perKgFee":"100.00",
             "smallSizeSurcharge":"0.00","mediumSizeSurcharge":"150.00","largeSizeSurcharge":"350.00",
             "currency":"LKR","active":true}
            """.formatted(ORIGIN, DESTINATION);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UserRepository users;
    @MockitoBean
    private ExternalIdentityRepository identities;
    @MockitoBean
    private GoogleLoginCodeRepository googleCodes;
    @MockitoBean
    private ZoneRepository zones;
    @MockitoBean
    private PricingRuleRepository pricingRules;
    @MockitoBean
    private MerchantRepository merchants;
    @MockitoBean
    private ParcelRepository parcels;
    @MockitoBean
    private ParcelStatusHistoryRepository parcelHistory;
    @MockitoBean
    private DeliveryProofRepository deliveryProofs;
    @MockitoBean
    private OutboxEventRepository outboxEvents;
    @MockitoBean
    private CourierProfileRepository courierProfiles;

    @BeforeEach
    void stubAuthenticatedUser() {
        when(users.findById(any())).thenAnswer(inv -> {
            User stub = new User("stub@example.com", "hash", "Stub User", Set.of(Role.ADMIN));
            ReflectionTestUtils.setField(stub, "id", inv.getArgument(0));
            return Optional.of(stub);
        });
    }

    @Test
    void adminListsRules() throws Exception {
        when(pricingRules.findAll(any(Sort.class))).thenReturn(List.of(rule()));

        mvc.perform(get(RULES).with(TestPrincipal.admin().postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].baseFee").value("300.00"))
                .andExpect(jsonPath("$[0].currency").value("LKR"));
    }

    @Test
    void adminCreatesRuleWith201AndLocation() throws Exception {
        when(zones.existsById(any())).thenReturn(true);
        when(pricingRules.existsByOriginZoneIdAndDestinationZoneId(any(), any())).thenReturn(false);
        when(pricingRules.saveAndFlush(any(PricingRule.class))).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(post(RULES).with(TestPrincipal.admin().postProcessor())
                        .contentType("application/json").content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith(RULES + "/")))
                .andExpect(jsonPath("$.mediumSizeSurcharge").value("150.00"));
    }

    @Test
    void unknownZoneIsRejectedWith400() throws Exception {
        when(zones.existsById(any())).thenReturn(false);

        mvc.perform(post(RULES).with(TestPrincipal.admin().postProcessor())
                        .contentType("application/json").content(BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicatePairIsAConflict() throws Exception {
        when(zones.existsById(any())).thenReturn(true);
        when(pricingRules.existsByOriginZoneIdAndDestinationZoneId(any(), any())).thenReturn(true);

        mvc.perform(post(RULES).with(TestPrincipal.admin().postProcessor())
                        .contentType("application/json").content(BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void negativeFeeIsRejectedWith400() throws Exception {
        String negative = BODY.replace("\"baseFee\":\"300.00\"", "\"baseFee\":\"-1.00\"");

        mvc.perform(post(RULES).with(TestPrincipal.admin().postProcessor())
                        .contentType("application/json").content(negative))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tooPreciseFeeIsRejectedWith400() throws Exception {
        String precise = BODY.replace("\"perKgFee\":\"100.00\"", "\"perKgFee\":\"100.005\"");

        mvc.perform(post(RULES).with(TestPrincipal.admin().postProcessor())
                        .contentType("application/json").content(precise))
                .andExpect(status().isBadRequest());
    }

    @Test
    void badCurrencyIsRejectedWith400() throws Exception {
        String badCurrency = BODY.replace("\"LKR\"", "\"RUPEES\"");

        mvc.perform(post(RULES).with(TestPrincipal.admin().postProcessor())
                        .contentType("application/json").content(badCurrency))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatingAnUnknownRuleIsNotFound() throws Exception {
        when(pricingRules.findById(any())).thenReturn(Optional.empty());

        mvc.perform(put(RULES + "/" + UUID.randomUUID()).with(TestPrincipal.admin().postProcessor())
                        .contentType("application/json").content(BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void customersAndDispatchersCannotManageRules() throws Exception {
        mvc.perform(post(RULES).with(TestPrincipal.customer().postProcessor())
                        .contentType("application/json").content(BODY))
                .andExpect(status().isForbidden());
        mvc.perform(get(RULES).with(TestPrincipal.dispatcher().postProcessor()))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCallersAreUnauthorized() throws Exception {
        mvc.perform(get(RULES)).andExpect(status().isUnauthorized());
    }

    private static PricingRule rule() {
        PricingRule rule = new PricingRule(UUID.fromString(ORIGIN), UUID.fromString(DESTINATION),
                new BigDecimal("300.00"), new BigDecimal("100.00"),
                BigDecimal.ZERO, new BigDecimal("150.00"), new BigDecimal("350.00"), "LKR", true);
        ReflectionTestUtils.setField(rule, "id", UUID.randomUUID());
        return rule;
    }
}
