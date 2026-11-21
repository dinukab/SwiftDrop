package com.swiftdrop.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.swiftdrop.notification.domain.DeadLetterReplay;
import com.swiftdrop.notification.domain.DeadLetterReplayRepository;

class DeadLetterReplayServiceTest {

    private static final String QUEUE = "notification.parcel-status-changed";
    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID EVENT_1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID EVENT_2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");

    private DeadLetterReplayRepository repository;
    private FakeDeadLetterQueue queue;
    private DeadLetterReplayService service;

    @BeforeEach
    void setUp() {
        repository = mock(DeadLetterReplayRepository.class);
        queue = new FakeDeadLetterQueue();
        service = new DeadLetterReplayService(queue, repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void everyReplayedMessageIsAuditedWithWhoWhenAndWhich() {
        queue.add(letter(EVENT_1, "rejected"));
        queue.add(letter(EVENT_2, "delivery_limit"));

        DeadLetterReplayService.Outcome outcome = service.replay(ADMIN, 10);

        assertThat(outcome.replayed()).extracting(DeadLetter::eventId).containsExactly(EVENT_1, EVENT_2);
        assertThat(outcome.stopReason()).isNull();
        assertThat(queue.waiting).isEmpty();
        ArgumentCaptor<DeadLetterReplay> saved = ArgumentCaptor.forClass(DeadLetterReplay.class);
        verify(repository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(DeadLetterReplay::getEventId).containsExactly(EVENT_1, EVENT_2);
        assertThat(saved.getAllValues()).extracting(DeadLetterReplay::getOriginalReason)
                .containsExactly("rejected", "delivery_limit");
        assertThat(saved.getAllValues()).allSatisfy(row -> {
            assertThat(row.getReplayedBy()).isEqualTo(ADMIN);
            assertThat(row.getReplayedAt()).isEqualTo(NOW);
            assertThat(row.getQueue()).isEqualTo(QUEUE);
        });
    }

    @Test
    void onlyTheRequestedNumberOfOldestMessagesMove() {
        queue.add(letter(EVENT_1, "rejected"));
        queue.add(letter(EVENT_2, "rejected"));

        DeadLetterReplayService.Outcome outcome = service.replay(ADMIN, 1);

        assertThat(outcome.replayed()).extracting(DeadLetter::eventId).containsExactly(EVENT_1);
        assertThat(queue.waiting).extracting(DeadLetter::eventId).containsExactly(EVENT_2);
    }

    @Test
    void anUnparseableMessageIsStillReplayedAndAuditedWithoutAnEventId() {
        queue.add(new DeadLetter(null, null, "rejected", 1, NOW));

        service.replay(ADMIN, 5);

        ArgumentCaptor<DeadLetterReplay> saved = ArgumentCaptor.forClass(DeadLetterReplay.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getEventId()).isNull();
    }

    @Test
    void aLimitOutsideTheRangeIsRejectedBeforeAnythingMoves() {
        queue.add(letter(EVENT_1, "rejected"));

        assertThatThrownBy(() -> service.replay(ADMIN, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.replay(ADMIN, DeadLetterReplayService.MAX_REPLAY + 1))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(queue.waiting).hasSize(1);
        verify(repository, never()).save(any());
    }

    @Test
    void ifTheAuditCannotBeSavedTheMessageStaysInTheDeadLetterQueueAndTheReplayStops() {
        queue.add(letter(EVENT_1, "rejected"));
        queue.add(letter(EVENT_2, "rejected"));
        when(repository.save(any())).thenAnswer(invocation -> {
            DeadLetterReplay row = invocation.getArgument(0);
            if (EVENT_2.equals(row.getEventId())) {
                throw new IllegalStateException("database down");
            }
            return row;
        });

        DeadLetterReplayService.Outcome outcome = service.replay(ADMIN, 10);

        assertThat(outcome.replayed()).extracting(DeadLetter::eventId).containsExactly(EVENT_1);
        assertThat(outcome.stopReason()).isEqualTo("AUDIT_FAILED");
        assertThat(queue.waiting).extracting(DeadLetter::eventId).containsExactly(EVENT_2);
    }

    @Test
    void aBrokerFailureIsReportedAsAStopReason() {
        queue.add(letter(EVENT_1, "rejected"));
        queue.failWith = "NOT_CONFIRMED";

        DeadLetterReplayService.Outcome outcome = service.replay(ADMIN, 10);

        assertThat(outcome.replayed()).isEmpty();
        assertThat(outcome.stopReason()).isEqualTo("NOT_CONFIRMED");
        assertThat(queue.waiting).hasSize(1);
        verify(repository, never()).save(any());
    }

    @Test
    void theOverviewShowsTheDepthAndTheOldestMessages() {
        queue.add(letter(EVENT_1, "rejected"));
        queue.add(letter(EVENT_2, "rejected"));

        DeadLetterReplayService.Overview overview = service.overview();

        assertThat(overview.queue()).isEqualTo(QUEUE);
        assertThat(overview.depth()).isEqualTo(2);
        assertThat(overview.oldest()).extracting(DeadLetter::eventId).containsExactly(EVENT_1, EVENT_2);
        assertThat(queue.waiting).hasSize(2); // looking does not remove
    }

    @Test
    void theHistoryIsPagedNewestFirst() {
        DeadLetterReplay row = new DeadLetterReplay(QUEUE, EVENT_1, "rejected", ADMIN, NOW);
        when(repository.findAllByOrderByReplayedAtDescIdDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row)));

        assertThat(service.history(0, 20).getContent()).containsExactly(row);
    }

    private static DeadLetter letter(UUID eventId, String reason) {
        return new DeadLetter(eventId, "DELIVERED", reason, 1, NOW);
    }

    /** Behaves like the broker port: a message leaves only after the audit callback returned. */
    private static final class FakeDeadLetterQueue implements DeadLetterQueue {

        final List<DeadLetter> waiting = new ArrayList<>();
        String failWith;

        void add(DeadLetter letter) {
            waiting.add(letter);
        }

        @Override
        public String sourceQueue() {
            return QUEUE;
        }

        @Override
        public long depth() {
            return waiting.size();
        }

        @Override
        public List<DeadLetter> peek(int max) {
            return List.copyOf(waiting.subList(0, Math.min(max, waiting.size())));
        }

        @Override
        public Result replayOldest(int max, Consumer<DeadLetter> beforeAcknowledge) {
            List<DeadLetter> replayed = new ArrayList<>();
            for (int i = 0; i < max && !waiting.isEmpty(); i++) {
                if (failWith != null) {
                    return new Result(replayed, failWith);
                }
                DeadLetter next = waiting.get(0);
                try {
                    beforeAcknowledge.accept(next);
                } catch (RuntimeException e) {
                    return new Result(replayed, "AUDIT_FAILED");
                }
                waiting.remove(0);
                replayed.add(next);
            }
            return new Result(replayed, null);
        }
    }
}
