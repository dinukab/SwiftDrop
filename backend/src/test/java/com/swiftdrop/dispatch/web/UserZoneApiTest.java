package com.swiftdrop.dispatch.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
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
import com.swiftdrop.dispatch.application.CourierProfileService;
import com.swiftdrop.dispatch.application.HubService;
import com.swiftdrop.dispatch.domain.CourierProfile;
import com.swiftdrop.dispatch.domain.CourierProfileRepository;
import com.swiftdrop.dispatch.domain.Hub;
import com.swiftdrop.identity.api.UserSummary;
import com.swiftdrop.identity.application.AccountService;
import com.swiftdrop.identity.domain.ExternalIdentityRepository;
import com.swiftdrop.identity.domain.GoogleLoginCodeRepository;
import com.swiftdrop.identity.domain.UserRepository;
import com.swiftdrop.merchant.domain.MerchantApiKeyRepository;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.merchant.domain.WebhookDeliveryRepository;
import com.swiftdrop.merchant.domain.WebhookEndpointRepository;
import com.swiftdrop.pricing.domain.PricingRuleRepository;
import com.swiftdrop.pricing.domain.ZoneRepository;
import com.swiftdrop.test.security.TestPrincipal;

/** The zone a user is assigned to: dispatcher hub, courier home hub, or not applicable. */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id="})
@AutoConfigureMockMvc
class UserZoneApiTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final String ZONE_URL = "/api/v1/users/" + USER + "/zone";

    @Autowired
    private MockMvc mvc;

    /** Mocked as AccountService (the UserDirectory implementation) so BootstrapAdminService keeps its real bean type. */
    @MockitoBean
    private AccountService accounts;
    @MockitoBean
    private HubService hubs;
    @MockitoBean
    private CourierProfileService profileService;

    @MockitoBean
    private CourierProfileRepository profiles;
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

    private static Hub hub(String code, String name) {
        Hub hub = BeanUtils.instantiateClass(Hub.class);
        ReflectionTestUtils.setField(hub, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(hub, "code", code);
        ReflectionTestUtils.setField(hub, "name", name);
        ReflectionTestUtils.setField(hub, "main", false);
        ReflectionTestUtils.setField(hub, "active", true);
        return hub;
    }

    private void userWithRoles(String fullName, Set<String> roles) {
        when(accounts.findUser(USER)).thenReturn(Optional.of(
                new UserSummary(USER, fullName, "someone@example.test", roles, true)));
    }

    @Test
    void dispatcherZoneIsTheirHub() throws Exception {
        userWithRoles("Nimal Perera", Set.of("DISPATCHER"));
        when(hubs.hubForDispatcher(USER)).thenReturn(Optional.of(hub("LK-UP", "Uva Province Hub")));

        mvc.perform(get(ZONE_URL).with(TestPrincipal.admin().postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basis").value("DISPATCHER_HUB"))
                .andExpect(jsonPath("$.zoneCode").value("LK-UP"))
                .andExpect(jsonPath("$.zoneName").value("Uva Province Hub"));
    }

    @Test
    void dispatcherWithoutHubHasNoZoneCode() throws Exception {
        userWithRoles("Nimal Perera", Set.of("DISPATCHER"));
        when(hubs.hubForDispatcher(USER)).thenReturn(Optional.empty());

        mvc.perform(get(ZONE_URL).with(TestPrincipal.admin().postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basis").value("DISPATCHER_HUB"))
                .andExpect(jsonPath("$.zoneCode").doesNotExist());
    }

    @Test
    void courierZoneIsTheirHomeHub() throws Exception {
        userWithRoles("Kasun Silva", Set.of("COURIER"));
        UUID homeHubId = UUID.randomUUID();
        CourierProfile profile = BeanUtils.instantiateClass(CourierProfile.class);
        ReflectionTestUtils.setField(profile, "homeHubId", homeHubId);
        when(profileService.findByUserId(USER)).thenReturn(Optional.of(profile));
        when(hubs.requireHub(homeHubId)).thenReturn(hub("LK-WP", "Colombo Main Hub (Western Province)"));

        mvc.perform(get(ZONE_URL).with(TestPrincipal.admin().postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basis").value("COURIER_HOME_HUB"))
                .andExpect(jsonPath("$.zoneCode").value("LK-WP"));
    }

    @Test
    void courierWithoutProfileHasNoZoneCode() throws Exception {
        userWithRoles("Kasun Silva", Set.of("COURIER"));
        when(profileService.findByUserId(USER)).thenReturn(Optional.empty());

        mvc.perform(get(ZONE_URL).with(TestPrincipal.admin().postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basis").value("COURIER_HOME_HUB"))
                .andExpect(jsonPath("$.zoneCode").doesNotExist());
    }

    @Test
    void merchantAndCustomerZoneIsNotApplicable() throws Exception {
        for (String role : List.of("MERCHANT", "CUSTOMER")) {
            userWithRoles("Someone", Set.of(role));
            mvc.perform(get(ZONE_URL).with(TestPrincipal.admin().postProcessor()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.basis").value("NOT_APPLICABLE"))
                    .andExpect(jsonPath("$.zoneCode").doesNotExist());
        }
    }

    @Test
    void unknownUserIsNotFound() throws Exception {
        when(accounts.findUser(USER)).thenReturn(Optional.empty());

        mvc.perform(get(ZONE_URL).with(TestPrincipal.admin().postProcessor())).andExpect(status().isNotFound());
    }

    @Test
    void zoneIsAdminOnly() throws Exception {
        for (TestPrincipal other : List.of(TestPrincipal.dispatcher(), TestPrincipal.customer(),
                TestPrincipal.merchant(), TestPrincipal.courier())) {
            mvc.perform(get(ZONE_URL).with(other.postProcessor())).andExpect(status().isForbidden());
        }
        mvc.perform(get(ZONE_URL)).andExpect(status().isUnauthorized());
    }

    private static final String CHANGE_BODY = "{\"hubCode\":\"LK-WP\"}";

    @Test
    void adminChangesADispatchersZoneThroughTheirHub() throws Exception {
        userWithRoles("Nimal Perera", Set.of("DISPATCHER"));
        when(hubs.assignDispatcher(USER, "LK-WP")).thenReturn(hub("LK-WP", "Colombo Main Hub (Western Province)"));

        mvc.perform(put(ZONE_URL).with(TestPrincipal.admin().postProcessor())
                        .contentType(MediaType.APPLICATION_JSON).content(CHANGE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basis").value("DISPATCHER_HUB"))
                .andExpect(jsonPath("$.zoneCode").value("LK-WP"));
        verify(hubs).assignDispatcher(USER, "LK-WP");
    }

    @Test
    void adminChangesACouriersHomeHub() throws Exception {
        userWithRoles("Kasun Silva", Set.of("COURIER"));
        UUID profileId = UUID.randomUUID();
        CourierProfile profile = BeanUtils.instantiateClass(CourierProfile.class);
        ReflectionTestUtils.setField(profile, "id", profileId);
        when(profileService.findByUserId(USER)).thenReturn(Optional.of(profile));
        Hub wp = hub("LK-WP", "Colombo Main Hub (Western Province)");
        when(hubs.findActiveByCode("LK-WP")).thenReturn(Optional.of(wp));

        mvc.perform(put(ZONE_URL).with(TestPrincipal.admin().postProcessor())
                        .contentType(MediaType.APPLICATION_JSON).content(CHANGE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basis").value("COURIER_HOME_HUB"))
                .andExpect(jsonPath("$.zoneCode").value("LK-WP"));
        verify(profileService).assignHomeHub(profileId, wp.getId());
    }

    @Test
    void aCourierWithoutAProfileCannotBeZoned() throws Exception {
        userWithRoles("Kasun Silva", Set.of("COURIER"));
        when(profileService.findByUserId(USER)).thenReturn(Optional.empty());

        mvc.perform(put(ZONE_URL).with(TestPrincipal.admin().postProcessor())
                        .contentType(MediaType.APPLICATION_JSON).content(CHANGE_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void anUnknownHubCodeIsNotFound() throws Exception {
        userWithRoles("Kasun Silva", Set.of("COURIER"));
        CourierProfile profile = BeanUtils.instantiateClass(CourierProfile.class);
        when(profileService.findByUserId(USER)).thenReturn(Optional.of(profile));
        when(hubs.findActiveByCode("XX")).thenReturn(Optional.empty());

        mvc.perform(put(ZONE_URL).with(TestPrincipal.admin().postProcessor())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"hubCode\":\"XX\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void merchantsAndCustomersCannotBeZoned() throws Exception {
        userWithRoles("Someone", Set.of("MERCHANT"));

        mvc.perform(put(ZONE_URL).with(TestPrincipal.admin().postProcessor())
                        .contentType(MediaType.APPLICATION_JSON).content(CHANGE_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void changingAZoneIsAdminOnlyAndNeedsAHubCode() throws Exception {
        for (TestPrincipal other : List.of(TestPrincipal.dispatcher(), TestPrincipal.customer(),
                TestPrincipal.merchant(), TestPrincipal.courier())) {
            mvc.perform(put(ZONE_URL).with(other.postProcessor())
                            .contentType(MediaType.APPLICATION_JSON).content(CHANGE_BODY))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(put(ZONE_URL).contentType(MediaType.APPLICATION_JSON).content(CHANGE_BODY))
                .andExpect(status().isUnauthorized());

        userWithRoles("Nimal Perera", Set.of("DISPATCHER"));
        mvc.perform(put(ZONE_URL).with(TestPrincipal.admin().postProcessor())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(hubs, org.mockito.Mockito.never()).assignDispatcher(eq(USER), org.mockito.ArgumentMatchers.any());
    }
}
