package com.swiftdrop.dispatch.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.swiftdrop.delivery.api.ParcelAssignment;
import com.swiftdrop.delivery.api.ParcelLegs;
import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.api.ParcelQueries;
import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelSummary;
import com.swiftdrop.delivery.api.PageResult;
import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.OutboxEventRepository;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository;
import com.swiftdrop.dispatch.domain.CourierProfile;
import com.swiftdrop.dispatch.application.AssignmentHistoryService;
import com.swiftdrop.dispatch.application.HubService;
import com.swiftdrop.dispatch.domain.AssignmentRecord;
import com.swiftdrop.delivery.api.LegType;
import com.swiftdrop.dispatch.domain.CourierProfileRepository;
import com.swiftdrop.dispatch.domain.Hub;
import com.swiftdrop.dispatch.domain.VehicleType;
import com.swiftdrop.identity.domain.ExternalIdentityRepository;
import com.swiftdrop.identity.domain.GoogleLoginCodeRepository;
import com.swiftdrop.identity.domain.Role;
import com.swiftdrop.identity.domain.User;
import com.swiftdrop.identity.domain.UserRepository;
import com.swiftdrop.merchant.domain.MerchantApiKeyRepository;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.merchant.domain.WebhookDeliveryRepository;
import com.swiftdrop.merchant.domain.WebhookEndpointRepository;
import com.swiftdrop.pricing.api.ParcelSize;
import com.swiftdrop.pricing.domain.PricingRuleRepository;
import com.swiftdrop.pricing.domain.ZoneRepository;
import com.swiftdrop.test.security.TestPrincipal;

/** Dispatcher endpoints: unassigned queue, assignment and workload (ADM-1, ADM-2). */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id="})
@AutoConfigureMockMvc
class DispatchApiTest {

    private static final UUID DISPATCHER = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID COURIER = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID PARCEL = UUID.fromString("5a0e0000-0000-0000-0000-000000000004");
    private static final String ASSIGN = "/api/v1/parcel-assignments";

    @Autowired
    private MockMvc mvc;

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

    @BeforeEach
    void theDispatcherWorksForUvaAndTheParcelWaitsThere() {
        when(hubService.hubForDispatcher(DISPATCHER)).thenReturn(Optional.of(hub("LK-UP")));
        when(parcelQueries.findParcelById(PARCEL)).thenReturn(Optional.of(parcelPickedUpIn("LK-UP")));
    }

