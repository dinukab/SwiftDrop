package com.swiftdrop.dispatch.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.dispatch.domain.DispatcherHub;
import com.swiftdrop.dispatch.domain.DispatcherHubRepository;
import com.swiftdrop.dispatch.domain.Hub;
import com.swiftdrop.dispatch.domain.HubRepository;
import com.swiftdrop.identity.api.UserDirectory;
import com.swiftdrop.identity.api.UserSummary;

class HubServiceTest {

    private static final UUID DISPATCHER = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");

    private HubRepository hubs;
    private DispatcherHubRepository bindings;
    private UserDirectory users;
    private HubService service;

    private Hub uva;
    private Hub colombo;

    @BeforeEach
    void setUp() {
        hubs = mock(HubRepository.class);
        bindings = mock(DispatcherHubRepository.class);
        users = mock(UserDirectory.class);
        service = new HubService(hubs, bindings, users, Clock.fixed(NOW, ZoneOffset.UTC));
        uva = hub("LK-UP", "Uva Province Hub", false, true);
        colombo = hub("LK-WP", "Colombo Main Hub (Western Province)", true, true);
    }

    private static Hub hub(String code, String name, boolean main, boolean active) {
        Hub hub = BeanUtils.instantiateClass(Hub.class);
        ReflectionTestUtils.setField(hub, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(hub, "code", code);
        ReflectionTestUtils.setField(hub, "name", name);
        ReflectionTestUtils.setField(hub, "main", main);
        ReflectionTestUtils.setField(hub, "active", active);
        return hub;
    }

    private void user(boolean enabled, String role) {
        when(users.findUser(DISPATCHER)).thenReturn(Optional.of(
                new UserSummary(DISPATCHER, "Nimal Silva", "nimal@swiftdrop.com", Set.of(role), enabled)));
    }

    @Test
    void listsActiveHubsFromTheRepositoryOrder() {
        when(hubs.findByActiveTrueOrderByMainDescNameAsc()).thenReturn(List.of(colombo, uva));

        assertThat(service.listHubs()).containsExactly(colombo, uva);
    }

    @Test
    void bindsADispatcherToAnActiveHub() {
        user(true, "DISPATCHER");
        when(hubs.findByCode("LK-UP")).thenReturn(Optional.of(uva));
        when(bindings.findById(DISPATCHER)).thenReturn(Optional.empty());

        assertThat(service.assignDispatcher(DISPATCHER, "LK-UP")).isSameAs(uva);

        ArgumentCaptor<DispatcherHub> saved = ArgumentCaptor.forClass(DispatcherHub.class);
        verify(bindings).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(DISPATCHER);
        assertThat(saved.getValue().getHubId()).isEqualTo(uva.getId());
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void hubCodeIsTrimmedAndCaseInsensitive() {
        user(true, "DISPATCHER");
        when(hubs.findByCode("LK-UP")).thenReturn(Optional.of(uva));
        when(bindings.findById(DISPATCHER)).thenReturn(Optional.empty());

        assertThat(service.assignDispatcher(DISPATCHER, " lk-up ")).isSameAs(uva);
    }

    @Test
    void movesAnAlreadyBoundDispatcherToAnotherHub() {
        user(true, "DISPATCHER");
        DispatcherHub existing = new DispatcherHub(DISPATCHER, colombo.getId(), NOW.minusSeconds(3600));
        when(hubs.findByCode("LK-UP")).thenReturn(Optional.of(uva));
        when(bindings.findById(DISPATCHER)).thenReturn(Optional.of(existing));

        service.assignDispatcher(DISPATCHER, "LK-UP");

        assertThat(existing.getHubId()).isEqualTo(uva.getId());
        assertThat(existing.getUpdatedAt()).isEqualTo(NOW);
        verify(bindings, never()).save(any());
    }

    @Test
    void rejectsUnknownUser() {
        when(users.findUser(DISPATCHER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignDispatcher(DISPATCHER, "LK-UP"))
                .isInstanceOf(DispatcherUserNotFoundException.class);
        verify(bindings, never()).save(any());
    }

    @Test
    void rejectsDisabledDispatcher() {
        user(false, "DISPATCHER");

        assertThatThrownBy(() -> service.assignDispatcher(DISPATCHER, "LK-UP"))
                .isInstanceOf(DispatcherNotEligibleException.class)
                .hasMessage("Dispatcher account is disabled");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "COURIER", "CUSTOMER", "MERCHANT"})
    void rejectsUsersWithoutTheDispatcherRole(String role) {
        user(true, role);

        assertThatThrownBy(() -> service.assignDispatcher(DISPATCHER, "LK-UP"))
                .isInstanceOf(DispatcherNotEligibleException.class)
                .hasMessage("User does not have the DISPATCHER role");
        verify(bindings, never()).save(any());
    }

    @Test
    void rejectsUnknownAndInactiveHubs() {
        user(true, "DISPATCHER");
        when(hubs.findByCode("LK-XX")).thenReturn(Optional.empty());
        Hub closed = hub("LK-NP", "Northern Province Hub", false, false);
        when(hubs.findByCode("LK-NP")).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> service.assignDispatcher(DISPATCHER, "LK-XX"))
                .isInstanceOf(HubNotFoundException.class);
        assertThatThrownBy(() -> service.assignDispatcher(DISPATCHER, "LK-NP"))
                .isInstanceOf(HubNotFoundException.class);
        verify(bindings, never()).save(any());
    }

    @Test
    void findsTheHubOfABoundDispatcher() {
        when(bindings.findById(DISPATCHER)).thenReturn(Optional.of(new DispatcherHub(DISPATCHER, uva.getId(), NOW)));
        when(hubs.findById(uva.getId())).thenReturn(Optional.of(uva));

        assertThat(service.hubForDispatcher(DISPATCHER)).contains(uva);
    }

    @Test
    void anUnboundDispatcherOrADeactivatedHubMeansNoHub() {
        when(bindings.findById(DISPATCHER)).thenReturn(Optional.empty());
        assertThat(service.hubForDispatcher(DISPATCHER)).isEmpty();

        Hub closed = hub("LK-NP", "Northern Province Hub", false, false);
        when(bindings.findById(DISPATCHER)).thenReturn(Optional.of(new DispatcherHub(DISPATCHER, closed.getId(), NOW)));
        when(hubs.findById(closed.getId())).thenReturn(Optional.of(closed));
        assertThat(service.hubForDispatcher(DISPATCHER)).isEmpty();
    }

    @Test
    void listsOnlyDispatchersWhoseHubIsActive() {
        UUID other = UUID.fromString("00000000-0000-0000-0000-0000000000e2");
        Hub closed = hub("LK-NP", "Northern Province Hub", false, false);
        when(hubs.findAll()).thenReturn(List.of(uva, colombo, closed));
        when(bindings.findAll()).thenReturn(List.of(
                new DispatcherHub(DISPATCHER, uva.getId(), NOW),
                new DispatcherHub(other, closed.getId(), NOW),
                new DispatcherHub(UUID.randomUUID(), UUID.randomUUID(), NOW)));

        assertThat(service.listDispatcherHubs()).containsOnlyKeys(DISPATCHER).containsEntry(DISPATCHER, uva);
    }
}
