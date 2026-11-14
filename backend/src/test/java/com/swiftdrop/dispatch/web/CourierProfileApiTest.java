package com.swiftdrop.dispatch.web;

import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.OutboxEventRepository;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository;
import com.swiftdrop.dispatch.application.CourierProfileNotFoundException;
import com.swiftdrop.dispatch.application.CourierProfileService;
import com.swiftdrop.dispatch.application.CourierUserNotEligibleException;
import com.swiftdrop.dispatch.application.DuplicateCourierProfileException;
import com.swiftdrop.dispatch.domain.CourierProfile;
import com.swiftdrop.dispatch.domain.CourierProfileRepository;
import com.swiftdrop.dispatch.domain.VehicleType;
import com.swiftdrop.identity.domain.ExternalIdentityRepository;
import com.swiftdrop.identity.domain.GoogleLoginCodeRepository;
import com.swiftdrop.identity.domain.UserRepository;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.pricing.api.FeeCalculator;
import com.swiftdrop.pricing.domain.PricingRuleRepository;
import com.swiftdrop.pricing.domain.ZoneRepository;
import com.swiftdrop.test.security.TestPrincipal;

/** Courier profile endpoints over HTTP: roles, validation and error mapping. */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id="})
@AutoConfigureMockMvc
class CourierProfileApiTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID PROFILE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CourierProfileService profiles;

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

    private CourierProfile profile;

    @BeforeEach
    void setUp() {
        // The token converter re-checks the account on every request
        when(users.findById(any())).thenAnswer(inv -> {
            com.swiftdrop.identity.domain.User stub = new com.swiftdrop.identity.domain.User(
                    "stub@example.com", "hash", "Stub User",
                    Set.of(com.swiftdrop.identity.domain.Role.DISPATCHER, com.swiftdrop.identity.domain.Role.ADMIN,
                            com.swiftdrop.identity.domain.Role.CUSTOMER, com.swiftdrop.identity.domain.Role.COURIER,
                            com.swiftdrop.identity.domain.Role.MERCHANT));
            ReflectionTestUtils.setField(stub, "id", inv.getArgument(0));
            return Optional.of(stub);
        });
        profile = new CourierProfile(USER_ID, "+94771234567", VehicleType.BIKE, NOW);
        ReflectionTestUtils.setField(profile, "id", PROFILE_ID);
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, TestPrincipal principal) {
        return request.with(principal.postProcessor());
    }

    @Test
    void dispatcherAndAdminListProfilesWithNames() throws Exception {
        when(profiles.listProfiles(eq(true), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(profile), PageRequest.of(0, 20), 1));

        for (TestPrincipal allowed : List.of(TestPrincipal.dispatcher(), TestPrincipal.admin())) {
            mvc.perform(as(get("/api/v1/couriers?active=true"), allowed))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].id").value(PROFILE_ID.toString()))
                    .andExpect(jsonPath("$.items[0].fullName").value("Stub User"))
                    .andExpect(jsonPath("$.items[0].phone").value("+94771234567"))
                    .andExpect(jsonPath("$.items[0].vehicleType").value("BIKE"))
                    .andExpect(jsonPath("$.totalItems").value(1));
        }
    }

    @Test
    void otherRolesAndAnonymousAreRejected() throws Exception {
        for (TestPrincipal other : List.of(TestPrincipal.customer(), TestPrincipal.merchant(),
                TestPrincipal.courier())) {
            mvc.perform(as(get("/api/v1/couriers"), other)).andExpect(status().isForbidden());
            mvc.perform(as(post("/api/v1/couriers"), other).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"userId\":\"" + USER_ID + "\",\"phone\":\"+94771234567\",\"vehicleType\":\"BIKE\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(as(patch("/api/v1/couriers/" + PROFILE_ID), other).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"active\":false}")).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/couriers")).andExpect(status().isUnauthorized());
        verify(profiles, never()).createProfile(any(), any(), any());
        verify(profiles, never()).updateProfile(any(), any(), any(), any());
    }

    @Test
    void createReturns201() throws Exception {
        when(profiles.createProfile(USER_ID, "+94771234567", VehicleType.BIKE)).thenReturn(profile);

        mvc.perform(as(post("/api/v1/couriers"), TestPrincipal.admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + USER_ID + "\",\"phone\":\"+94771234567\",\"vehicleType\":\"BIKE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void invalidCreateBodiesAreBadRequests() throws Exception {
        mvc.perform(as(post("/api/v1/couriers"), TestPrincipal.dispatcher()).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalidFields", contains("phone", "userId", "vehicleType")));
        mvc.perform(as(post("/api/v1/couriers"), TestPrincipal.dispatcher()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + USER_ID + "\",\"phone\":\"+94771234567\",\"vehicleType\":\"TRUCK\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void badPhoneFormatIsBadRequestWithoutEchoingIt() throws Exception {
        when(profiles.createProfile(any(), eq("0771234567"), any()))
                .thenThrow(new IllegalArgumentException("phone must be in international format"));

        mvc.perform(as(post("/api/v1/couriers"), TestPrincipal.dispatcher()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + USER_ID + "\",\"phone\":\"0771234567\",\"vehicleType\":\"BIKE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid request"));
    }

    @Test
    void duplicateAndIneligibleUsersAreConflicts() throws Exception {
        String body = "{\"userId\":\"" + USER_ID + "\",\"phone\":\"+94771234567\",\"vehicleType\":\"BIKE\"}";
        when(profiles.createProfile(any(), any(), any())).thenThrow(new DuplicateCourierProfileException());
        mvc.perform(as(post("/api/v1/couriers"), TestPrincipal.dispatcher()).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isConflict());

        doThrow(new CourierUserNotEligibleException("User does not have the COURIER role"))
                .when(profiles).createProfile(any(), any(), any());
        mvc.perform(as(post("/api/v1/couriers"), TestPrincipal.dispatcher()).contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("User does not have the COURIER role"));
    }

    @Test
    void patchUpdatesAndUnknownIdIs404() throws Exception {
        when(profiles.updateProfile(PROFILE_ID, null, null, false)).thenReturn(profile);
        mvc.perform(as(patch("/api/v1/couriers/" + PROFILE_ID), TestPrincipal.dispatcher())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PROFILE_ID.toString()));

        UUID unknown = UUID.randomUUID();
        when(profiles.updateProfile(eq(unknown), any(), any(), any())).thenThrow(new CourierProfileNotFoundException());
        mvc.perform(as(patch("/api/v1/couriers/" + unknown), TestPrincipal.dispatcher())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isNotFound());

        mvc.perform(as(patch("/api/v1/couriers/not-a-uuid"), TestPrincipal.dispatcher())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }
}
