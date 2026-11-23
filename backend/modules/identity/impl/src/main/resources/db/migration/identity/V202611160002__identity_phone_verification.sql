-- Sprint 1: SMS codes that prove a phone number at self-registration (D3), and revocation of invitation links when
-- a new one is issued (D2). Only hashes are stored. Nothing is deleted (M2).

ALTER TABLE identity.invitation ADD COLUMN revoked_at TIMESTAMPTZ;

CREATE TABLE identity.phone_verification
(
    id          UUID PRIMARY KEY,
    phone       TEXT        NOT NULL,
    code_hash   TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    attempts    INTEGER     NOT NULL,
    verified_at TIMESTAMPTZ,
    consumed_at TIMESTAMPTZ,
    version     BIGINT      NOT NULL
);
CREATE INDEX phone_verification_phone_idx ON identity.phone_verification (phone, created_at);
