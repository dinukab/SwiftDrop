package com.swiftdrop.merchant.web;

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
import org.springframework.data.domain.Pageable;
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
import com.swiftdrop.identity.domain.ExternalIdentityRepository;
import com.swiftdrop.identity.domain.GoogleLoginCodeRepository;
import com.swiftdrop.identity.domain.Role;
import com.swiftdrop.identity.domain.User;
import com.swiftdrop.identity.domain.UserRepository;
import com.swiftdrop.merchant.application.MerchantOnboardingService;
import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.pricing.api.FeeCalculator;
import com.swiftdrop.pricing.domain.PricingRuleRepository;
import com.swiftdrop.pricing.domain.ZoneRepository;
import com.swiftdrop.test.security.AuthorizationMatrixExecutor;
import com.swiftdrop.test.security.AuthorizationMatrixRow;
import com.swiftdrop.test.security.TestPrincipal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * Authorization matrix for merchant registration and admin review (TST-2 / SD-401 / SD-402).
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id="})
@AutoConfigureMockMvc
class MerchantAuthorizationTest {

    private static final UUID ADMIN_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CUSTOMER_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID COURIER_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID MERCHANT_USER_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private static final UUID MERCHANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mvc;

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
    private com.swiftdrop.dispatch.domain.CourierProfileRepository courierProfiles;
    @MockitoBean
    private MerchantOnboardingService onboardingService;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.when(users.findById(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> {
                com.swiftdrop.identity.domain.User stubUser = new com.swiftdrop.identity.domain.User(
                        "stub@example.com", "hash", "Stub User",
                        java.util.Set.of(com.swiftdrop.identity.domain.Role.CUSTOMER));
                org.springframework.test.util.ReflectionTestUtils.setField(stubUser, "id", inv.getArgument(0));
                return java.util.Optional.of(stubUser);
            });
        User adminUser = withId(new User("admin@example.com", "hash", "Admin", Set.of(Role.ADMIN)), ADMIN_ID);
        when(users.findById(ADMIN_ID)).thenReturn(Optional.of(adminUser));

        Merchant pending = new Merchant(MERCHANT_USER_ID, "Test Business", "REG-TEST-1", "merchant@example.com", "0770000000");
        ReflectionTestUtils.setField(pending, "id", MERCHANT_ID);

        when(onboardingService.register(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(pending);
        when(onboardingService.requireOwnMerchant(MERCHANT_USER_ID)).thenReturn(pending);
        when(onboardingService.list(anyInt(), anyInt(), isNull()))
                .thenReturn(new PageImpl<>(List.of(pending)));
        when(onboardingService.list(anyInt(), anyInt(), any()))
                .thenReturn(new PageImpl<>(List.of(pending)));
        when(onboardingService.approve(eq(MERCHANT_ID), any())).thenReturn(pending);
        when(onboardingService.reject(eq(MERCHANT_ID), any(), anyString())).thenReturn(pending);
        when(merchantRepository.findById(MERCHANT_ID)).thenReturn(Optional.of(pending));
        when(merchantRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(pending)));
    }

    @TestFactory
    Stream<DynamicTest> merchantAuthorizationMatrix() {
        return AuthorizationMatrixExecutor.toDynamicTests(mvc, merchantMatrixRows());
    }

    @Test
    void executeAllMerchantMatrixRows() {
        AuthorizationMatrixExecutor.executeAll(mvc, merchantMatrixRows());
    }

    private List<AuthorizationMatrixRow> merchantMatrixRows() {
        String validRegisterBody = """
                {
                  "fullName": "Ada Merchant",
                  "businessName": "Ada's Shop",
                  "registrationNumber": "REG-ADA-1",
                  "contactEmail": "ada@shop.example",
                  "contactPhone": "0770000000",
                  "password": "correct-horse"
                }
                """;
        String validRejectBody = "{\"reason\": \"Incomplete documentation\"}";

        return List.of(
                AuthorizationMatrixRow.row(
                        "POST /api/v1/merchants: Anonymous valid request returns 201 Created",
                        HttpMethod.POST,
                        "/api/v1/merchants",
                        TestPrincipal.anonymous(),
                        HttpStatus.CREATED,
                        req -> req.contentType(MediaType.APPLICATION_JSON).content(validRegisterBody)
                ),
                AuthorizationMatrixRow.row(
                        "POST /api/v1/merchants: Invalid payload returns 400 Bad Request",
                        HttpMethod.POST,
                        "/api/v1/merchants",
                        TestPrincipal.anonymous(),
                        HttpStatus.BAD_REQUEST,
                        req -> req.contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fullName\":\"\",\"email\":\"bad\",\"password\":\"x\",\"businessName\":\"\"}")
                ),

                AuthorizationMatrixRow.row(
                        "GET /api/v1/merchants/me: Anonymous returns 401 Unauthorized",
                        HttpMethod.GET,
                        "/api/v1/merchants/me",
                        TestPrincipal.anonymous(),
                        HttpStatus.UNAUTHORIZED
                ),
                AuthorizationMatrixRow.row(
                        "GET /api/v1/merchants/me: CUSTOMER returns 403 Forbidden",
                        HttpMethod.GET,
                        "/api/v1/merchants/me",
                        TestPrincipal.customer(CUSTOMER_ID),
                        HttpStatus.FORBIDDEN
                ),
                AuthorizationMatrixRow.row(
                        "GET /api/v1/merchants/me: MERCHANT returns 200 OK",
                        HttpMethod.GET,
                        "/api/v1/merchants/me",
                        TestPrincipal.merchant(MERCHANT_USER_ID),
                        HttpStatus.OK
                ),

                AuthorizationMatrixRow.row(
                        "GET /api/v1/merchants: Anonymous returns 401 Unauthorized",
                        HttpMethod.GET,
                        "/api/v1/merchants",
                        TestPrincipal.anonymous(),
                        HttpStatus.UNAUTHORIZED
                ),
                AuthorizationMatrixRow.row(
                        "GET /api/v1/merchants: CUSTOMER returns 403 Forbidden",
                        HttpMethod.GET,
                        "/api/v1/merchants",
                        TestPrincipal.customer(CUSTOMER_ID),
                        HttpStatus.FORBIDDEN
                ),
                AuthorizationMatrixRow.row(
                        "GET /api/v1/merchants: COURIER returns 403 Forbidden",
                        HttpMethod.GET,
                        "/api/v1/merchants",
                        TestPrincipal.courier(COURIER_ID),
                        HttpStatus.FORBIDDEN
                ),
                AuthorizationMatrixRow.row(
                        "GET /api/v1/merchants: MERCHANT returns 403 Forbidden",
                        HttpMethod.GET,
                        "/api/v1/merchants",
                        TestPrincipal.merchant(MERCHANT_USER_ID),
                        HttpStatus.FORBIDDEN
                ),
                AuthorizationMatrixRow.row(
                        "GET /api/v1/merchants: ADMIN returns 200 OK",
                        HttpMethod.GET,
                        "/api/v1/merchants",
                        TestPrincipal.admin(ADMIN_ID),
                        HttpStatus.OK
                ),

                AuthorizationMatrixRow.row(
                        "POST /merchants/{id}/approval: Anonymous returns 401 Unauthorized",
                        HttpMethod.POST,
                        "/api/v1/merchants/" + MERCHANT_ID + "/approval",
                        TestPrincipal.anonymous(),
                        HttpStatus.UNAUTHORIZED
                ),
                AuthorizationMatrixRow.row(
                        "POST /merchants/{id}/approval: CUSTOMER returns 403 Forbidden",
                        HttpMethod.POST,
                        "/api/v1/merchants/" + MERCHANT_ID + "/approval",
                        TestPrincipal.customer(CUSTOMER_ID),
                        HttpStatus.FORBIDDEN
                ),
                AuthorizationMatrixRow.row(
                        "POST /merchants/{id}/approval: MERCHANT returns 403 Forbidden",
                        HttpMethod.POST,
                        "/api/v1/merchants/" + MERCHANT_ID + "/approval",
                        TestPrincipal.merchant(MERCHANT_USER_ID),
                        HttpStatus.FORBIDDEN
                ),
                AuthorizationMatrixRow.row(
                        "POST /merchants/{id}/approval: ADMIN returns 200 OK",
                        HttpMethod.POST,
                        "/api/v1/merchants/" + MERCHANT_ID + "/approval",
                        TestPrincipal.admin(ADMIN_ID),
                        HttpStatus.OK
                ),

                AuthorizationMatrixRow.row(
                        "POST /merchants/{id}/rejection: Anonymous returns 401 Unauthorized",
                        HttpMethod.POST,
                        "/api/v1/merchants/" + MERCHANT_ID + "/rejection",
                        TestPrincipal.anonymous(),
                        HttpStatus.UNAUTHORIZED,
                        req -> req.contentType(MediaType.APPLICATION_JSON).content(validRejectBody)
                ),
                AuthorizationMatrixRow.row(
                        "POST /merchants/{id}/rejection: CUSTOMER returns 403 Forbidden",
                        HttpMethod.POST,
                        "/api/v1/merchants/" + MERCHANT_ID + "/rejection",
                        TestPrincipal.customer(CUSTOMER_ID),
                        HttpStatus.FORBIDDEN,
                        req -> req.contentType(MediaType.APPLICATION_JSON).content(validRejectBody)
                ),
                AuthorizationMatrixRow.row(
                        "POST /merchants/{id}/rejection: ADMIN returns 200 OK",
                        HttpMethod.POST,
                        "/api/v1/merchants/" + MERCHANT_ID + "/rejection",
                        TestPrincipal.admin(ADMIN_ID),
                        HttpStatus.OK,
                        req -> req.contentType(MediaType.APPLICATION_JSON).content(validRejectBody)
                )
        );
    }

    private static User withId(User user, UUID id) {
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
