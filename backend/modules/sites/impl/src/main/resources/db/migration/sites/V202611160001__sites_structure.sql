-- Structure of the client organizations (A1 to A6): organizations, sites, buildings, outdoor areas, units, and
-- one address table for every owner. Statuses and types are plain text, without CHECK constraint (architecture
-- rule). Nothing is ever deleted (M2). Foreign keys stay inside the sites schema (CF-3). Every business table carries
-- organization_id and site_id so that perimeter filters stay simple and indexable.

-- Reference data (A1): countries and cities. Read only for the application.
CREATE TABLE sites.country
(
    code    TEXT PRIMARY KEY,
    name_ar TEXT NOT NULL,
    name_en TEXT,
    name_fr TEXT NOT NULL
);

-- For Algeria a city is a commune: its 4-digit code starts with the 2-digit code of its wilaya.
CREATE TABLE sites.city
(
    country_code TEXT NOT NULL REFERENCES sites.country (code),
    code         TEXT NOT NULL,
    name_ar      TEXT NOT NULL,
    name_en      TEXT,
    name_fr      TEXT NOT NULL,
    PRIMARY KEY (country_code, code)
);

INSERT INTO sites.country (code, name_ar, name_en, name_fr) VALUES ('DZ', 'الجزائر', 'Algeria', 'Algérie');

REVOKE INSERT, UPDATE ON sites.country, sites.city FROM "${app-role}";

CREATE TABLE sites.organization
(
    id            UUID PRIMARY KEY,
    type          TEXT        NOT NULL,
    name          TEXT        NOT NULL,
    contact_phone TEXT,
    contact_email TEXT,
    active        BOOLEAN     NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    version       BIGINT      NOT NULL
);
CREATE INDEX organization_name_idx ON sites.organization (lower(name));

CREATE TABLE sites.site
(
    id               UUID PRIMARY KEY,
    organization_id  UUID        NOT NULL REFERENCES sites.organization (id),
    type             TEXT        NOT NULL,
    name             TEXT        NOT NULL,
    lifecycle_status TEXT        NOT NULL,
    active           BOOLEAN     NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    version          BIGINT      NOT NULL
);
CREATE INDEX site_organization_idx ON sites.site (organization_id, lower(name));

CREATE TABLE sites.building
(
    id              UUID PRIMARY KEY,
    organization_id UUID        NOT NULL,
    site_id         UUID        NOT NULL REFERENCES sites.site (id),
    name            TEXT        NOT NULL,
    building_use    TEXT        NOT NULL,
    floor_count     INTEGER     NOT NULL,
    active          BOOLEAN     NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    version         BIGINT      NOT NULL
);
CREATE UNIQUE INDEX building_name_uq ON sites.building (site_id, lower(name));

CREATE TABLE sites.outdoor_area
(
    id              UUID PRIMARY KEY,
    organization_id UUID        NOT NULL,
    site_id         UUID        NOT NULL REFERENCES sites.site (id),
    type            TEXT        NOT NULL,
    name            TEXT        NOT NULL,
    active          BOOLEAN     NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    version         BIGINT      NOT NULL
);
CREATE UNIQUE INDEX outdoor_area_name_uq ON sites.outdoor_area (site_id, lower(name));

CREATE TABLE sites.unit
(
    id               UUID PRIMARY KEY,
    organization_id  UUID        NOT NULL,
    site_id          UUID        NOT NULL REFERENCES sites.site (id),
    type             TEXT        NOT NULL,
    reference        TEXT        NOT NULL,
    building_id      UUID REFERENCES sites.building (id),
    outdoor_area_id  UUID REFERENCES sites.outdoor_area (id),
    floor            INTEGER,
    annex_of_unit_id UUID REFERENCES sites.unit (id),
    saleable         BOOLEAN     NOT NULL,
    rentable         BOOLEAN     NOT NULL,
    active           BOOLEAN     NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    version          BIGINT      NOT NULL
);
CREATE UNIQUE INDEX unit_reference_uq ON sites.unit (site_id, lower(reference));
CREATE INDEX unit_building_idx ON sites.unit (building_id);
CREATE INDEX unit_outdoor_area_idx ON sites.unit (outdoor_area_id);

-- One table for every owner: a site has exactly one current address, a building may have its own (A1).
-- A changed address is replaced: the old row keeps replaced_at (M2).
CREATE TABLE sites.address
(
    id              UUID PRIMARY KEY,
    organization_id UUID             NOT NULL,
    site_id         UUID             NOT NULL REFERENCES sites.site (id),
    owner_type      TEXT             NOT NULL,
    owner_id        UUID             NOT NULL,
    label           TEXT,
    country_code    TEXT             NOT NULL,
    city_code       TEXT             NOT NULL,
    address_line    TEXT             NOT NULL,
    latitude        DOUBLE PRECISION,
    longitude       DOUBLE PRECISION,
    created_at      TIMESTAMPTZ      NOT NULL,
    replaced_at     TIMESTAMPTZ,
    version         BIGINT           NOT NULL,
    FOREIGN KEY (country_code, city_code) REFERENCES sites.city (country_code, code)
);
CREATE UNIQUE INDEX address_current_uq ON sites.address (owner_type, owner_id) WHERE replaced_at IS NULL;
