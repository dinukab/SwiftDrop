package com.swiftdrop.dispatch.web;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;

/** Reads the caller from the verified token; the subject is the user ID. */
final class Callers {

    private Callers() {
    }

    static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
