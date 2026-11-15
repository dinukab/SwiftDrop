package com.swiftdrop.dispatch.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

import com.swiftdrop.delivery.api.PageResult;
import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelSummary;
import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.OutboxEventRepository;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository;
import com.swiftdrop.dispatch.application.CourierNotEligibleException;
import com.swiftdrop.dispatch.application.CourierWorkload;
import com.swiftdrop.dispatch.application.DispatchService;
import com.swiftdrop.dispatch.domain.CourierProfileRepository;
import com.swiftdrop.dispatch.domain.VehicleType;
import com.swiftdrop.identity.domain.ExternalIdentityRepository;
import com.swiftdrop.identity.domain.GoogleLoginCodeRepository;
import com.swiftdrop.identity.domain.Role;
import com.swiftdrop.identity.domain.User;
import com.swiftdrop.identity.domain.UserRepository;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.pricing.api.FeeCalculator;
import com.swiftdrop.pricing.api.ParcelSize;
import com.swiftdrop.pricing.domain.PricingRuleRepository;
import com.swiftdrop.pricing.domain.ZoneRepository;
import com.swiftdrop.test.security.TestPrincipal;

/** Unassigned-parcel and assignment endpoints over HTTP: DISPATCHER only, error mapping. */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id="})
@AutoConfigureMockMvc
class DispatchApiTest {

    private static final UUID PARCEL_ID = UUID.fromString("5a0e0000-0000-0000-0000-000000000001");
    private static final UUID COURIER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID DISPATCHER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final String BODY =
            "{\"parcelId\":\"" + PARCEL_ID + "\",\"courierUserId\":\"" + COURIER_ID + "\"}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DispatchService dispatch;

    @MockitoBean
    private UserRepository users;
    @MockitoBean
    private ExternalIdentityRepository identities;
    @MockitoBean
    private GoogleLoginCodeRepository googleCodes;
    @MockitoBean
    private ParcelRepository parcels;
    @MockitoBean
    private ParcelStatusHistoryRepository parcelHistory;
    @MockitoBean
    private DeliveryProofRepository deliveryProofs;
    @MockitoBean
    private OutboxEventRepository outboxEvents;
    @MockitoBean
    private FeeCalculator feeCalculator;
    @MockitoBean
    private ZoneRepository zones;
    @MockitoBean
    private PricingRuleRepository pricingRules;
    @MockitoBean
    private CourierProfileRepository courierProfiles;
    @MockitoBean
    private MerchantRepository merchants;

    @BeforeEach
    void setUp() {
        // The token converter re-checks the account on every request
        when(users.findById(any())).thenAnswer(inv -> {
            User stub = new User("stub@example.com", "hash", "Stub User", Set.of(Role.values()));
            ReflectionTestUtils.setField(stub, "id", inv.getArgument(0));
            return Optional.of(stub);
        });
    }

    @Test
    void dispatcherListsUnassignedParcelsWithoutAddresses() throws Exception {
        ParcelSummary parcel = new ParcelSummary(PARCEL_ID, "SD7K3M9Q2XWB", ParcelStatus.CREATED, ParcelSize.SMALL,
                1000, ParcelSource.CUSTOMER, UUID.randomUUID(), null, null, null,
                Instant.parse("2026-11-14T09:00:00Z"));
        when(dispatch.unassigned(0, 20)).thenReturn(new PageResult<>(List.of(parcel), 0, 20, 1, 1));

        mvc.perform(get("/api/v1/unassigned-parcels").with(TestPrincipal.dispatcher().postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(PARCEL_ID.toString()))
                .andExpect(jsonPath("$.items[0].trackingNumber").value("SD7K3M9Q2XWB"))
                .andExpect(jsonPath("$.items[0].size").value("SMALL"))
                .andExpect(jsonPath("$.items[0].customerId").doesNotExist())
                .andExpect(jsonPath("$.items[0].phone").doesNotExist())
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void everyoneButDispatchersIsRejected() throws Exception {
        for (TestPrincipal other : List.of(TestPrincipal.admin(), TestPrincipal.customer(),
                TestPrincipal.merchant(), TestPrincipal.courier())) {
            mvc.perform(get("/api/v1/unassigned-parcels").with(other.postProcessor()))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/parcel-assignments").with(other.postProcessor())
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/unassigned-parcels")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/parcel-assignments").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verify(dispatch, never()).assign(any(), any(), any());
    }

    @Test
    void assignUsesTheTokenSubjectAsDispatcher() throws Exception {
        mvc.perform(post("/api/v1/parcel-assignments")
                        .with(TestPrincipal.dispatcher(DISPATCHER_ID).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parcelId").value(PARCEL_ID.toString()))
                .andExpect(jsonPath("$.courierUserId").value(COURIER_ID.toString()));

        verify(dispatch).assign(PARCEL_ID, COURIER_ID, DISPATCHER_ID);
    }

    @Test
    void missingFieldsAreBadRequests() throws Exception {
        mvc.perform(post("/api/v1/parcel-assignments").with(TestPrincipal.dispatcher().postProcessor())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(dispatch, never()).assign(any(), any(), any());
    }

    @Test
    void errorsMapTo404And409() throws Exception {
        doThrow(new ParcelNotFoundException()).when(dispatch).assign(eq(PARCEL_ID), any(), any());
        mvc.perform(post("/api/v1/parcel-assignments").with(TestPrincipal.dispatcher().postProcessor())
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isNotFound());

        doThrow(new ParcelConflictException("Parcel can no longer be assigned"))
                .when(dispatch).assign(eq(PARCEL_ID), any(), any());
        mvc.perform(post("/api/v1/parcel-assignments").with(TestPrincipal.dispatcher().postProcessor())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Parcel can no longer be assigned"));

        doThrow(new CourierNotEligibleException("Courier profile is inactive"))
                .when(dispatch).assign(eq(PARCEL_ID), any(), any());
        mvc.perform(post("/api/v1/parcel-assignments").with(TestPrincipal.dispatcher().postProcessor())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Courier profile is inactive"));
    }

    @Test
    void workloadsAreForDispatchersOnly() throws Exception {
        when(dispatch.workloads()).thenReturn(List.of(
                new CourierWorkload(COURIER_ID, "Kasun Perera", VehicleType.BIKE, 2)));

        mvc.perform(get("/api/v1/courier-workloads").with(TestPrincipal.dispatcher().postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].courierUserId").value(COURIER_ID.toString()))
                .andExpect(jsonPath("$[0].fullName").value("Kasun Perera"))
                .andExpect(jsonPath("$[0].vehicleType").value("BIKE"))
                .andExpect(jsonPath("$[0].activeParcels").value(2));

        for (TestPrincipal other : List.of(TestPrincipal.admin(), TestPrincipal.customer(),
                TestPrincipal.merchant(), TestPrincipal.courier())) {
            mvc.perform(get("/api/v1/courier-workloads").with(other.postProcessor()))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/courier-workloads")).andExpect(status().isUnauthorized());
    }
}
