package com.swiftdrop.identity.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import com.swiftdrop.identity.api.IdentityClaims;
import com.swiftdrop.identity.domain.CourierApproval;
import com.swiftdrop.identity.domain.CourierApprovalRepository;
import com.swiftdrop.identity.domain.CourierApprovalStatus;
import com.swiftdrop.identity.domain.Role;
import com.swiftdrop.identity.domain.User;

/** Token issuance rules: courier authority is granted only for approved (or admin-created) couriers. */
class AccessTokenIssuerTest {

    private static final UUID ADMIN_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID USER_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    private final CourierApprovalRepository approvals = mock(CourierApprovalRepository.class);
    private final JwtEncoder encoder = mock(JwtEncoder.class);
    private final AccessTokenIssuer issuer = new AccessTokenIssuer(encoder,
            new JwtProperties("test-secret-key-that-is-long-enough-0123456789", "swiftdrop", Duration.ofMinutes(15)),
            approvals);

    @Test
    void pendingCourierTokenOmitsCourierRole() {
        when(approvals.findByUserId(USER_ID)).thenReturn(Optional.of(pending()));

        assertThat(rolesOf(courierUser())).doesNotContain("COURIER");
    }

    @Test
    void rejectedCourierTokenOmitsCourierRole() {
        CourierApproval rejected = new CourierApproval(USER_ID);
        rejected.reject(ADMIN_ID, "bad docs");
        when(approvals.findByUserId(USER_ID)).thenReturn(Optional.of(rejected));

        assertThat(rolesOf(courierUser())).doesNotContain("COURIER");
    }

    @Test
    void approvedCourierTokenKeepsCourierRole() {
        CourierApproval approved = new CourierApproval(USER_ID);
        approved.approve(ADMIN_ID);
        when(approvals.findByUserId(USER_ID)).thenReturn(Optional.of(approved));

        assertThat(rolesOf(courierUser())).contains("COURIER");
    }

    @Test
    void adminCreatedCourierWithoutApprovalRowKeepsCourierRole() {
        when(approvals.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThat(rolesOf(courierUser())).contains("COURIER");
    }

    @Test
    void nonCourierRolesAreUnaffected() {
        User customer = new User("c@example.com", "hash", "C", Set.of(Role.CUSTOMER));
        org.springframework.test.util.ReflectionTestUtils.setField(customer, "id", USER_ID);
        when(approvals.findByUserId(USER_ID)).thenReturn(Optional.of(pending()));

        assertThat(rolesOf(customer)).contains("CUSTOMER");
    }

    private CourierApproval pending() {
        return new CourierApproval(USER_ID);
    }

    private User courierUser() {
        User user = new User("k@example.com", "hash", "K", Set.of(Role.COURIER));
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }

    private Set<String> rolesOf(User user) {
        when(encoder.encode(any())).thenAnswer(inv -> {
            JwtEncoderParameters params = inv.getArgument(0);
            org.springframework.security.oauth2.jwt.Jwt jwt =
                    mock(org.springframework.security.oauth2.jwt.Jwt.class);
            when(jwt.getTokenValue()).thenReturn("sig");
            return jwt;
        });
        issuer.issue(user);
        ArgumentCaptor<JwtEncoderParameters> captor = ArgumentCaptor.forClass(JwtEncoderParameters.class);
        org.mockito.Mockito.verify(encoder).encode(captor.capture());
        JwtClaimsSet claims = captor.getValue().getClaims();
        @SuppressWarnings("unchecked")
        java.util.List<String> roles = (java.util.List<String>) claims.getClaim(IdentityClaims.ROLES);
        return Set.copyOf(roles);
    }
}
