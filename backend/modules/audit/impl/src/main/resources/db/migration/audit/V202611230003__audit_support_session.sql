-- Sprint 2 (C12, ADR-035): an action done by a platform administrator during a support session carries the session.
ALTER TABLE audit.audit_entry ADD COLUMN support_session_id UUID;
CREATE INDEX audit_entry_support_session_idx ON audit.audit_entry (support_session_id)
    WHERE support_session_id IS NOT NULL;
