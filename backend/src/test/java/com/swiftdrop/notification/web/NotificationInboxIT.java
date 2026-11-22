package com.swiftdrop.notification.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.swiftdrop.notification.application.NotificationService;
import com.swiftdrop.test.security.TestPrincipal;

/**
 * The customer notification inbox on real PostgreSQL: the migration, storing one message per event, the inbox, the
 * unread count, reading, and that nobody can see or change another user's messages.
 */
@SpringBootTest(properties = {
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id=",
        "swiftdrop.identity.bootstrap.admin-email=",
        "swiftdrop.identity.bootstrap.admin-password="
})
@AutoConfigureMockMvc
@Testcontainers
class NotificationInboxIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private NotificationService notifications;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID alice;
    private UUID bob;

    @BeforeEach
    void setUp() {
        alice = user("alice");
        bob = user("bob");
    }

    private void event(UUID owner, String status, String tracking) {
        notifications.record(UUID.randomUUID(), UUID.randomUUID(), tracking, status, "CUSTOMER", owner);
    }

    @Test
    void aCustomersInboxHoldsOnlyTheirOwnMessagesNewestFirstWithAnUnreadCount() throws Exception {
        event(alice, "PICKED_UP", "SDAAAAAAAAAA");
        event(alice, "DELIVERED", "SDAAAAAAAAAA");
        event(bob, "DELIVERED", "SDBBBBBBBBBB");

        mvc.perform(get("/api/v1/notifications").with(TestPrincipal.customer(alice).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].status").value("DELIVERED"))
                .andExpect(jsonPath("$.items[1].status").value("PICKED_UP"))
                .andExpect(jsonPath("$.items[0].message").value("Your parcel SDAAAAAAAAAA was delivered."))
                .andExpect(jsonPath("$.items[0].read").value(false));
        mvc.perform(get("/api/v1/notifications/unread-count").with(TestPrincipal.customer(alice).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));
        mvc.perform(get("/api/v1/notifications/unread-count").with(TestPrincipal.customer(bob).postProcessor()))
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void aMessageIsReadOnlyByItsOwnerAndOtherUsersGetANotFound() throws Exception {
        event(alice, "DELIVERED", "SDCCCCCCCCCC");
        UUID id = jdbc.queryForObject("SELECT id FROM notifications WHERE user_id = ?", UUID.class, alice);

        mvc.perform(patch("/api/v1/notifications/" + id + "/read").with(TestPrincipal.customer(bob).postProcessor()))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT read_at IS NULL FROM notifications WHERE id = ?", Boolean.class, id))
                .isTrue();

        mvc.perform(patch("/api/v1/notifications/" + id + "/read").with(TestPrincipal.customer(alice).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
        mvc.perform(get("/api/v1/notifications/unread-count").with(TestPrincipal.customer(alice).postProcessor()))
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void readAllMarksOnlyTheCallersMessages() throws Exception {
        event(alice, "PICKED_UP", "SDDDDDDDDDDD");
        event(alice, "OUT_FOR_DELIVERY", "SDDDDDDDDDDD");
        event(bob, "PICKED_UP", "SDEEEEEEEEEE");

        mvc.perform(post("/api/v1/notifications/read-all").with(TestPrincipal.customer(alice).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(2));

        mvc.perform(get("/api/v1/notifications/unread-count").with(TestPrincipal.customer(alice).postProcessor()))
                .andExpect(jsonPath("$.count").value(0));
        mvc.perform(get("/api/v1/notifications/unread-count").with(TestPrincipal.customer(bob).postProcessor()))
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void oneEventStoresAtMostOneMessageAndQuietEventsStoreNone() {
        UUID eventId = UUID.randomUUID();
        UUID parcel = UUID.randomUUID();

        assertThat(notifications.record(eventId, parcel, "SDFFFFFFFFFF", "DELIVERED", "CUSTOMER", alice)).isTrue();
        assertThat(notifications.record(eventId, parcel, "SDFFFFFFFFFF", "DELIVERED", "CUSTOMER", alice)).isFalse();
        assertThat(notifications.record(UUID.randomUUID(), parcel, "SDFFFFFFFFFF", "IN_TRANSIT", "CUSTOMER", alice))
                .isFalse();
        assertThat(notifications.record(UUID.randomUUID(), parcel, "SDFFFFFFFFFF", "DELIVERED", "MERCHANT", null))
                .isFalse();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE parcel_id = ?", Integer.class,
                parcel)).isEqualTo(1);
    }

    @Test
    void onlyCustomersCanUseTheInboxAndAnonymousCallersAreRefused() throws Exception {
        for (TestPrincipal other : List.of(TestPrincipal.admin(), TestPrincipal.courier(),
                TestPrincipal.dispatcher(), TestPrincipal.merchant())) {
            mvc.perform(get("/api/v1/notifications").with(other.postProcessor())).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/notifications/unread-count").with(other.postProcessor()))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/notifications/read-all").with(other.postProcessor()))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
    }

    private UUID user(String prefix) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, full_name, enabled, created_at) VALUES (?, ?, ?, TRUE, ?)",
                id, prefix + "-" + id + "@example.com", "Test " + prefix, Timestamp.from(Instant.now()));
        jdbc.update("INSERT INTO user_roles (user_id, role_name) VALUES (?, 'CUSTOMER')", id);
        return id;
    }
}
