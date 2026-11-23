-- Journal of important actions (M2, security baseline): sign-ins, lockouts, invitations, later permission changes,
-- quote decisions and exports. Append-only: the application role may insert and read, never update or delete.

CREATE TABLE audit.audit_entry
(
    id           UUID PRIMARY KEY,
    occurred_at  TIMESTAMPTZ NOT NULL,
    recorded_at  TIMESTAMPTZ NOT NULL,
    actor_type   TEXT        NOT NULL,
    actor_id     UUID        NOT NULL,
    action       TEXT        NOT NULL,
    subject_type TEXT,
    subject_id   UUID,
    details      JSONB       NOT NULL
);
CREATE INDEX audit_entry_subject_idx ON audit.audit_entry (subject_type, subject_id);
CREATE INDEX audit_entry_occurred_idx ON audit.audit_entry (occurred_at);

REVOKE UPDATE ON audit.audit_entry FROM "${app-role}";
