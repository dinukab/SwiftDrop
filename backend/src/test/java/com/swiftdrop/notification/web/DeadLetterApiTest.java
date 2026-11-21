package com.swiftdrop.notification.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.swiftdrop.delivery.api.ParcelAssignment;
import com.swiftdrop.delivery.api.ParcelLegs;
import com.swiftdrop.delivery.api.ParcelQueries;
import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.OutboxEventRepository;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository;
import com.swiftdrop.dispatch.application.AssignmentHistoryService;
import com.swiftdrop.dispatch.application.HubService;
import com.swiftdrop.dispatch.domain.CourierProfileRepository;
import com.swiftdrop.identity.domain.ExternalIdentityRepository;
import com.swiftdrop.identity.domain.GoogleLoginCodeRepository;
import com.swiftdrop.identity.domain.UserRepository;
import com.swiftdrop.merchant.domain.MerchantApiKeyRepository;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.merchant.domain.WebhookDeliveryRepository;
import com.swiftdrop.merchant.domain.WebhookEndpointRepository;
import com.swiftdrop.notification.application.DeadLetter;
import com.swiftdrop.notification.application.DeadLetterReplayService;
import com.swiftdrop.notification.domain.DeadLetterReplay;
import com.swiftdrop.pricing.domain.PricingRuleRepository;
import com.swiftdrop.pricing.domain.ZoneRepository;
import com.swiftdrop.test.security.TestPrincipal;

/** The admin dead-letter endpoints: who may call them, what they return, and that bodies never leave the server. */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id="})
@AutoConfigureMockMvc
class DeadLetterApiTest {

    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID EVENT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");
    private static final String BASE = "/api/v1/admin/dead-letters";
    private static final String QUEUE = "notification.parcel-status-changed";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DeadLetterReplayService service;

    // The test context has no database: every repository-backed bean the application wires is replaced
    @MockitoBean
    private ParcelQueries parcelQueries;
    @MockitoBean
    private HubService hubService;
    @MockitoBean
    private AssignmentHistoryService historyService;
    @MockitoBean
    private ParcelLegs parcelLegs;
    @MockitoBean
    private ParcelAssignment parcelAssignment;
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

    // ---- who may call ----

