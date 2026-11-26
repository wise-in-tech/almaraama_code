-- Sprint 3 (G1 to G4, ADR-039): equipment families and models, suppliers, equipment with documents and warranties.
-- Nothing is deleted (M2): families, models, suppliers and warranties are archived (active = false), equipment and
-- documents get archived_at. References to other modules (organization, site, building, outdoor area, file) are
-- identifiers only, without foreign key (CF-3).
--
-- Format for loading the default lists by hand (decision of 20 November):
-- assets.asset_family  owner_scope = 'PLATFORM', organization_id empty; code readable and unique ('ELEVATOR',
--                      'FIRE_EXTINGUISHER', 'GATE'…), names in the three languages, sort_order.
-- assets.asset_model   family_id of its family, owner_scope = 'PLATFORM', name, model_version and manufacturer
--                      optional. id, created_at, updated_at, version have defaults.

CREATE TABLE assets.asset_family
(
    id              UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    owner_scope     TEXT        NOT NULL DEFAULT 'PLATFORM',
    organization_id UUID,
    code            TEXT        NOT NULL,
    name_ar         TEXT,
    name_en         TEXT,
    name_fr         TEXT,
    sort_order      INTEGER     NOT NULL DEFAULT 100,
    active          BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX asset_family_code_uq ON assets.asset_family (organization_id, code) NULLS NOT DISTINCT;

CREATE TABLE assets.asset_model
(
    id              UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    family_id       UUID        NOT NULL REFERENCES assets.asset_family (id),
    owner_scope     TEXT        NOT NULL DEFAULT 'PLATFORM',
    organization_id UUID,
    name            TEXT        NOT NULL,
    model_version   TEXT,
    manufacturer    TEXT,
    active          BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX asset_model_name_uq
    ON assets.asset_model (family_id, organization_id, name, model_version) NULLS NOT DISTINCT;

CREATE TABLE assets.supplier
(
    id              UUID PRIMARY KEY,
    organization_id UUID        NOT NULL,
    name            TEXT        NOT NULL,
    phone           TEXT,
    email           TEXT,
    activity        TEXT,
    active          BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    version         BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX supplier_organization_idx ON assets.supplier (organization_id, name);

CREATE TABLE assets.asset
(
    id              UUID PRIMARY KEY,
    organization_id UUID        NOT NULL,
    site_id         UUID        NOT NULL,
    family_id       UUID        NOT NULL REFERENCES assets.asset_family (id),
    model_id        UUID REFERENCES assets.asset_model (id),
    name            TEXT        NOT NULL,
    reference       TEXT        NOT NULL,
    building_id     UUID,
    floor           INTEGER,
    outdoor_area_id UUID,
    state           TEXT        NOT NULL,
    serial_number   TEXT,
    commissioned_on DATE,
    supplier_id     UUID REFERENCES assets.supplier (id),
    -- Characteristics (G3) as JSON text keyed by definition code, validated by the customization module (ADR-039).
    attributes      TEXT        NOT NULL DEFAULT '{}',
    qr_code         TEXT        NOT NULL,
    archived_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    version         BIGINT      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX asset_reference_uq ON assets.asset (site_id, reference);
CREATE UNIQUE INDEX asset_qr_code_uq ON assets.asset (qr_code);
CREATE INDEX asset_site_idx ON assets.asset (site_id, family_id);

CREATE TABLE assets.asset_document
(
    id          UUID PRIMARY KEY,
    asset_id    UUID        NOT NULL REFERENCES assets.asset (id),
    file_id     UUID        NOT NULL,
    type        TEXT        NOT NULL,
    title       TEXT        NOT NULL,
    added_by    UUID        NOT NULL,
    added_at    TIMESTAMPTZ NOT NULL,
    archived_at TIMESTAMPTZ,
    version     BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX asset_document_asset_idx ON assets.asset_document (asset_id);

CREATE TABLE assets.warranty
(
    id                UUID PRIMARY KEY,
    asset_id          UUID        NOT NULL REFERENCES assets.asset (id),
    start_date        DATE        NOT NULL,
    end_date          DATE        NOT NULL,
    alert_days_before INTEGER     NOT NULL DEFAULT 30,
    document_id       UUID REFERENCES assets.asset_document (id),
    note              TEXT,
    active            BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    version           BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX warranty_asset_idx ON assets.warranty (asset_id);
CREATE INDEX warranty_end_idx ON assets.warranty (end_date) WHERE active;
