package com.swiftdrop.pricing.web;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.swiftdrop.pricing.domain.PricingRuleRepository;
import com.swiftdrop.pricing.domain.Zone;
import com.swiftdrop.pricing.domain.ZoneRepository;
import com.swiftdrop.test.security.TestPrincipal;

/** The admin zone endpoints through HTTP; the repositories are mocked. */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id="})
@AutoConfigureMockMvc
class ZoneAdminApiTest {

    private static final String ZONES = "/api/v1/admin/zones";
    private static final String BODY = "{\"code\":\"LK-NP\",\"name\":\"Northern Province\",\"active\":true}";

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
    void adminListsAllZonesIncludingInactive() throws Exception {
        when(zones.findAll(any(Sort.class))).thenReturn(List.of(zone("LK-NP", "Northern Province", false)));

        mvc.perform(get(ZONES).with(TestPrincipal.admin().postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("LK-NP"))
                .andExpect(jsonPath("$[0].active").value(false));
    }

    @Test
    void adminCreatesZoneWith201AndLocation() throws Exception {
        when(zones.existsByCode(anyString())).thenReturn(false);
        when(zones.existsByName(anyString())).thenReturn(false);
        when(zones.saveAndFlush(any(Zone.class))).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(post(ZONES).with(TestPrincipal.admin().postProcessor())
                        .contentType("application/json").content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith(ZONES + "/")))
                .andExpect(jsonPath("$.code").value("LK-NP"));
    }

    @Test
    void duplicateZoneCodeIsAConflict() throws Exception {
        when(zones.existsByCode(anyString())).thenReturn(true);

        mvc.perform(post(ZONES).with(TestPrincipal.admin().postProcessor())
                        .contentType("application/json").content(BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void updatingAnUnknownZoneIsNotFound() throws Exception {
        when(zones.findById(any())).thenReturn(Optional.empty());

        mvc.perform(put(ZONES + "/" + UUID.randomUUID()).with(TestPrincipal.admin().postProcessor())
                        .contentType("application/json").content(BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void customersAndDispatchersCannotManageZones() throws Exception {
        mvc.perform(post(ZONES).with(TestPrincipal.customer().postProcessor())
                        .contentType("application/json").content(BODY))
                .andExpect(status().isForbidden());
        mvc.perform(get(ZONES).with(TestPrincipal.dispatcher().postProcessor()))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCallersAreUnauthorized() throws Exception {
        mvc.perform(get(ZONES)).andExpect(status().isUnauthorized());
    }

    @Test
    void missingFieldsAreRejectedWith400() throws Exception {
        mvc.perform(post(ZONES).with(TestPrincipal.admin().postProcessor())
                        .contentType("application/json").content("{\"code\":\"\",\"name\":\"X\"}"))
                .andExpect(status().isBadRequest());
    }

    private static Zone zone(String code, String name, boolean active) {
        Zone zone = new Zone(code, name, active);
        ReflectionTestUtils.setField(zone, "id", UUID.randomUUID());
        return zone;
    }
}
