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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.swiftdrop.delivery.api.ParcelAssignment;
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
import com.swiftdrop.dispatch.domain.CourierProfileRepository;
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

    @Test
    void theQueueListsUnassignedParcelsWithoutPersonalData() throws Exception {
        ParcelSummary parcel = new ParcelSummary(PARCEL, "SD1234567890", ParcelStatus.CREATED, ParcelSize.SMALL, 500,
                ParcelSource.MERCHANT, null, UUID.randomUUID(), "ORD-7", null, Instant.parse("2026-11-15T10:00:00Z"));
        when(parcelQueries.findUnassignedParcels(any()))
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
