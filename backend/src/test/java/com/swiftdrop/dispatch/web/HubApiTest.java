package com.swiftdrop.dispatch.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
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
import com.swiftdrop.dispatch.application.DispatcherNotEligibleException;
import com.swiftdrop.dispatch.application.DispatcherUserNotFoundException;
import com.swiftdrop.dispatch.application.HubNotFoundException;
import com.swiftdrop.dispatch.application.HubService;
import com.swiftdrop.dispatch.domain.CourierProfileRepository;
import com.swiftdrop.dispatch.domain.Hub;
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

/** Hub endpoints: roles, the hub coming from the token, and error mapping. */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id="})
@AutoConfigureMockMvc
class HubApiTest {

    private static final UUID DISPATCHER = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final String BODY = "{\"hubCode\":\"LK-UP\"}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private HubService hubs;

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

    private static Hub hub(String code, String name, boolean main) {
        Hub hub = BeanUtils.instantiateClass(Hub.class);
        ReflectionTestUtils.setField(hub, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(hub, "code", code);
        ReflectionTestUtils.setField(hub, "name", name);
        ReflectionTestUtils.setField(hub, "main", main);
        ReflectionTestUtils.setField(hub, "active", true);
        return hub;
    }

    @Test
    void dispatcherAndAdminListHubsMainFirst() throws Exception {
        when(hubs.listHubs()).thenReturn(List.of(
                hub("LK-WP", "Colombo Main Hub (Western Province)", true), hub("LK-UP", "Uva Province Hub", false)));

        for (TestPrincipal allowed : List.of(TestPrincipal.dispatcher(), TestPrincipal.admin())) {
            mvc.perform(get("/api/v1/hubs").with(allowed.postProcessor()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].code").value("LK-WP"))
                    .andExpect(jsonPath("$[0].main").value(true))
                    .andExpect(jsonPath("$[1].code").value("LK-UP"))
                    .andExpect(jsonPath("$[1].id").doesNotExist());
        }
    }

    @Test
    void hubListIsClosedToOtherRolesAndAnonymous() throws Exception {
        for (TestPrincipal other : List.of(TestPrincipal.customer(), TestPrincipal.merchant(),
                TestPrincipal.courier())) {
            mvc.perform(get("/api/v1/hubs").with(other.postProcessor())).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/hubs")).andExpect(status().isUnauthorized());
    }

    @Test
    void dispatcherSeesTheirOwnHubFromTheToken() throws Exception {
        when(hubs.hubForDispatcher(DISPATCHER)).thenReturn(Optional.of(hub("LK-UP", "Uva Province Hub", false)));

        mvc.perform(get("/api/v1/dispatchers/me").with(TestPrincipal.dispatcher(DISPATCHER).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(DISPATCHER.toString()))
                .andExpect(jsonPath("$.hub.code").value("LK-UP"));
    }

    @Test
    void dispatcherWithoutHubGetsNullHub() throws Exception {
        when(hubs.hubForDispatcher(DISPATCHER)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/dispatchers/me").with(TestPrincipal.dispatcher(DISPATCHER).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hub").doesNotExist());
    }

    @Test
    void meIsForDispatchersOnly() throws Exception {
        for (TestPrincipal other : List.of(TestPrincipal.admin(), TestPrincipal.customer(),
                TestPrincipal.merchant(), TestPrincipal.courier())) {
            mvc.perform(get("/api/v1/dispatchers/me").with(other.postProcessor())).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/dispatchers/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminAssignsAHubToADispatcher() throws Exception {
        when(hubs.assignDispatcher(DISPATCHER, "LK-UP")).thenReturn(hub("LK-UP", "Uva Province Hub", false));

        mvc.perform(put("/api/v1/dispatchers/" + DISPATCHER + "/hub").with(TestPrincipal.admin().postProcessor())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(DISPATCHER.toString()))
                .andExpect(jsonPath("$.hub.code").value("LK-UP"));
    }

    @Test
    void onlyAdminsMayAssignAHub() throws Exception {
        for (TestPrincipal other : List.of(TestPrincipal.dispatcher(), TestPrincipal.customer(),
                TestPrincipal.merchant(), TestPrincipal.courier())) {
            mvc.perform(put("/api/v1/dispatchers/" + DISPATCHER + "/hub").with(other.postProcessor())
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(put("/api/v1/dispatchers/" + DISPATCHER + "/hub")
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        verify(hubs, never()).assignDispatcher(any(), any());
    }

    @Test
    void assignmentErrorsMapTo400404And409() throws Exception {
        TestPrincipal admin = TestPrincipal.admin();
        String url = "/api/v1/dispatchers/" + DISPATCHER + "/hub";

        mvc.perform(put(url).with(admin.postProcessor()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        doThrow(new HubNotFoundException()).when(hubs).assignDispatcher(eq(DISPATCHER), any());
        mvc.perform(put(url).with(admin.postProcessor()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());

        doThrow(new DispatcherUserNotFoundException()).when(hubs).assignDispatcher(eq(DISPATCHER), any());
        mvc.perform(put(url).with(admin.postProcessor()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());

        doThrow(new DispatcherNotEligibleException("User does not have the DISPATCHER role"))
                .when(hubs).assignDispatcher(eq(DISPATCHER), any());
        mvc.perform(put(url).with(admin.postProcessor()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("User does not have the DISPATCHER role"));
    }
}
