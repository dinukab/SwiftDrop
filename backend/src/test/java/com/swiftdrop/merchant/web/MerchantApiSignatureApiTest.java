package com.swiftdrop.merchant.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.swiftdrop.merchant.application.ApiRequestSignature;

/**
 * Store requests with signatures optional (Phase 5, the migration period): a signed request is checked,
 * an unsigned one passes, and a key without a secret cannot send a signature it cannot be checked against.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id=",
        "swiftdrop.merchant-api.require-signature=false"})
@AutoConfigureMockMvc
class MerchantApiSignatureApiTest extends MerchantApiSignatureTestSupport {

    @BeforeEach
    void setUp() {
        stubMerchantAndKeys();
        // An authenticated, unknown order: the request got past the key and the signature checks
        when(parcelQueries.findByMerchantReference(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void aValidSignatureIsAccepted() throws Exception {
        String ts = String.valueOf(now());
        mvc.perform(signed(get(STATUS_PATH), KEY_WITH_SECRET, ts,
                ApiRequestSignature.sign(SIGNING_SECRET, ts, "GET", STATUS_PATH, new byte[0])))
                .andExpect(status().isNotFound());
    }

    @Test
    void anUnsignedRequestIsStillAcceptedWhileSignaturesAreOptional() throws Exception {
        mvc.perform(get(STATUS_PATH).header("X-API-Key", KEY_WITH_SECRET))
                .andExpect(status().isNotFound());
    }

    @Test
    void aWrongSignatureIsRefused() throws Exception {
        String ts = String.valueOf(now());
        mvc.perform(signed(get(STATUS_PATH), KEY_WITH_SECRET, ts, "v1=" + "0".repeat(64)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(parcelQueries);
    }

    @Test
    void aSignatureMadeWithAnotherSecretIsRefused() throws Exception {
        String ts = String.valueOf(now());
        mvc.perform(signed(get(STATUS_PATH), KEY_WITH_SECRET, ts,
                ApiRequestSignature.sign("some-other-secret-1234", ts, "GET", STATUS_PATH, new byte[0])))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aStaleSignatureIsRefused() throws Exception {
        String ts = String.valueOf(now() - 600);
        mvc.perform(signed(get(STATUS_PATH), KEY_WITH_SECRET, ts,
                ApiRequestSignature.sign(SIGNING_SECRET, ts, "GET", STATUS_PATH, new byte[0])))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aSignatureDoesNotCoverADifferentBody() throws Exception {
        byte[] signedBody = "{\"merchantOrderReference\":\"REF-1\"}".getBytes(StandardCharsets.UTF_8);
        byte[] sentBody = "{\"merchantOrderReference\":\"REF-2\"}".getBytes(StandardCharsets.UTF_8);
        String ts = String.valueOf(now());
        mvc.perform(signed(post(CREATE_PATH).contentType(MediaType.APPLICATION_JSON).content(sentBody),
                KEY_WITH_SECRET, ts, ApiRequestSignature.sign(SIGNING_SECRET, ts, "POST", CREATE_PATH, signedBody)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aKeyWithoutASecretCannotSendASignature() throws Exception {
        String ts = String.valueOf(now());
        mvc.perform(signed(get(STATUS_PATH), KEY_WITHOUT_SECRET, ts,
                ApiRequestSignature.sign(SIGNING_SECRET, ts, "GET", STATUS_PATH, new byte[0])))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aKeyWithoutASecretStillWorksUnsignedWhileSignaturesAreOptional() throws Exception {
        mvc.perform(get(STATUS_PATH).header("X-API-Key", KEY_WITHOUT_SECRET))
                .andExpect(status().isNotFound());
    }

    private static MockHttpServletRequestBuilder signed(MockHttpServletRequestBuilder request, String key, String ts,
            String signature) {
        return request.header("X-API-Key", key)
                .header(ApiRequestSignature.TIMESTAMP_HEADER, ts)
                .header(ApiRequestSignature.SIGNATURE_HEADER, signature);
    }
}
