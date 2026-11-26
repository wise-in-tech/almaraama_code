-- Sprint 3 (G3, ADR-039, ADR-012): characteristics of equipment families, defined as data.
-- owner_scope: PLATFORM (defaults kept by the administrators, M7) or ORGANIZATION (an organization's own additions,
-- organization_id set). target_type: ASSET in P1. family_id: the equipment family (assets module, no FK, CF-3).
-- Values are stored by the assets module, as JSON text keyed by code.
--
-- Format for loading by hand (decision of 20 November): one row per characteristic, code in lowerCamelCase
-- ('capacityKg'), data_type in TEXT, INTEGER, DECIMAL, DATE, BOOLEAN, LIST; for a LIST, its choices in
-- customization.attribute_option. id, created_at, updated_at, version have defaults.
CREATE TABLE customization.attribute_definition
(
    id              UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    owner_scope     TEXT        NOT NULL DEFAULT 'PLATFORM',
    organization_id UUID,
    target_type     TEXT        NOT NULL DEFAULT 'ASSET',
    family_id       UUID        NOT NULL,
    code            TEXT        NOT NULL,
    data_type       TEXT        NOT NULL,
    required        BOOLEAN     NOT NULL DEFAULT FALSE,
    label_ar        TEXT,
    label_en        TEXT,
    label_fr        TEXT,
    unit            TEXT,
    sort_order      INTEGER     NOT NULL DEFAULT 100,
    active          BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX attribute_definition_code_uq
    ON customization.attribute_definition (target_type, family_id, organization_id, code) NULLS NOT DISTINCT;

-- Choices of a LIST characteristic. Never deleted: archived (active = false) once used (M2).
CREATE TABLE customization.attribute_option
(
    id            UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    definition_id UUID        NOT NULL REFERENCES customization.attribute_definition (id),
    code          TEXT        NOT NULL,
    label_ar      TEXT,
    label_en      TEXT,
    label_fr      TEXT,
    sort_order    INTEGER     NOT NULL DEFAULT 100,
    active        BOOLEAN     NOT NULL DEFAULT TRUE,
    version       BIGINT      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX attribute_option_code_uq ON customization.attribute_option (definition_id, code);
