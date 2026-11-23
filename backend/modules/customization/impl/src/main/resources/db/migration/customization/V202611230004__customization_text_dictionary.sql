-- Sprint 2 (M6, ADR-029): dictionary of the interface texts, edited by the platform administrators without a
-- deployment. Keys come from the developers' locale files and are loaded at start-up by the application (TextSeed);
-- an administrator's text (origin EDITED) is never overwritten by a release. Nothing is deleted (M2): a key that
-- disappears from the files is marked obsolete; every change is a new row of text_revision.
--
-- ============================================================================================================
-- FORMAT FOR LOADING BY HAND
-- ============================================================================================================
-- Do not load these tables by hand: change the locale files (frontend/src/shared/i18n/locales/*.json, copied by
-- pnpm sync:texts) or use the back-office.
-- ============================================================================================================

-- language: AR, EN, FR. origin: SEED (delivered) or EDITED (administrator). seed_text: the delivered text, kept to
-- check the {{variables}} of an edited text and to compare.
CREATE TABLE customization.text_entry
(
    id         UUID PRIMARY KEY,
    text_key   TEXT        NOT NULL,
    language   TEXT        NOT NULL,
    text       TEXT        NOT NULL,
    seed_text  TEXT,
    origin     TEXT        NOT NULL,
    obsolete   BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL,
    version    BIGINT      NOT NULL,
    UNIQUE (text_key, language)
);

-- Append only: one row per change (release or administrator). actor_type: PERSON or SYSTEM.
CREATE TABLE customization.text_revision
(
    id          UUID PRIMARY KEY,
    entry_id    UUID        NOT NULL REFERENCES customization.text_entry (id),
    text_key    TEXT        NOT NULL,
    language    TEXT        NOT NULL,
    old_text    TEXT,
    new_text    TEXT        NOT NULL,
    origin      TEXT        NOT NULL,
    actor_type  TEXT        NOT NULL,
    actor_id    UUID        NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX text_revision_key_idx ON customization.text_revision (text_key, recorded_at DESC);
REVOKE UPDATE ON customization.text_revision FROM "${app-role}";
