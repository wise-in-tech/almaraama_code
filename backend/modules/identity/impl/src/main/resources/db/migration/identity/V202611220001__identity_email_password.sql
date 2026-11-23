-- Sprint 1, spec v3.4 (ADR-030): sign-in by e-mail and password; no PIN and no SMS any more.
--   * The e-mail becomes the identifier: mandatory, unique (stored trimmed and in lower case), verified by a link (D1).
--   * The phone stays mandatory (E.164, ADR-028) but is no longer unique: it is not an identifier any more.
--   * One table of account links (set my password, e-mail verification, password reset) replaces the invitations.
--   * Sent e-mails are traced without their address, for the sending limits and for support (ADR-031).
-- Dropped objects (phone_verification, pin_hash, invitation) only ever held development data: no production data
-- exists before the pilot, so nothing of business value is lost (M2). Earlier migrations are left unchanged.

-- ---- person ----
-- Development accounts without e-mail get a placeholder address that can never receive mail (.invalid, RFC 2606):
-- they must be invited again with a real address.
UPDATE identity.person SET email = 'no-email-' || id || '@migrated.invalid' WHERE email IS NULL OR btrim(email) = '';
UPDATE identity.person SET email = lower(btrim(email));
ALTER TABLE identity.person ALTER COLUMN email SET NOT NULL;
CREATE UNIQUE INDEX person_email_uq ON identity.person (email);
ALTER TABLE identity.person DROP CONSTRAINT person_phone_key;
CREATE INDEX person_phone_idx ON identity.person (phone);
ALTER TABLE identity.person ADD COLUMN email_verified_at TIMESTAMPTZ;
-- Accounts already active had proved their phone; their e-mail is not proved yet.

-- ---- credential: the Argon2id hash of the password replaces the PIN hash ----
-- An existing PIN credential cannot become a password: password_hash stays empty until the person sets a password
-- through "forgot password" (development data only).
ALTER TABLE identity.credential DROP COLUMN pin_hash;
ALTER TABLE identity.credential ADD COLUMN password_hash TEXT;

-- ---- account links (ADR-030): only the SHA-256 hash of the secret is stored ----
-- purpose: SET_PASSWORD (7 days, D4), EMAIL_VERIFICATION (48 hours, D1), PASSWORD_RESET (1 hour, D5).
-- Plain text, no CHECK constraint (architecture rule).
CREATE TABLE identity.account_token
(
    id             UUID PRIMARY KEY,
    person_id      UUID        NOT NULL REFERENCES identity.person (id),
    purpose        TEXT        NOT NULL,
    token_hash     TEXT        NOT NULL UNIQUE,
    issued_by_type TEXT        NOT NULL,
    issued_by_id   UUID        NOT NULL,
    issued_at      TIMESTAMPTZ NOT NULL,
    expires_at     TIMESTAMPTZ NOT NULL,
    used_at        TIMESTAMPTZ,
    revoked_at     TIMESTAMPTZ,
    version        BIGINT      NOT NULL
);
CREATE INDEX account_token_person_idx ON identity.account_token (person_id, purpose) WHERE used_at IS NULL AND revoked_at IS NULL;

-- Former invitation links keep working as "set my password" links.
INSERT INTO identity.account_token (id, person_id, purpose, token_hash, issued_by_type, issued_by_id, issued_at,
                                    expires_at, used_at, revoked_at, version)
SELECT id, person_id, 'SET_PASSWORD', token_hash, issued_by_type, issued_by_id, issued_at, expires_at, used_at,
       revoked_at, version
FROM identity.invitation;
DROP TABLE identity.invitation;

-- ---- traced e-mails (ADR-031): no address, no content, no link ----
-- kind: SET_PASSWORD, EMAIL_VERIFICATION, PASSWORD_RESET, PASSWORD_CHANGED. outcome: SENT, FAILED, THROTTLED.
CREATE TABLE identity.email_dispatch
(
    id         UUID PRIMARY KEY,
    person_id  UUID        NOT NULL REFERENCES identity.person (id),
    kind       TEXT        NOT NULL,
    outcome    TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX email_dispatch_person_idx ON identity.email_dispatch (person_id, kind, created_at);

-- ---- SMS verification of self-registration is abandoned (spec v3.4, D5) ----
DROP TABLE identity.phone_verification;

-- New tables receive SELECT, INSERT, UPDATE through the default privileges of the schema; never DELETE (M2).
