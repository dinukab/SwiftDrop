package com.swiftdrop.merchant.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import com.swiftdrop.merchant.application.ApiRequestSignature;

/**
 * With signatures required: every store request must be signed, so an unsigned request and a key that
 * has no signing secret are both refused.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id=",
        "swiftdrop.merchant-api.require-signature=true"})
@AutoConfigureMockMvc
class MerchantApiSignatureRequiredApiTest extends MerchantApiSignatureTestSupport {

    @BeforeEach
    void setUp() {
        stubMerchantAndKeys();
        when(parcelQueries.findByMerchantReference(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void anUnsignedRequestIsRefusedWhenSignaturesAreRequired() throws Exception {
        mvc.perform(get(STATUS_PATH).header("X-API-Key", KEY_WITH_SECRET))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aSignedRequestIsAcceptedWhenSignaturesAreRequired() throws Exception {
        String ts = String.valueOf(now());
        mvc.perform(get(STATUS_PATH)
                        .header("X-API-Key", KEY_WITH_SECRET)
                        .header(ApiRequestSignature.TIMESTAMP_HEADER, ts)
                        .header(ApiRequestSignature.SIGNATURE_HEADER,
                                ApiRequestSignature.sign(SIGNING_SECRET, ts, "GET", STATUS_PATH, new byte[0])))
                .andExpect(status().isNotFound());
    }

    @Test
    void aKeyCreatedBeforeSigningIsRefusedEvenWhenItSignsNothing() throws Exception {
        mvc.perform(get(STATUS_PATH).header("X-API-Key", KEY_WITHOUT_SECRET))
                .andExpect(status().isUnauthorized());
    }
}
