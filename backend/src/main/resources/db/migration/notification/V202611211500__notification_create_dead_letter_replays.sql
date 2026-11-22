-- Owner: notification module (ADR 0004 section 6). An administrator can move dead-lettered messages back to their
-- source queue. Every replayed message is recorded here: who replayed it, when, which event, and why it had been
-- dead-lettered.
--
-- * event_id is null when the dead letter could not be parsed (a malformed message is still replayed and audited).
-- * original_reason is the broker's dead-letter reason (for example rejected or delivery_limit), not message content.
-- * A message may be replayed more than once (if it fails again), so event_id is not unique.
-- * No message body is stored.

CREATE TABLE notification_dead_letter_replays (
    id              UUID         NOT NULL,
    queue           VARCHAR(100) NOT NULL,
    event_id        UUID,
    original_reason VARCHAR(50),
    replayed_by     UUID         NOT NULL,
    replayed_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_notification_dead_letter_replays PRIMARY KEY (id),
    CONSTRAINT fk_notification_dead_letter_replays_user FOREIGN KEY (replayed_by) REFERENCES users (id)
);

-- The audit list, newest first
CREATE INDEX ix_notification_dead_letter_replays_at ON notification_dead_letter_replays (replayed_at DESC);
