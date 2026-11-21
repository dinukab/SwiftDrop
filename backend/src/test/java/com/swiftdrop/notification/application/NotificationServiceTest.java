package com.swiftdrop.notification.application;

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
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.swiftdrop.notification.domain.Notification;
import com.swiftdrop.notification.domain.NotificationRepository;

class NotificationServiceTest {

    private static final UUID EVENT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PARCEL = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CUSTOMER = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");

    private NotificationRepository repository;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepository.class);
        service = new NotificationService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void aQualifyingEventStoresOneMessageForTheCustomer() {
        when(repository.existsByEventId(EVENT)).thenReturn(false);

        boolean stored = service.record(EVENT, PARCEL, "SD7K3M9Q2XWB", "DELIVERED", "CUSTOMER", CUSTOMER);

        assertThat(stored).isTrue();
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(CUSTOMER);
        assertThat(saved.getValue().getEventId()).isEqualTo(EVENT);
        assertThat(saved.getValue().getMessage()).isEqualTo("Your parcel SD7K3M9Q2XWB was delivered.");
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getValue().isRead()).isFalse();
    }

    @Test
    void aRedeliveredEventCreatesNothingNew() {
        when(repository.existsByEventId(EVENT)).thenReturn(true);

        assertThat(service.record(EVENT, PARCEL, "SD7K3M9Q2XWB", "DELIVERED", "CUSTOMER", CUSTOMER)).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void eventsThatDoNotQualifyAreIgnoredWithoutTouchingTheStore() {
        assertThat(service.record(EVENT, PARCEL, "SD7K3M9Q2XWB", "IN_TRANSIT", "CUSTOMER", CUSTOMER)).isFalse();
        assertThat(service.record(EVENT, PARCEL, "SD7K3M9Q2XWB", "DELIVERED", "MERCHANT", null)).isFalse();

        verify(repository, never()).save(any());
        verify(repository, never()).existsByEventId(any());
    }

    @Test
    void aUserReadsOnlyTheirOwnNotificationAndReadingAgainKeepsTheFirstTime() {
        Notification own = new Notification(CUSTOMER, PARCEL, "SD7K3M9Q2XWB", EVENT, "DELIVERED", "msg", NOW.minusSeconds(60));
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndUserId(id, CUSTOMER)).thenReturn(Optional.of(own));
        when(repository.findByIdAndUserId(id, OTHER)).thenReturn(Optional.empty());

        Notification read = service.markRead(CUSTOMER, id);
        assertThat(read.getReadAt()).isEqualTo(NOW);

        Notification again = new NotificationService(repository, Clock.fixed(NOW.plusSeconds(500), ZoneOffset.UTC))
                .markRead(CUSTOMER, id);
        assertThat(again.getReadAt()).isEqualTo(NOW);

        // Someone else's notification looks the same as a missing one
        assertThatThrownBy(() -> service.markRead(OTHER, id)).isInstanceOf(NotificationNotFoundException.class);
    }

    @Test
    void readAllAndTheUnreadCountAreScopedToTheUser() {
        when(repository.markAllRead(CUSTOMER, NOW)).thenReturn(3);
        when(repository.countByUserIdAndReadAtIsNull(CUSTOMER)).thenReturn(2L);

        assertThat(service.markAllRead(CUSTOMER)).isEqualTo(3);
        assertThat(service.unreadCount(CUSTOMER)).isEqualTo(2);
        verify(repository, never()).markAllRead(org.mockito.ArgumentMatchers.eq(OTHER), any());
    }
}
