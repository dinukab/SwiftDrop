package com.swiftdrop.merchant.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

/**
 * The store API has its own request limit, counted for every method (not only POST), so a flood of
 * status reads cannot take the service down. Limit set to 2 here to keep the test short.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id=",
        "swiftdrop.rate-limit.merchant-api-requests-per-minute=2"})
@AutoConfigureMockMvc
class MerchantApiRateLimitApiTest extends MerchantApiSignatureTestSupport {

    @Test
    void theStoreApiIsLimitedPerClientForEveryMethod() throws Exception {
        // Refused by the key check (401) but still counted: the limit runs first
        mvc.perform(get(STATUS_PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(STATUS_PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(STATUS_PATH)).andExpect(status().isTooManyRequests());
    }
}
