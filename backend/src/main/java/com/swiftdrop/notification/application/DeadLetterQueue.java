package com.swiftdrop.notification.application;

import java.util.List;
import java.util.function.Consumer;

/**
 * The dead-letter queue of the notification consumer, as the replay service sees it (ADR 0004 section 6). The broker
 * implementation is in {@code infrastructure.messaging}; this port keeps the replay rules testable without a broker.
 */
public interface DeadLetterQueue {

    /** The queue that dead letters are moved back to. */
    String sourceQueue();

    /** How many messages wait in the dead-letter queue. */
    long depth();

    /** Looks at up to {@code max} of the oldest messages without removing them. */
    List<DeadLetter> peek(int max);

    /**
     * Moves up to {@code max} of the oldest messages back to {@link #sourceQueue()}, one at a time. A message leaves
     * the dead-letter queue only after the broker confirmed its republication <em>and</em>
     * {@code beforeAcknowledge} returned. If either fails, that message stays in the dead-letter queue and the
     * replay stops; the messages replayed before it stay replayed.
     *
     * @param beforeAcknowledge called once per message after its confirmed republication, for the audit record
     */
    Result replayOldest(int max, Consumer<DeadLetter> beforeAcknowledge);

    /**
     * @param replayed   the messages moved back, in order
     * @param stopReason {@code null} if the replay ended normally (the limit or an empty queue), otherwise a short
     *                   category such as {@code NOT_CONFIRMED}, {@code NOT_ROUTED}, {@code AUDIT_FAILED} or
     *                   {@code BROKER_UNAVAILABLE}; never the broker's own text
     */
    record Result(List<DeadLetter> replayed, String stopReason) {
    }
}
