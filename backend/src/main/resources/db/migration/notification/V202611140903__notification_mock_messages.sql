CREATE TABLE notification_messages (
    event_id UUID PRIMARY KEY,
    recipient_user_id UUID NOT NULL,
    parcel_id UUID NOT NULL,
    tracking_number VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
