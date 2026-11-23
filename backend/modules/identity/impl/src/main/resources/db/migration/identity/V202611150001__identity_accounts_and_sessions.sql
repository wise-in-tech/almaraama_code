-- Identity: accounts, PIN credentials, invitations, devices and refresh tokens (D1, D2, D4, D6).
-- Statuses are plain text, without CHECK constraint (architecture rule). Nothing is ever deleted (M2).
-- Foreign keys stay inside the identity schema (CF-3).

CREATE TABLE identity.person
(
    id         UUID PRIMARY KEY,
    first_name TEXT        NOT NULL,
    last_name  TEXT        NOT NULL,
    phone      TEXT        NOT NULL UNIQUE,
    email      TEXT,
    language   TEXT        NOT NULL,
    status     TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version    BIGINT      NOT NULL
);

-- The PIN is stored only as an Argon2id hash. Lockout state lives with it.
CREATE TABLE identity.credential
(
    person_id       UUID PRIMARY KEY REFERENCES identity.person (id),
    pin_hash        TEXT        NOT NULL,
    failed_attempts INTEGER     NOT NULL,
    locked_until    TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ NOT NULL,
    version         BIGINT      NOT NULL
);

-- Only the SHA-256 hash of the invitation secret is stored.
CREATE TABLE identity.invitation
(
    id             UUID PRIMARY KEY,
    person_id      UUID        NOT NULL REFERENCES identity.person (id),
    token_hash     TEXT        NOT NULL UNIQUE,
    issued_by_type TEXT        NOT NULL,
    issued_by_id   UUID        NOT NULL,
    issued_at      TIMESTAMPTZ NOT NULL,
    expires_at     TIMESTAMPTZ NOT NULL,
    used_at        TIMESTAMPTZ,
    version        BIGINT      NOT NULL
);
CREATE INDEX invitation_person_idx ON identity.invitation (person_id);

CREATE TABLE identity.trusted_device
(
    id           UUID PRIMARY KEY,
    person_id    UUID        NOT NULL REFERENCES identity.person (id),
    label        TEXT,
    created_at   TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    revoked_at   TIMESTAMPTZ,
    version      BIGINT      NOT NULL
);
CREATE INDEX trusted_device_person_idx ON identity.trusted_device (person_id);

-- Only the SHA-256 hash of the refresh token is stored. A used token stays, to detect its reuse.
CREATE TABLE identity.refresh_token
(
    id         UUID PRIMARY KEY,
    device_id  UUID        NOT NULL REFERENCES identity.trusted_device (id),
    token_hash TEXT        NOT NULL UNIQUE,
    issued_at  TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    version    BIGINT      NOT NULL
);
CREATE INDEX refresh_token_device_idx ON identity.refresh_token (device_id);
