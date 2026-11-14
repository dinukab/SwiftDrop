package com.swiftdrop.identity.web;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.OutboxEventRepository;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository;
import com.swiftdrop.identity.application.CourierApprovalService;
import com.swiftdrop.identity.domain.CourierApproval;
import com.swiftdrop.identity.domain.CourierApprovalRepository;
import com.swiftdrop.identity.domain.CourierApprovalStatus;
import com.swiftdrop.identity.domain.ExternalIdentityRepository;
import com.swiftdrop.identity.domain.GoogleLoginCodeRepository;
import com.swiftdrop.identity.domain.Role;
import com.swiftdrop.identity.domain.User;
import com.swiftdrop.identity.domain.UserRepository;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.pricing.api.FeeCalculator;
import com.swiftdrop.pricing.domain.PricingRuleRepository;
import com.swiftdrop.pricing.domain.ZoneRepository;
import com.swiftdrop.test.security.TestPrincipal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Public courier registration with admin approval: registration, duplicate/invalid input,
 * role-injection resistance, pending/rejected/approved token authority, and admin review
 * authorization (TST-2).
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id="})
@AutoConfigureMockMvc
class CourierRegistrationApiTest {

    private static final UUID ADMIN_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CUSTOMER_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID COURIER_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID PENDING_COURIER_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

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
    private MerchantRepository merchants;
    @MockitoBean
    private CourierApprovalRepository courierApprovals;
    @MockitoBean
    private com.swiftdrop.dispatch.domain.CourierProfileRepository courierProfiles;
    @MockitoBean
    private CourierApprovalService courierApprovalService;

    private CourierApproval pendingApproval;
    private CourierApproval approvedApproval;
    private CourierApproval rejectedApproval;

