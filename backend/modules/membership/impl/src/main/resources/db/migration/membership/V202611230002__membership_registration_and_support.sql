-- Sprint 2, spec v3.4 (ADR-034, ADR-035): registration QR code per site, self-registration with a free comment,
-- pre-registrations with a single-use key, support sessions of the platform administrators, and the default rows of
-- the permissions added by sprint 2 (RB-6). Earlier migrations are left unchanged. Plain text without CHECK constraint
-- (architecture rule). The application role reads, inserts and updates; it never deletes (M2).
--
-- ============================================================================================================
-- FORMAT FOR LOADING BY HAND
-- ============================================================================================================
-- None of these tables is reference data: rows are written by the application only.
-- ============================================================================================================

-- ---- D2: the join code becomes the registration QR code (same rows, same codes) ----
ALTER TABLE membership.join_code RENAME TO registration_qr;
ALTER TABLE membership.registration_qr RENAME CONSTRAINT join_code_pkey TO registration_qr_pkey;
ALTER TABLE membership.registration_qr RENAME CONSTRAINT join_code_code_key TO registration_qr_code_key;

-- ---- D2, D3: a self-registration carries a free comment and is submitted once the e-mail is verified ----
-- comment              free text of the person (never logged); NULL when none.
-- submitted_at         when the request reached the managers (e-mail verified); NULL = not submitted yet.
-- key_hash             SHA-256 (hex) of the pre-registration key the person typed, until it is checked; NULL after.
-- key_outcome          NONE, MATCHED, MATCHED_NEEDS_REVIEW, NOT_MATCHED (glossary KeyOutcome).
-- pre_registration_id  the pre-registration whose key matched.
ALTER TABLE membership.site_membership ADD COLUMN comment TEXT;
ALTER TABLE membership.site_membership ADD COLUMN submitted_at TIMESTAMPTZ;
ALTER TABLE membership.site_membership ADD COLUMN key_hash TEXT;
ALTER TABLE membership.site_membership ADD COLUMN key_outcome TEXT NOT NULL DEFAULT 'NONE';
ALTER TABLE membership.site_membership ADD COLUMN pre_registration_id UUID;
-- Requests recorded before this migration were visible at once: they count as submitted.
UPDATE membership.site_membership SET submitted_at = created_at WHERE origin = 'SELF_REGISTRATION';
-- The declared units are replaced by the free comment (spec v3.4, D2); only development data ever used them.
ALTER TABLE membership.site_membership DROP COLUMN declared_units;
CREATE INDEX site_membership_unsubmitted_idx ON membership.site_membership (person_id)
    WHERE submitted_at IS NULL AND status = 'PENDING';

-- ---- D3: pre-registrations ----
-- planned_occupancies  one line per planned unit: unit_id|OWNER or TENANT|true or false (primary tenant).
-- key_hash             SHA-256 (hex) of the personal key; the key itself is never stored.
-- status               PENDING, USED, CANCELLED (EXPIRED is computed from expires_at, never stored).
-- origin               MANUAL, IMPORT.
CREATE TABLE membership.pre_registration
(
    id                  UUID PRIMARY KEY,
    organization_id     UUID        NOT NULL,
    site_id             UUID        NOT NULL,
    first_name          TEXT        NOT NULL,
    last_name           TEXT        NOT NULL,
    email               TEXT        NOT NULL,
    phone               TEXT        NOT NULL,
    language            TEXT        NOT NULL,
    planned_occupancies TEXT        NOT NULL,
    key_hash            TEXT        NOT NULL UNIQUE,
    expires_at          TIMESTAMPTZ NOT NULL,
    status              TEXT        NOT NULL,
    origin              TEXT        NOT NULL,
    created_by          UUID        NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    used_at             TIMESTAMPTZ,
    used_by_person_id   UUID,
    membership_id       UUID,
    cancelled_by        UUID,
    cancelled_at        TIMESTAMPTZ,
    cancel_reason       TEXT,
    updated_at          TIMESTAMPTZ NOT NULL,
    version             BIGINT      NOT NULL
);
CREATE INDEX pre_registration_site_idx ON membership.pre_registration (site_id, status, created_at);
CREATE INDEX pre_registration_email_idx ON membership.pre_registration (site_id, email) WHERE status = 'PENDING';

-- ---- C12: support sessions of the platform administrators (ADR-035) ----
-- One hour; EXPIRED is computed from expires_at. ended_at is set when the administrator ends it early.
CREATE TABLE membership.support_session
(
    id              UUID PRIMARY KEY,
    person_id       UUID        NOT NULL,
    organization_id UUID        NOT NULL,
    reason          TEXT        NOT NULL,
    started_at      TIMESTAMPTZ NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    ended_at        TIMESTAMPTZ,
    version         BIGINT      NOT NULL
);
CREATE INDEX support_session_person_idx ON membership.support_session (person_id, expires_at) WHERE ended_at IS NULL;
CREATE INDEX support_session_organization_idx ON membership.support_session (organization_id, started_at);

-- ---- RB-6: default rows of the permissions added by sprint 2 (ADR-026, section "Droits ajoutés au sprint 2") ----
INSERT INTO membership.role_default_permission (role_code, permission)
VALUES ('SITE_MANAGER', 'pre-registration:create'),
       ('SITE_MANAGER', 'pre-registration:read'),
       ('SITE_MANAGER', 'pre-registration:cancel'),
       ('PLATFORM_ADMIN', 'organization:read'),
       ('PLATFORM_ADMIN', 'reference-data:read'),
       ('PLATFORM_ADMIN', 'reference-data:create'),
       ('PLATFORM_ADMIN', 'reference-data:update'),
       ('PLATFORM_ADMIN', 'reference-data:archive'),
       ('PLATFORM_ADMIN', 'reference-data:validate-provisional'),
       ('PLATFORM_ADMIN', 'text:read'),
       ('PLATFORM_ADMIN', 'text:update'),
       ('PLATFORM_ADMIN', 'support:access');
