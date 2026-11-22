package com.swiftdrop.notification.application;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.swiftdrop.notification.domain.DeadLetterReplay;
import com.swiftdrop.notification.domain.DeadLetterReplayRepository;

/**
 * Admin recovery for dead-lettered notification events (ADR 0004 section 6): shows what is waiting in the dead-letter
 * queue and moves messages back to the source queue. Every replayed message is audited (who, when, which event)
 * before it leaves the dead-letter queue, so a message is never replayed without a record. Replaying is safe to
 * repeat: the consumer ignores an event it has already stored.
 */
@Service
@Lazy
public class DeadLetterReplayService {

    /** The most messages one replay request may move. */
    public static final int MAX_REPLAY = 50;

    /** How many messages the overview shows. */
    public static final int PREVIEW_LIMIT = 20;

    private static final Logger log = LoggerFactory.getLogger(DeadLetterReplayService.class);

    private final DeadLetterQueue deadLetters;
    private final DeadLetterReplayRepository replays;
    private final Clock clock;

    @Autowired
    public DeadLetterReplayService(DeadLetterQueue deadLetters, DeadLetterReplayRepository replays) {
        this(deadLetters, replays, Clock.systemUTC());
    }

    DeadLetterReplayService(DeadLetterQueue deadLetters, DeadLetterReplayRepository replays, Clock clock) {
        this.deadLetters = Objects.requireNonNull(deadLetters, "deadLetters");
        this.replays = Objects.requireNonNull(replays, "replays");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * The queue depth and the oldest messages (without bodies). The broker counts only messages that are ready, so a
     * message that another look is holding for a moment is missed; the depth is therefore never lower than the number
     * of messages listed.
     */
    public Overview overview() {
        long depth = deadLetters.depth();
        List<DeadLetter> oldest = deadLetters.peek(PREVIEW_LIMIT);
        return new Overview(deadLetters.sourceQueue(), Math.max(depth, oldest.size()), oldest);
    }

    /**
     * Moves up to {@code limit} of the oldest dead letters back to the source queue and audits each one.
     *
     * @throws IllegalArgumentException if {@code limit} is not between 1 and {@link #MAX_REPLAY}
     */
    public Outcome replay(UUID adminUserId, int limit) {
        Objects.requireNonNull(adminUserId, "adminUserId");
        if (limit < 1 || limit > MAX_REPLAY) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_REPLAY);
        }
        String queue = deadLetters.sourceQueue();
        // Each audit row is saved (and committed) before its message leaves the dead-letter queue
        DeadLetterQueue.Result result = deadLetters.replayOldest(limit, letter -> replays.save(
                new DeadLetterReplay(queue, letter.eventId(), letter.reason(), adminUserId, clock.instant())));
        // IDs and counts only: never message content
        log.info("Dead-letter replay by user {}: {} message(s) moved back to {}{}", adminUserId,
                result.replayed().size(), queue, result.stopReason() == null ? "" : " (stopped: " + result.stopReason() + ")");
        return new Outcome(result.replayed(), result.stopReason());
    }

    /** The audit trail, newest first. */
    public Page<DeadLetterReplay> history(int page, int size) {
        return replays.findAllByOrderByReplayedAtDescIdDesc(PageRequest.of(page, size));
    }

    /** What is waiting in the dead-letter queue. */
    public record Overview(String queue, long depth, List<DeadLetter> oldest) {
    }

    /** The result of one replay request; {@code stopReason} is {@code null} if it ended normally. */
    public record Outcome(List<DeadLetter> replayed, String stopReason) {
    }
}