    @BeforeEach
    void setUp() {
        pendingApproval = approval(PENDING_COURIER_ID, CourierApprovalStatus.PENDING);
        approvedApproval = approval(COURIER_ID, CourierApprovalStatus.APPROVED);
        rejectedApproval = approval(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"), CourierApprovalStatus.REJECTED);

        when(courierApprovals.findByUserId(PENDING_COURIER_ID)).thenReturn(Optional.of(pendingApproval));
        when(courierApprovals.findByUserId(COURIER_ID)).thenReturn(Optional.of(approvedApproval));

        User admin = withId(new User("admin@example.com", "hash", "Admin", Set.of(Role.DISPATCHER_ADMIN)), ADMIN_ID);
        when(users.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
    }

    private static CourierApproval approval(UUID userId, CourierApprovalStatus status) {
        CourierApproval approval = new CourierApproval(userId);
        if (status == CourierApprovalStatus.APPROVED) {
            approval.approve(ADMIN_ID);
        } else if (status == CourierApprovalStatus.REJECTED) {
            approval.reject(ADMIN_ID, "Incomplete documents");
        }
        ReflectionTestUtils.setField(approval, "status", status);
        return approval;
    }

    private static User withId(User user, UUID id) {
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    // ---- Public registration ----

    @Test
    void anonymousCanRegisterCourier() throws Exception {
        when(courierApprovalService.register(anyString(), anyString(), anyString())).thenReturn(pendingApproval);
        when(courierApprovalService.findUser(PENDING_COURIER_ID)).thenReturn(
                withId(new User("courier@example.com", "hash", "Courier One", Set.of(Role.COURIER)), PENDING_COURIER_ID));

        mvc.perform(post("/api/v1/courier-registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Courier One","email":"courier@example.com","password":"correct-horse"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.userId").value(PENDING_COURIER_ID.toString()))
                .andExpect(jsonPath("$.email").value("courier@example.com"));
    }

    @Test
    void invalidCourierRegistrationReturns400() throws Exception {
        mvc.perform(post("/api/v1/courier-registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"\",\"email\":\"bad\",\"password\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void roleInjectionStillRegistersCourierOnly() throws Exception {
        when(courierApprovalService.register(anyString(), anyString(), anyString())).thenReturn(pendingApproval);
        when(courierApprovalService.findUser(PENDING_COURIER_ID)).thenReturn(
                withId(new User("c@example.com", "hash", "C", Set.of(Role.COURIER)), PENDING_COURIER_ID));

        mvc.perform(post("/api/v1/courier-registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"C","email":"c@example.com","password":"correct-horse",
                                 "role":"DISPATCHER_ADMIN","roles":["DISPATCHER_ADMIN"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    // ---- Admin approval authorization ----

    @Test
    void anonymousCannotListCourierRegistrations() throws Exception {
        mvc.perform(get("/api/v1/courier-registrations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotApproveCourier() throws Exception {
        mvc.perform(post("/api/v1/courier-registrations/" + PENDING_COURIER_ID + "/approval")
                        .with(TestPrincipal.customer(CUSTOMER_ID).postProcessor()))
                .andExpect(status().isForbidden());
    }

    @Test
    void merchantCannotApproveCourier() throws Exception {
        mvc.perform(post("/api/v1/courier-registrations/" + PENDING_COURIER_ID + "/approval")
                        .with(TestPrincipal.merchant(UUID.randomUUID()).postProcessor()))
                .andExpect(status().isForbidden());
    }

    @Test
    void courierCannotApproveCourier() throws Exception {
        mvc.perform(post("/api/v1/courier-registrations/" + PENDING_COURIER_ID + "/approval")
                        .with(TestPrincipal.courier(COURIER_ID).postProcessor()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanApproveAndRejectCourier() throws Exception {
        when(courierApprovalService.approve(PENDING_COURIER_ID, ADMIN_ID)).thenReturn(approvedApproval);
        when(courierApprovalService.findUser(COURIER_ID)).thenReturn(
                withId(new User("c@example.com", "hash", "C", Set.of(Role.COURIER)), COURIER_ID));
        mvc.perform(post("/api/v1/courier-registrations/" + PENDING_COURIER_ID + "/approval")
                        .with(TestPrincipal.dispatcherAdmin(ADMIN_ID).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        when(courierApprovalService.reject(PENDING_COURIER_ID, ADMIN_ID, "bad docs")).thenReturn(rejectedApproval);
        when(courierApprovalService.findUser(rejectedApproval.getUserId())).thenReturn(
                withId(new User("r@example.com", "hash", "R", Set.of(Role.COURIER)), rejectedApproval.getUserId()));
        mvc.perform(post("/api/v1/courier-registrations/" + PENDING_COURIER_ID + "/rejection")
                        .with(TestPrincipal.dispatcherAdmin(ADMIN_ID).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"bad docs\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    // ---- Token authority for pending couriers ----

    @Test
    void pendingCourierTokenLacksCourierAuthority() throws Exception {
        // Simulate AccessTokenIssuer.effectiveRoles: pending approval strips COURIER.
        User pendingUser = withId(new User("p@example.com", passwordEncoder.encode("correct-horse"), "P", Set.of(Role.COURIER)), PENDING_COURIER_ID);
        when(users.findByEmail("p@example.com")).thenReturn(Optional.of(pendingUser));
        when(users.findById(PENDING_COURIER_ID)).thenReturn(Optional.of(pendingUser));
        String token = courierTokenFor(pendingUser);

        // The token must not carry ROLE_COURIER: courier endpoints reject it.
        mvc.perform(get("/api/v1/courier/parcels")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void approvedCourierTokenKeepsCourierAuthority() throws Exception {
        User approvedUser = withId(new User("a@example.com", passwordEncoder.encode("correct-horse"), "A", Set.of(Role.COURIER)), COURIER_ID);
        when(users.findByEmail("a@example.com")).thenReturn(Optional.of(approvedUser));
        when(users.findById(COURIER_ID)).thenReturn(Optional.of(approvedUser));
        String token = courierTokenFor(approvedUser);

        mvc.perform(get("/api/v1/courier/parcels")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void adminCreatedCourierWithoutApprovalRowKeepsAuthority() throws Exception {
        UUID adminCourierId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        User adminCourier = withId(new User("ac@example.com", passwordEncoder.encode("correct-horse"), "AC", Set.of(Role.COURIER)), adminCourierId);
        when(users.findByEmail("ac@example.com")).thenReturn(Optional.of(adminCourier));
        when(users.findById(adminCourierId)).thenReturn(Optional.of(adminCourier));
        // No approval row stubbed -> findByUserId returns Optional.empty() by Mockito default? No:
        // unstubbed mock returns null for Optional. Stub explicitly.
        when(courierApprovals.findByUserId(adminCourierId)).thenReturn(Optional.empty());
        String token = courierTokenFor(adminCourier);

        mvc.perform(get("/api/v1/courier/parcels")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void courierCanReadOwnApprovalStatus() throws Exception {
        when(courierApprovalService.findByUserId(PENDING_COURIER_ID)).thenReturn(Optional.of(pendingApproval));
        when(courierApprovalService.findUser(PENDING_COURIER_ID)).thenReturn(
                withId(new User("p@example.com", "hash", "P", Set.of(Role.COURIER)), PENDING_COURIER_ID));

        mvc.perform(get("/api/v1/courier-registrations/me")
                        .with(TestPrincipal.courier(PENDING_COURIER_ID).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    private String courierTokenFor(User user) throws Exception {
        // Issue a real token through the login endpoint so AccessTokenIssuer applies approval rules.
        when(users.findByEmailIgnoringCase(any())).thenAnswer(inv -> users.findByEmail(inv.getArgument(0)));
        MvcResult result = mvc.perform(post("/api/v1/auth/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"correct-horse\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }
}
