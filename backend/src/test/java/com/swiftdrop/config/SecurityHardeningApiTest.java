package com.swiftdrop.config;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.OutboxEventRepository;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security hardening checks: disabled-account JWT revocation, per-request courier approval
 * enforcement, registration/update role-injection resistance, and rate limiting (429).
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id=",
        "swiftdrop.rate-limit.registration-requests-per-minute=3"})
@AutoConfigureMockMvc
class SecurityHardeningApiTest {

    private static final UUID ADMIN_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID COURIER_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

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

    private User admin;
    private User courier;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.when(users.findById(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> {
                    User stubUser = new User("stub@example.com", "hash", "Stub", Set.of(Role.CUSTOMER));
                    org.springframework.test.util.ReflectionTestUtils.setField(stubUser, "id", inv.getArgument(0));
                    return Optional.of(stubUser);
                });

        admin = new User("admin@example.com", passwordEncoder.encode("correct-horse"),
                "Admin", Set.of(Role.ADMIN));
        org.springframework.test.util.ReflectionTestUtils.setField(admin, "id", ADMIN_ID);

        courier = new User("courier@example.com", passwordEncoder.encode("correct-horse"),
                "Courier", Set.of(Role.COURIER));
        org.springframework.test.util.ReflectionTestUtils.setField(courier, "id", COURIER_ID);
    }

    private String loginToken(User user) throws Exception {
        when(users.findByEmailIgnoringCase(user.getEmail())).thenReturn(Optional.of(user));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        MvcResult result = mvc.perform(post("/api/v1/auth/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"correct-horse\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    @Test
    void disabledUserTokenIsRejectedImmediately() throws Exception {
        String token = loginToken(admin);
        admin.setEnabled(false); // admin disables the account after the token was issued

        mvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void activeUserTokenStillWorks() throws Exception {
        String token = loginToken(admin);

        mvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void courierApprovalRevocationStripsAuthorityOnNextRequest() throws Exception {
        // Approved courier logs in and receives a token carrying ROLE_COURIER.
        CourierApproval approved = new CourierApproval(COURIER_ID);
        approved.approve(ADMIN_ID);
        when(courierApprovals.findByUserId(COURIER_ID)).thenReturn(Optional.of(approved));
        String token = loginToken(courier);

        mvc.perform(get("/api/v1/courier/parcels")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        // Approval is revoked; the SAME token must lose courier authority on the next request.
        CourierApproval pending = new CourierApproval(COURIER_ID);
        when(courierApprovals.findByUserId(COURIER_ID)).thenReturn(Optional.of(pending));
        mvc.perform(get("/api/v1/courier/parcels")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void roleInjectionInUserUpdateIsIgnored() throws Exception {
        User target = new User("target@example.com", "hash", "Target", Set.of(Role.CUSTOMER));
        org.springframework.test.util.ReflectionTestUtils.setField(target, "id",
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
        when(users.findById(target.getId())).thenReturn(Optional.of(target));
        when(users.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        mvc.perform(patch("/api/v1/users/" + target.getId())
                        .with(TestPrincipal.admin(ADMIN_ID).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"role\":\"ADMIN\",\"roles\":[\"ADMIN\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.roles.length()").value(1));
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        String token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhYWFhYWFhYS1hYWFhLWFhYWEtYWFhYS1hYWFhYWFhYWFhYSIsInJvbGVzIjpbIkNBUlNPTUVSIl19.tampered";
        mvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registrationEndpointRateLimitReturns429() throws Exception {
        // This context sets registration limit to 3/min per IP. Exceed it with invalid payloads.
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"fullName\":\"\",\"email\":\"bad\",\"password\":\"x\"}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"\",\"email\":\"bad\",\"password\":\"x\"}"))
                .andExpect(status().isTooManyRequests());
    }
}