    private static Hub hub(String code) {
        Hub hub = BeanUtils.instantiateClass(Hub.class);
        ReflectionTestUtils.setField(hub, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(hub, "code", code);
        ReflectionTestUtils.setField(hub, "name", code + " Hub");
        ReflectionTestUtils.setField(hub, "active", true);
        return hub;
    }

    private static ParcelSummary parcelPickedUpIn(String provinceCode) {
        return new ParcelSummary(PARCEL, "SD1234567890", ParcelStatus.CREATED, ParcelSize.SMALL, 500,
                ParcelSource.CUSTOMER, UUID.randomUUID(), null, null, null, Instant.parse("2026-11-17T10:00:00Z"),
                provinceCode, "LK-SP");
    }

    @Test
    void theQueueListsUnassignedParcelsWithoutPersonalData() throws Exception {
        ParcelSummary parcel = new ParcelSummary(PARCEL, "SD1234567890", ParcelStatus.CREATED, ParcelSize.SMALL, 500,
                ParcelSource.MERCHANT, null, UUID.randomUUID(), "ORD-7", null, Instant.parse("2026-11-15T10:00:00Z"));
        when(parcelQueries.findUnassignedParcels(any(), any()))
                .thenReturn(new PageResult<>(List.of(parcel), 0, 20, 1, 1));

        mvc.perform(get("/api/v1/unassigned-parcels").with(TestPrincipal.dispatcher(DISPATCHER).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].trackingNumber").value("SD1234567890"))
                .andExpect(jsonPath("$.items[0].source").value("MERCHANT"))
                .andExpect(jsonPath("$.items[0].merchantOrderReference").value("ORD-7"))
                .andExpect(jsonPath("$.items[0].customerId").doesNotExist())
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void theQueueShowsThePickupAndDropoffProvince() throws Exception {
        ParcelSummary parcel = new ParcelSummary(PARCEL, "SD1234567890", ParcelStatus.CREATED, ParcelSize.SMALL, 500,
                ParcelSource.CUSTOMER, UUID.randomUUID(), null, null, null, Instant.parse("2026-11-15T10:00:00Z"),
                "LK-UP", "LK-SP");
        when(parcelQueries.findUnassignedParcels(any(), any()))
                .thenReturn(new PageResult<>(List.of(parcel), 0, 20, 1, 1));

        mvc.perform(get("/api/v1/unassigned-parcels").with(TestPrincipal.dispatcher(DISPATCHER).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].pickupProvinceCode").value("LK-UP"))
                .andExpect(jsonPath("$.items[0].dropoffProvinceCode").value("LK-SP"))
                .andExpect(jsonPath("$.items[0].customerId").doesNotExist())
                .andExpect(jsonPath("$.items[0].phone").doesNotExist());
    }

    @Test
    void anEligibleCourierIsAssigned() throws Exception {
        courierIsEligible();

        mvc.perform(post(ASSIGN)
                        .with(TestPrincipal.dispatcher(DISPATCHER).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parcelId\":\"" + PARCEL + "\",\"courierUserId\":\"" + COURIER + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parcelId").value(PARCEL.toString()))
                .andExpect(jsonPath("$.courierUserId").value(COURIER.toString()));
        // The dispatcher is the caller from the token: it was passed through, not read from the body
        verify(parcelAssignment).assignCourier(PARCEL, COURIER, DISPATCHER);
    }

    @Test
    void anIneligibleCourierIsAConflictAndNothingIsAssigned() throws Exception {
        identityUser(COURIER, "Kamal", false, Role.COURIER);

        mvc.perform(post(ASSIGN)
                        .with(TestPrincipal.dispatcher(DISPATCHER).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parcelId\":\"" + PARCEL + "\",\"courierUserId\":\"" + COURIER + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Courier account is disabled"));
        verifyNoInteractions(parcelAssignment);
    }

    @Test
    void aParcelPastPickupIsAConflict() throws Exception {
        courierIsEligible();
        doThrow(new ParcelConflictException("A courier can only be assigned before pickup"))
                .when(parcelAssignment).assignCourier(PARCEL, COURIER, DISPATCHER);

        mvc.perform(post(ASSIGN)
                        .with(TestPrincipal.dispatcher(DISPATCHER).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parcelId\":\"" + PARCEL + "\",\"courierUserId\":\"" + COURIER + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A courier can only be assigned before pickup"));
    }

    @Test
    void anUnknownParcelIsNotFound() throws Exception {
        courierIsEligible();
        doThrow(new ParcelNotFoundException()).when(parcelAssignment).assignCourier(PARCEL, COURIER, DISPATCHER);

        mvc.perform(post(ASSIGN)
                        .with(TestPrincipal.dispatcher(DISPATCHER).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parcelId\":\"" + PARCEL + "\",\"courierUserId\":\"" + COURIER + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aParcelFromAnotherHubIsNotFoundAndNothingIsAssigned() throws Exception {
        courierIsEligible();
        when(parcelQueries.findParcelById(PARCEL)).thenReturn(Optional.of(parcelPickedUpIn("LK-SP")));

        mvc.perform(post(ASSIGN)
                        .with(TestPrincipal.dispatcher(DISPATCHER).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parcelId\":\"" + PARCEL + "\",\"courierUserId\":\"" + COURIER + "\"}"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(parcelAssignment);
    }

    @Test
    void aDispatcherWithoutAHubGetsAConflictOnTheQueueAndOnAssignment() throws Exception {
        when(hubService.hubForDispatcher(DISPATCHER)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/unassigned-parcels").with(TestPrincipal.dispatcher(DISPATCHER).postProcessor()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Dispatcher has no hub assigned"));
        mvc.perform(post(ASSIGN)
                        .with(TestPrincipal.dispatcher(DISPATCHER).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parcelId\":\"" + PARCEL + "\",\"courierUserId\":\"" + COURIER + "\"}"))
                .andExpect(status().isConflict());
        verifyNoInteractions(parcelAssignment);
    }

    @Test
    void theQueueIsFilteredByTheHubOfTheCallerNotByAnythingTheyType() throws Exception {
        when(parcelQueries.findUnassignedParcels(any(), any()))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0, 0));

        mvc.perform(get("/api/v1/unassigned-parcels?hub=LK-SP&pickupProvinceCode=LK-SP")
                        .with(TestPrincipal.dispatcher(DISPATCHER).postProcessor()))
                .andExpect(status().isOk());

        verify(parcelQueries).findUnassignedParcels(new com.swiftdrop.delivery.api.PageRequest(0, 20), "LK-UP");
    }

    @Test
    void theAssignmentHistoryListsWhoAssignedWhomAndWhetherItIsStillActive() throws Exception {
        UUID other = UUID.fromString("00000000-0000-0000-0000-0000000000d2");
        Hub uva = hub("LK-UP");
        when(hubService.hubForDispatcher(DISPATCHER)).thenReturn(Optional.of(uva));
        when(hubService.requireHub(uva.getId())).thenReturn(uva);
        AssignmentRecord first = new AssignmentRecord(PARCEL, LegType.PICKUP, COURIER, DISPATCHER, uva.getId(),
                Instant.parse("2026-11-18T09:00:00Z"));
        first.end(AssignmentRecord.EndReason.REASSIGNED, Instant.parse("2026-11-18T09:30:00Z"));
        AssignmentRecord second = new AssignmentRecord(PARCEL, LegType.PICKUP, other, DISPATCHER, uva.getId(),
                Instant.parse("2026-11-18T09:30:00Z"));
        when(historyService.history(PARCEL)).thenReturn(List.of(first, second));
        identityUser(COURIER, "Kamal Perera", true, Role.COURIER);
        identityUser(other, "Nimal Silva", true, Role.COURIER);
        identityUser(DISPATCHER, "Dina Dispatcher", true, Role.DISPATCHER);

        mvc.perform(get("/api/v1/parcels/" + PARCEL + "/assignment-history")
                        .with(TestPrincipal.dispatcher(DISPATCHER).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].courierName").value("Kamal Perera"))
                .andExpect(jsonPath("$[0].dispatcherName").value("Dina Dispatcher"))
                .andExpect(jsonPath("$[0].hubCode").value("LK-UP"))
                .andExpect(jsonPath("$[0].legType").value("PICKUP"))
                .andExpect(jsonPath("$[0].endReason").value("REASSIGNED"))
                .andExpect(jsonPath("$[0].active").value(false))
                .andExpect(jsonPath("$[1].courierName").value("Nimal Silva"))
                .andExpect(jsonPath("$[1].active").value(true))
                .andExpect(jsonPath("$[1].endedAt").doesNotExist());
    }

    @Test
    void theHistoryOfAnotherHubsParcelIsNotFound() throws Exception {
        when(parcelQueries.findParcelById(PARCEL)).thenReturn(Optional.of(parcelPickedUpIn("LK-SP")));

        mvc.perform(get("/api/v1/parcels/" + PARCEL + "/assignment-history")
                        .with(TestPrincipal.dispatcher(DISPATCHER).postProcessor()))
                .andExpect(status().isNotFound());
        verifyNoInteractions(historyService);
    }

    @Test
    void theHistoryNeedsAHubAndADispatcher() throws Exception {
        when(hubService.hubForDispatcher(DISPATCHER)).thenReturn(Optional.empty());
        String url = "/api/v1/parcels/" + PARCEL + "/assignment-history";

        mvc.perform(get(url).with(TestPrincipal.dispatcher(DISPATCHER).postProcessor()))
                .andExpect(status().isConflict());
        for (TestPrincipal other : List.of(TestPrincipal.admin(), TestPrincipal.customer(), TestPrincipal.merchant(),
                TestPrincipal.courier())) {
            mvc.perform(get(url).with(other.postProcessor())).andExpect(status().isForbidden());
        }
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        verifyNoInteractions(historyService);
    }

    @Test
    void assignmentNeedsBothIds() throws Exception {
        mvc.perform(post(ASSIGN)
                        .with(TestPrincipal.dispatcher(DISPATCHER).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parcelId\":\"" + PARCEL + "\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(parcelAssignment);
    }

    @Test
    void theWorkloadListsCouriersLeastBusyFirst() throws Exception {
        UUID busy = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
        UUID idle = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
        when(profiles.findByActive(eq(true), any())).thenReturn(new PageImpl<>(List.of(
                new CourierProfile(busy, "+94771111111", VehicleType.VAN, Instant.now()),
                new CourierProfile(idle, "+94772222222", VehicleType.CAR, Instant.now()))));
        identityUser(busy, "Busy Bill", true, Role.COURIER);
        identityUser(idle, "Idle Ida", true, Role.COURIER);
        when(parcelQueries.countActiveParcelsByCourier()).thenReturn(Map.of(busy, 3L));

        mvc.perform(get("/api/v1/courier-workloads").with(TestPrincipal.dispatcher(DISPATCHER).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Idle Ida"))
                .andExpect(jsonPath("$[0].activeParcels").value(0))
                .andExpect(jsonPath("$[1].name").value("Busy Bill"))
                .andExpect(jsonPath("$[1].activeParcels").value(3))
                .andExpect(jsonPath("$[0].phone").doesNotExist());
    }

    @Test
    void onlyADispatcherUsesTheDispatchEndpoints() throws Exception {
        for (TestPrincipal wrongRole : new TestPrincipal[] {
                TestPrincipal.customer(), TestPrincipal.merchant(DISPATCHER), TestPrincipal.courier(),
                TestPrincipal.admin()}) {
            mvc.perform(get("/api/v1/unassigned-parcels").with(wrongRole.postProcessor()))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/courier-workloads").with(wrongRole.postProcessor()))
                    .andExpect(status().isForbidden());
            mvc.perform(post(ASSIGN).with(wrongRole.postProcessor())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"parcelId\":\"" + PARCEL + "\",\"courierUserId\":\"" + COURIER + "\"}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/unassigned-parcels")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/courier-workloads")).andExpect(status().isUnauthorized());
        verifyNoInteractions(parcelAssignment, parcelQueries);
    }

    private static final UUID LEG_ID = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final UUID COLOMBO_HUB_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    private static com.swiftdrop.delivery.api.LegView legFrom(UUID sourceHub) {
        return new com.swiftdrop.delivery.api.LegView(LEG_ID, PARCEL, 2, LegType.INTER_HUB, sourceHub,
                COLOMBO_HUB_ID, null, null, null);
    }

    private String legBody() {
        return "{\"parcelId\":\"" + PARCEL + "\",\"legId\":\"" + LEG_ID + "\",\"courierUserId\":\"" + COURIER + "\"}";
    }

    @Test
    void theLegQueueShowsLegsWaitingAtTheDispatchersHub() throws Exception {
        Hub uva = hub("LK-UP");
        when(hubService.hubForDispatcher(DISPATCHER)).thenReturn(Optional.of(uva));
        when(hubService.requireHub(uva.getId())).thenReturn(uva);
        when(hubService.requireHub(COLOMBO_HUB_ID)).thenReturn(hub("LK-WP"));
        when(parcelLegs.findWaitingLegs(any(), any())).thenReturn(new PageResult<>(List.of(
                new com.swiftdrop.delivery.api.WaitingLeg(legFrom(uva.getId()), "SD1234567890", ParcelSize.SMALL, 700)),
                0, 20, 1, 1));

        mvc.perform(get("/api/v1/waiting-legs").with(TestPrincipal.dispatcher(DISPATCHER).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].legId").value(LEG_ID.toString()))
                .andExpect(jsonPath("$.items[0].trackingNumber").value("SD1234567890"))
                .andExpect(jsonPath("$.items[0].legType").value("INTER_HUB"))
                .andExpect(jsonPath("$.items[0].sourceHubCode").value("LK-UP"))
                .andExpect(jsonPath("$.items[0].destinationHubCode").value("LK-WP"))
                .andExpect(jsonPath("$.items[0].phone").doesNotExist())
                .andExpect(jsonPath("$.totalItems").value(1));
        verify(parcelLegs).findWaitingLegs(uva.getId(), new com.swiftdrop.delivery.api.PageRequest(0, 20));
    }

    @Test
    void aLegIsAssignedByTheDispatcherFromTheToken() throws Exception {
        Hub uva = hub("LK-UP");
        when(hubService.hubForDispatcher(DISPATCHER)).thenReturn(Optional.of(uva));
        when(parcelLegs.findLegs(PARCEL)).thenReturn(List.of(legFrom(uva.getId())));
        courierIsEligible();

        mvc.perform(post("/api/v1/leg-assignments")
                        .with(TestPrincipal.dispatcher(DISPATCHER).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON).content(legBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parcelId").value(PARCEL.toString()))
                .andExpect(jsonPath("$.courierUserId").value(COURIER.toString()));

        verify(parcelLegs).assignLegCourier(PARCEL, LEG_ID, COURIER, DISPATCHER);
    }

    @Test
    void aLegOfAnotherHubIsNotFoundAndAConflictFromDeliveryIsPassedOn() throws Exception {
        Hub uva = hub("LK-UP");
        when(hubService.hubForDispatcher(DISPATCHER)).thenReturn(Optional.of(uva));
        courierIsEligible();

        when(parcelLegs.findLegs(PARCEL)).thenReturn(List.of(legFrom(COLOMBO_HUB_ID)));
        mvc.perform(post("/api/v1/leg-assignments").with(TestPrincipal.dispatcher(DISPATCHER).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON).content(legBody()))
                .andExpect(status().isNotFound());
        verify(parcelLegs, org.mockito.Mockito.never()).assignLegCourier(any(), any(), any(), any());

        when(parcelLegs.findLegs(PARCEL)).thenReturn(List.of(legFrom(uva.getId())));
        doThrow(new ParcelConflictException("The previous leg is not complete yet"))
                .when(parcelLegs).assignLegCourier(PARCEL, LEG_ID, COURIER, DISPATCHER);
        mvc.perform(post("/api/v1/leg-assignments").with(TestPrincipal.dispatcher(DISPATCHER).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON).content(legBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The previous leg is not complete yet"));
    }

    @Test
    void theLegEndpointsAreForDispatchersWithAHubOnly() throws Exception {
        for (TestPrincipal other : List.of(TestPrincipal.admin(), TestPrincipal.customer(),
                TestPrincipal.merchant(), TestPrincipal.courier())) {
            mvc.perform(get("/api/v1/waiting-legs").with(other.postProcessor())).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/leg-assignments").with(other.postProcessor())
                    .contentType(MediaType.APPLICATION_JSON).content(legBody())).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/waiting-legs")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/leg-assignments").contentType(MediaType.APPLICATION_JSON).content(legBody()))
                .andExpect(status().isUnauthorized());

        when(hubService.hubForDispatcher(DISPATCHER)).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/waiting-legs").with(TestPrincipal.dispatcher(DISPATCHER).postProcessor()))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/leg-assignments").with(TestPrincipal.dispatcher(DISPATCHER).postProcessor())
                .contentType(MediaType.APPLICATION_JSON).content(legBody())).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/leg-assignments").with(TestPrincipal.dispatcher(DISPATCHER).postProcessor())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
    }

    private void courierIsEligible() {
        identityUser(COURIER, "Kamal", true, Role.COURIER);
        when(profiles.findByUserId(COURIER)).thenReturn(Optional.of(
                new CourierProfile(COURIER, "+94771234567", VehicleType.BIKE, Instant.now())));
    }

    /** Stubs the identity user behind {@code UserDirectory} (the repository), as the identity tests do. */
    private void identityUser(UUID id, String name, boolean enabled, Role role) {
        User user = new User(name.toLowerCase().replace(' ', '.') + "@example.com", "hash", name, Set.of(role));
        ReflectionTestUtils.setField(user, "id", id);
        user.setEnabled(enabled);
        when(users.findById(id)).thenReturn(Optional.of(user));
    }
}
