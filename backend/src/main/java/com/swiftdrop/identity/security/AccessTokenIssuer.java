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
import com.swiftdrop.identity.domain.User;

/** Issues signed access tokens. There are no refresh tokens or server-side sessions yet. */
@Component
public class AccessTokenIssuer {

    private final JwtEncoder encoder;
    private final JwtProperties properties;

    AccessTokenIssuer(JwtEncoder encoder, JwtProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    public AccessToken issue(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .claim(IdentityClaims.ROLES, roles(user))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(value, properties.accessTokenTtl());
    }

    private static List<String> roles(User user) {
        Set<String> roles = user.getRoles().stream()
                .map(Enum::name)
                .collect(Collectors.toCollection(TreeSet::new));
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