    @Test
    void anonymousCallersAreRejected() throws Exception {
        mvc.perform(get(BASE).with(TestPrincipal.anonymous().postProcessor())).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/replay").with(TestPrincipal.anonymous().postProcessor())
                .contentType(MediaType.APPLICATION_JSON).content("{\"limit\":1}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/replays").with(TestPrincipal.anonymous().postProcessor()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void everyRoleExceptAdminIsForbidden() throws Exception {
        for (TestPrincipal caller : List.of(TestPrincipal.customer(), TestPrincipal.courier(),
                TestPrincipal.merchant(), TestPrincipal.dispatcher())) {
            mvc.perform(get(BASE).with(caller.postProcessor())).andExpect(status().isForbidden());
            mvc.perform(post(BASE + "/replay").with(caller.postProcessor())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"limit\":1}"))
                    .andExpect(status().isForbidden());
            mvc.perform(get(BASE + "/replays").with(caller.postProcessor())).andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    // ---- overview ----

    @Test
    void theOverviewShowsTheDepthAndTheOldestMessagesWithoutBodies() throws Exception {
        when(service.overview()).thenReturn(new DeadLetterReplayService.Overview(QUEUE, 3,
                List.of(new DeadLetter(EVENT, "DELIVERED", "rejected", 1, NOW),
                        new DeadLetter(null, null, "delivery_limit", 5, null))));

        mvc.perform(get(BASE).with(TestPrincipal.admin(ADMIN).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queue").value(QUEUE))
                .andExpect(jsonPath("$.depth").value(3))
                .andExpect(jsonPath("$.oldest[0].eventId").value(EVENT.toString()))
                .andExpect(jsonPath("$.oldest[0].status").value("DELIVERED"))
                .andExpect(jsonPath("$.oldest[0].reason").value("rejected"))
                .andExpect(jsonPath("$.oldest[1].eventId").doesNotExist())
                .andExpect(jsonPath("$.oldest[0].body").doesNotExist())
                .andExpect(jsonPath("$.oldest[0].payload").doesNotExist());
    }

    @Test
    void anUnavailableBrokerIsReportedAs503WithoutBrokerText() throws Exception {
        when(service.overview()).thenThrow(new AmqpConnectException(new RuntimeException("secret broker detail")));

        mvc.perform(get(BASE).with(TestPrincipal.admin(ADMIN).postProcessor()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("The message broker is not available"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
    }

    // ---- replay ----

    @Test
    void aReplayIsDoneAsTheCallerFromTheTokenAndReportsWhatMoved() throws Exception {
        when(service.replay(eq(ADMIN), eq(5))).thenReturn(new DeadLetterReplayService.Outcome(
                List.of(new DeadLetter(EVENT, "DELIVERED", "rejected", 1, NOW),
                        new DeadLetter(null, null, "rejected", 1, NOW)),
                null));

        mvc.perform(post(BASE + "/replay").with(TestPrincipal.admin(ADMIN).postProcessor())
                .contentType(MediaType.APPLICATION_JSON).content("{\"limit\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(2))
                .andExpect(jsonPath("$.eventIds.length()").value(1))
                .andExpect(jsonPath("$.eventIds[0]").value(EVENT.toString()))
                .andExpect(jsonPath("$.stopReason").doesNotExist());
        verify(service).replay(ADMIN, 5);
    }

    @Test
    void aReplayThatStoppedEarlySaysWhy() throws Exception {
        when(service.replay(any(), anyInt())).thenReturn(
                new DeadLetterReplayService.Outcome(List.of(), "NOT_CONFIRMED"));

        mvc.perform(post(BASE + "/replay").with(TestPrincipal.admin(ADMIN).postProcessor())
                .contentType(MediaType.APPLICATION_JSON).content("{\"limit\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(0))
                .andExpect(jsonPath("$.stopReason").value("NOT_CONFIRMED"));
    }

    @Test
    void theReplayLimitMustBeBetweenOneAndFifty() throws Exception {
        for (String body : List.of("{\"limit\":0}", "{\"limit\":51}", "{\"limit\":-3}", "{}", "{\"limit\":null}")) {
            mvc.perform(post(BASE + "/replay").with(TestPrincipal.admin(ADMIN).postProcessor())
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void aMalformedBodyIsRejected() throws Exception {
        mvc.perform(post(BASE + "/replay").with(TestPrincipal.admin(ADMIN).postProcessor())
                .contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Malformed request body"));
        verifyNoInteractions(service);
    }

    @Test
    void theAdminIdCannotBeSuppliedByTheRequest() throws Exception {
        when(service.replay(any(), anyInt())).thenReturn(new DeadLetterReplayService.Outcome(List.of(), null));

        mvc.perform(post(BASE + "/replay").with(TestPrincipal.admin(ADMIN).postProcessor())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"limit\":1,\"replayedBy\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk());
        verify(service).replay(ADMIN, 1);
    }

    // ---- audit trail ----

    @Test
    void theAuditTrailIsPaged() throws Exception {
        DeadLetterReplay row = new DeadLetterReplay(QUEUE, EVENT, "rejected", ADMIN, NOW);
        when(service.history(0, 20)).thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));

        mvc.perform(get(BASE + "/replays").with(TestPrincipal.admin(ADMIN).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].queue").value(QUEUE))
                .andExpect(jsonPath("$.items[0].eventId").value(EVENT.toString()))
                .andExpect(jsonPath("$.items[0].originalReason").value("rejected"))
                .andExpect(jsonPath("$.items[0].replayedBy").value(ADMIN.toString()))
                .andExpect(jsonPath("$.totalItems").value(1));
    }
}
