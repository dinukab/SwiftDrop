package com.swiftdrop.identity.security;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import com.swiftdrop.identity.api.IdentityClaims;
import com.swiftdrop.identity.domain.CourierApprovalStatus;
import com.swiftdrop.identity.domain.Role;
import com.swiftdrop.identity.domain.User;
import com.swiftdrop.identity.domain.CourierApprovalRepository;

/** Issues signed access tokens. There are no refresh tokens or server-side sessions yet. */
@Component
public class AccessTokenIssuer {

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final CourierApprovalRepository courierApprovals;

    AccessTokenIssuer(JwtEncoder encoder, JwtProperties properties,
            CourierApprovalRepository courierApprovals) {
        this.encoder = encoder;
        this.properties = properties;
        this.courierApprovals = courierApprovals;
    }

    public AccessToken issue(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .claim(IdentityClaims.ROLES, effectiveRoles(user))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(value, properties.accessTokenTtl());
    }

    /**
     * Roles granted by this token. A courier registered publicly keeps the COURIER role only
     * once the approval row is APPROVED; pending and rejected registrations get a token without
     * courier authority. Admin-created couriers have no approval row and keep the role.
     */
    private java.util.List<String> effectiveRoles(User user) {
        Set<String> roles = user.getRoles().stream()
                .map(Enum::name)
                .collect(Collectors.toCollection(TreeSet::new));
        if (roles.contains(Role.COURIER.name())
                && courierApprovals.findByUserId(user.getId())
                        .map(approval -> approval.getStatus() != CourierApprovalStatus.APPROVED)
                        .orElse(false)) {
            roles.remove(Role.COURIER.name());
        }
        return List.copyOf(roles);
    }

    public record AccessToken(String value, Duration lifetime) {

        /** Never includes the token. */
        @Override
        public String toString() {
            return "AccessToken[lifetime=" + lifetime + "]";
        }
    }
}
