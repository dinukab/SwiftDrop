-- Owner: notification module (ADR 0003, ADR 0004 section 8). The mock SMS channel: each message is stored for the
-- recipient's user account and shown in the application instead of being sent.
--
-- * event_id is unique: the consumer may see an event more than once (redelivery), and one event creates
--   one notification at most.
-- * read_at is null until the recipient reads the message (unread count and bell).
-- * No phone number is stored: the message is addressed to the account, and the text names the tracking number only.

CREATE TABLE notifications (
    id              UUID         NOT NULL,
    user_id         UUID         NOT NULL,
    parcel_id       UUID         NOT NULL,
    tracking_number VARCHAR(20)  NOT NULL,
    event_id        UUID         NOT NULL,
    status          VARCHAR(30)  NOT NULL,
    message         VARCHAR(300) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    read_at         TIMESTAMPTZ,
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_notifications_event UNIQUE (event_id)
);

-- A user's inbox, newest first, and the unread count
CREATE INDEX ix_notifications_user_created ON notifications (user_id, created_at DESC);
CREATE INDEX ix_notifications_user_unread ON notifications (user_id) WHERE read_at IS NULL;
