package com.swiftdrop.dispatch.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

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
import com.swiftdrop.identity.domain.Role;
import com.swiftdrop.identity.domain.User;
import com.swiftdrop.identity.domain.UserRepository;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.pricing.api.FeeCalculator;
import com.swiftdrop.pricing.domain.PricingRuleRepository;
import com.swiftdrop.pricing.domain.ZoneRepository;
import com.swiftdrop.test.security.AuthorizationMatrixExecutor;
import com.swiftdrop.test.security.AuthorizationMatrixRow;
import com.swiftdrop.test.security.TestPrincipal;

/**
 * Courier profile admin API (task 7.1): authorization matrix and error mapping.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id="})
@AutoConfigureMockMvc
class CourierProfileApiTest {

    private static final UUID ADMIN_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID COURIER_USER_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID PROFILE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");

    private static final String CREATE_BODY = """
            {"userId": "dddddddd-dddd-dddd-dddd-dddddddddddd", "phone": "+94771234567", "vehicleType": "BIKE"}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CourierProfileService profileService;

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
    private MerchantRepository merchantRepository;
    @MockitoBean
    private CourierProfileRepository courierProfiles;

    private CourierProfile profile;

    @BeforeEach
    void setUp() {
        // Any caller's account exists and is enabled; the courier's account backs the response name
        when(users.findById(any())).thenAnswer(inv -> Optional.of(
                withId(new User("stub@example.com", "hash", "Stub User", Set.of(Role.CUSTOMER)), inv.getArgument(0))));
        when(users.findById(COURIER_USER_ID)).thenReturn(Optional.of(
                withId(new User("kasun@swiftdrop.example", "hash", "Kasun Perera", Set.of(Role.COURIER)), COURIER_USER_ID)));

        profile = new CourierProfile(COURIER_USER_ID, "+94771234567", VehicleType.BIKE, NOW);
        ReflectionTestUtils.setField(profile, "id", PROFILE_ID);

        when(profileService.listProfiles(anyInt(), anyInt(), any())).thenReturn(new PageImpl<>(List.of(profile)));
        when(profileService.createProfile(eq(COURIER_USER_ID), any(), any())).thenReturn(profile);
        when(profileService.updateProfile(eq(PROFILE_ID), any(), any())).thenReturn(profile);
        when(profileService.activate(PROFILE_ID)).thenReturn(profile);
        when(profileService.deactivate(PROFILE_ID)).thenReturn(profile);
        when(profileService.getProfile(PROFILE_ID)).thenReturn(profile);
    }

    @TestFactory
    Stream<DynamicTest> courierProfileAuthorizationMatrix() {
        return AuthorizationMatrixExecutor.toDynamicTests(mvc, matrixRows());
    }

    @Test
    void theCourierListHidesProfilesOfDeletedAccounts() throws Exception {
        UUID deletedUserId = UUID.fromString("00000000-0000-0000-0000-0000000000d2");
        CourierProfile gone = new CourierProfile(deletedUserId, "+94779999999", VehicleType.VAN, NOW);
        ReflectionTestUtils.setField(gone, "id", UUID.randomUUID());
        when(profileService.listProfiles(anyInt(), anyInt(), any())).thenReturn(new PageImpl<>(List.of(profile, gone)));
        when(users.findById(deletedUserId)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/couriers").with(TestPrincipal.admin(ADMIN_ID).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].fullName").value("Kasun Perera"));
    }

    @Test
    void createReturnsProfileWithCourierNameAndPhone() throws Exception {
        mvc.perform(post("/api/v1/couriers")
                        .with(TestPrincipal.admin(ADMIN_ID).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(PROFILE_ID.toString()))
                .andExpect(jsonPath("$.fullName").value("Kasun Perera"))
                .andExpect(jsonPath("$.phone").value("+94771234567"))
                .andExpect(jsonPath("$.vehicleType").value("BIKE"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void createWithUnknownVehicleTypeIsBadRequest() throws Exception {
        mvc.perform(post("/api/v1/couriers")
                        .with(TestPrincipal.admin(ADMIN_ID).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": \"" + COURIER_USER_ID + "\", \"phone\": \"+94771234567\", \"vehicleType\": \"TRUCK\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithMissingFieldsIsBadRequest() throws Exception {
        mvc.perform(post("/api/v1/couriers")
                        .with(TestPrincipal.admin(ADMIN_ID).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(profileService, never()).createProfile(any(), any(), any());
    }

    @Test
    void createWithBadPhoneFormatIsBadRequest() throws Exception {
        when(profileService.createProfile(eq(COURIER_USER_ID), eq("0771234567"), any()))
                .thenThrow(new IllegalArgumentException("phone must be in international format, for example +94771234567"));

        mvc.perform(post("/api/v1/couriers")
                        .with(TestPrincipal.admin(ADMIN_ID).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": \"" + COURIER_USER_ID + "\", \"phone\": \"0771234567\", \"vehicleType\": \"CAR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("phone must be in international format, for example +94771234567"));
    }

    @Test
    void createDuplicateProfileIsConflict() throws Exception {
        when(profileService.createProfile(eq(COURIER_USER_ID), any(), any()))
                .thenThrow(new DuplicateCourierProfileException());

        mvc.perform(post("/api/v1/couriers")
                        .with(TestPrincipal.admin(ADMIN_ID).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void createForNonCourierUserIsConflict() throws Exception {
        when(profileService.createProfile(eq(COURIER_USER_ID), any(), any()))
                .thenThrow(new CourierUserNotEligibleException("User does not have the COURIER role"));

        mvc.perform(post("/api/v1/couriers")
                        .with(TestPrincipal.admin(ADMIN_ID).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("User does not have the COURIER role"));
    }

    @Test
    void patchActiveFalseDeactivatesWithoutTouchingDetails() throws Exception {
        mvc.perform(patch("/api/v1/couriers/" + PROFILE_ID)
                        .with(TestPrincipal.admin(ADMIN_ID).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\": false}"))
                .andExpect(status().isOk());
        verify(profileService).deactivate(PROFILE_ID);
        verify(profileService, never()).updateProfile(any(), any(), any());
    }

    @Test
    void patchUnknownProfileIsNotFound() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(profileService.updateProfile(eq(unknown), any(), isNull()))
                .thenThrow(new CourierProfileNotFoundException());

        mvc.perform(patch("/api/v1/couriers/" + unknown)
                        .with(TestPrincipal.admin(ADMIN_ID).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"phone\": \"+94770000000\"}"))
                .andExpect(status().isNotFound());
    }

    private List<AuthorizationMatrixRow> matrixRows() {
        String patchBody = "{\"vehicleType\": \"CAR\"}";
        return List.of(
                row("GET /couriers: anonymous → 401", HttpMethod.GET, "/api/v1/couriers",
                        TestPrincipal.anonymous(), HttpStatus.UNAUTHORIZED, null),
                row("GET /couriers: CUSTOMER → 403", HttpMethod.GET, "/api/v1/couriers",
                        TestPrincipal.customer(), HttpStatus.FORBIDDEN, null),
                row("GET /couriers: COURIER → 403", HttpMethod.GET, "/api/v1/couriers",
                        TestPrincipal.courier(COURIER_USER_ID), HttpStatus.FORBIDDEN, null),
                row("GET /couriers: DISPATCHER → 403", HttpMethod.GET, "/api/v1/couriers",
                        TestPrincipal.dispatcher(), HttpStatus.FORBIDDEN, null),
                row("GET /couriers: ADMIN → 200", HttpMethod.GET, "/api/v1/couriers",
                        TestPrincipal.admin(ADMIN_ID), HttpStatus.OK, null),

                row("POST /couriers: anonymous → 401", HttpMethod.POST, "/api/v1/couriers",
                        TestPrincipal.anonymous(), HttpStatus.UNAUTHORIZED, CREATE_BODY),
                row("POST /couriers: MERCHANT → 403", HttpMethod.POST, "/api/v1/couriers",
                        TestPrincipal.merchant(), HttpStatus.FORBIDDEN, CREATE_BODY),
                row("POST /couriers: COURIER → 403", HttpMethod.POST, "/api/v1/couriers",
                        TestPrincipal.courier(COURIER_USER_ID), HttpStatus.FORBIDDEN, CREATE_BODY),
                row("POST /couriers: ADMIN → 201", HttpMethod.POST, "/api/v1/couriers",
                        TestPrincipal.admin(ADMIN_ID), HttpStatus.CREATED, CREATE_BODY),

                row("PATCH /couriers/{id}: anonymous → 401", HttpMethod.PATCH, "/api/v1/couriers/" + PROFILE_ID,
                        TestPrincipal.anonymous(), HttpStatus.UNAUTHORIZED, patchBody),
                row("PATCH /couriers/{id}: COURIER → 403", HttpMethod.PATCH, "/api/v1/couriers/" + PROFILE_ID,
                        TestPrincipal.courier(COURIER_USER_ID), HttpStatus.FORBIDDEN, patchBody),
                row("PATCH /couriers/{id}: ADMIN → 200", HttpMethod.PATCH, "/api/v1/couriers/" + PROFILE_ID,
                        TestPrincipal.admin(ADMIN_ID), HttpStatus.OK, patchBody));
    }

    private static AuthorizationMatrixRow row(String name, HttpMethod method, String path,
            TestPrincipal principal, HttpStatus expected, String jsonBody) {
        return AuthorizationMatrixRow.row(name, method, path, principal, expected, req -> {
            if (jsonBody != null) {
                req.contentType(MediaType.APPLICATION_JSON).content(jsonBody);
            }
        });
    }

    private static User withId(User user, UUID id) {
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
