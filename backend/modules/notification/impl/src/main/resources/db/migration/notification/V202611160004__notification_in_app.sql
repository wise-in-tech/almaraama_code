-- In-application notifications (K1, K6). Nothing is deleted (M2): a notification is marked as read.
-- source_key makes the event listeners idempotent (UML model, section 3.10).
CREATE TABLE notification.notification
(
    id           UUID PRIMARY KEY,
    recipient_id UUID        NOT NULL,
    template     TEXT        NOT NULL,
    params       TEXT        NOT NULL,
    link         TEXT,
    site_id      UUID,
    source_key   TEXT        NOT NULL UNIQUE,
    created_at   TIMESTAMPTZ NOT NULL,
    read_at      TIMESTAMPTZ,
    version      BIGINT      NOT NULL
);
CREATE INDEX notification_recipient_idx ON notification.notification (recipient_id, created_at DESC);
