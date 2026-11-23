-- Sprint 1, spec v3.4: geography as one zone tree (A8), addresses attached to a zone and copied to buildings (A9),
-- country carried by the organization (A1, M4, M5), unit categories (A6) and unit type OTHER.
-- Statuses and types stay plain text without CHECK constraint (architecture rule). Nothing is deleted (M2); the
-- dropped table sites.city and the dropped address columns only ever held development data (no production data
-- exists before the pilot). Earlier migrations are left unchanged.
--
-- ============================================================================================================
-- REFERENCE TABLES, FORMAT FOR LOADING BY HAND (decision of 20 November: lists loaded directly by the architect)
-- ============================================================================================================
-- sites.country        one row per country. code = ISO 3166-1 alpha-2 ('DZ'); names in ar, en, fr;
--                      attachment_level = level an address is attached to ("city": 2 = commune for Algeria);
--                      currency_code = ISO 4217 ('DZD', M5); time_zone = IANA ('Africa/Algiers');
--                      languages = comma-separated, display order ('AR,FR,EN'); calling_code = '+213';
--                      working_days = comma-separated MON..SUN ('SUN,MON,TUE,WED,THU').
-- sites.country_level  one row per named level of a country: (country_code, level 1..4, name_ar, name_en, name_fr).
--                      Algeria: 1 = Wilaya, 2 = Commune.
-- sites.zone           one row per zone. Fill ONLY: country_code, code, parent_code (code of the parent zone, empty
--                      at level 1), name_ar, name_en, name_fr, and optionally status ('VALIDATED' by default).
--                      id, parent_id, level and path are computed by the trigger below (and by the application for
--                      the zones it creates): never type them. Codes are readable and unique per country; for
--                      Algeria the wilaya code ('16') and the commune code ('1601' = wilaya + 2 digits).
--                      Example:
--                        INSERT INTO sites.zone (country_code, code, parent_code, name_ar, name_en, name_fr)
--                        VALUES ('DZ', '16', NULL, 'الجزائر', 'Algiers', 'Alger'),
--                               ('DZ', '1601', '16', 'الجزائر الوسطى', 'Algiers Centre', 'Alger-Centre');
--                      Parents must be inserted before their children (or in the same statement, parents first).
--                      A zone is never deleted: set status = 'DEACTIVATED' (M2).
-- sites.unit_category  default categories (scope_type = 'PLATFORM'): fill unit_type ('APARTMENT', 'SHOP', 'PARKING',
--                      'STORAGE', 'OTHER'), code (readable, unique per type: 'STUDIO', 'T1'…), names, sort_order.
--                      id, created_at, updated_at, version have defaults.
-- ============================================================================================================

-- ---- country: a set of parameters (A8) ----
ALTER TABLE sites.country ADD COLUMN attachment_level INTEGER;
ALTER TABLE sites.country ADD COLUMN currency_code TEXT;
ALTER TABLE sites.country ADD COLUMN time_zone TEXT;
ALTER TABLE sites.country ADD COLUMN languages TEXT;
ALTER TABLE sites.country ADD COLUMN calling_code TEXT;
ALTER TABLE sites.country ADD COLUMN working_days TEXT;
UPDATE sites.country
SET attachment_level = 2, currency_code = 'DZD', time_zone = 'Africa/Algiers', languages = 'AR,FR,EN',
    calling_code = '+213', working_days = 'SUN,MON,TUE,WED,THU'
WHERE code = 'DZ';
UPDATE sites.country SET name_en = name_fr WHERE name_en IS NULL;
ALTER TABLE sites.country ALTER COLUMN name_en SET NOT NULL;
ALTER TABLE sites.country ALTER COLUMN attachment_level SET NOT NULL;
ALTER TABLE sites.country ALTER COLUMN currency_code SET NOT NULL;
ALTER TABLE sites.country ALTER COLUMN time_zone SET NOT NULL;
ALTER TABLE sites.country ALTER COLUMN languages SET NOT NULL;
ALTER TABLE sites.country ALTER COLUMN calling_code SET NOT NULL;
ALTER TABLE sites.country ALTER COLUMN working_days SET NOT NULL;

CREATE TABLE sites.country_level
(
    country_code TEXT    NOT NULL REFERENCES sites.country (code),
    level        INTEGER NOT NULL,
    name_ar      TEXT    NOT NULL,
    name_en      TEXT    NOT NULL,
    name_fr      TEXT    NOT NULL,
    PRIMARY KEY (country_code, level)
);
INSERT INTO sites.country_level (country_code, level, name_ar, name_en, name_fr)
VALUES ('DZ', 1, 'ولاية', 'Wilaya', 'Wilaya'),
       ('DZ', 2, 'بلدية', 'Commune', 'Commune');
REVOKE INSERT, UPDATE ON sites.country_level FROM "${app-role}";

-- ---- zone: one tree for every country (A8) ----
-- status: VALIDATED (reference data), PROVISIONAL (created by a promoteur), DEACTIVATED (kept, no longer offered).
-- path: codes from the country down to the zone, 'DZ/16/1601/', for filters and counts at every level by prefix.
CREATE TABLE sites.zone
(
    id                         UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    country_code               TEXT        NOT NULL REFERENCES sites.country (code),
    code                       TEXT        NOT NULL,
    parent_code                TEXT,
    parent_id                  UUID REFERENCES sites.zone (id),
    level                      INTEGER,
    path                       TEXT,
    name_ar                    TEXT        NOT NULL,
    name_en                    TEXT        NOT NULL,
    name_fr                    TEXT        NOT NULL,
    status                     TEXT        NOT NULL DEFAULT 'VALIDATED',
    created_by_organization_id UUID,
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                    BIGINT      NOT NULL DEFAULT 0,
    UNIQUE (country_code, code)
);
CREATE UNIQUE INDEX zone_path_uq ON sites.zone (path);
CREATE INDEX zone_path_prefix_idx ON sites.zone (path text_pattern_ops);
CREATE INDEX zone_parent_idx ON sites.zone (country_code, parent_id, code);

-- Fills parent_id, level and path from parent_code, so that hand-loaded rows need no computed value. The
-- application computes the same values for the zones it creates; the trigger only fills what is missing.
CREATE FUNCTION sites.zone_fill_tree() RETURNS trigger
    LANGUAGE plpgsql AS
$$
DECLARE
    parent sites.zone%ROWTYPE;
BEGIN
    IF NEW.parent_code IS NOT NULL AND btrim(NEW.parent_code) = '' THEN
        NEW.parent_code := NULL;
    END IF;
    IF NEW.parent_id IS NULL AND NEW.parent_code IS NOT NULL THEN
        SELECT * INTO parent FROM sites.zone WHERE country_code = NEW.country_code AND code = NEW.parent_code;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'Zone %: parent % unknown in country % (insert parents first)',
                NEW.code, NEW.parent_code, NEW.country_code;
        END IF;
        NEW.parent_id := parent.id;
    ELSIF NEW.parent_id IS NOT NULL THEN
        SELECT * INTO parent FROM sites.zone WHERE id = NEW.parent_id;
        NEW.parent_code := parent.code;
    END IF;
    IF NEW.parent_id IS NULL THEN
        NEW.level := COALESCE(NEW.level, 1);
        NEW.path := COALESCE(NEW.path, NEW.country_code || '/' || NEW.code || '/');
    ELSE
        NEW.level := COALESCE(NEW.level, parent.level + 1);
        NEW.path := COALESCE(NEW.path, parent.path || NEW.code || '/');
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER zone_fill_tree
    BEFORE INSERT ON sites.zone
    FOR EACH ROW
EXECUTE FUNCTION sites.zone_fill_tree();

-- Existing development communes become zones: a level-1 zone per wilaya (first two digits of the commune code),
-- then the communes under it. The development data (db/dev) gives them their real names.
INSERT INTO sites.zone (country_code, code, parent_code, name_ar, name_en, name_fr)
SELECT DISTINCT country_code, substr(code, 1, 2), NULL, 'ولاية ' || substr(code, 1, 2),
                'Wilaya ' || substr(code, 1, 2), 'Wilaya ' || substr(code, 1, 2)
FROM sites.city
WHERE country_code = 'DZ' AND length(code) = 4;
INSERT INTO sites.zone (country_code, code, parent_code, name_ar, name_en, name_fr)
SELECT country_code, code, CASE WHEN country_code = 'DZ' AND length(code) = 4 THEN substr(code, 1, 2) END,
       name_ar, COALESCE(name_en, name_fr), name_fr
FROM sites.city;
ALTER TABLE sites.zone ALTER COLUMN level SET NOT NULL;
ALTER TABLE sites.zone ALTER COLUMN path SET NOT NULL;

-- ---- organization: the country is carried by the organization (A1, M4, M5) ----
-- Existing development organizations are Algerian. New type value RESIDENCE: a residence client without promoteur.
ALTER TABLE sites.organization ADD COLUMN country_code TEXT REFERENCES sites.country (code);
UPDATE sites.organization SET country_code = 'DZ' WHERE country_code IS NULL;
ALTER TABLE sites.organization ALTER COLUMN country_code SET NOT NULL;

-- ---- address: attached to a zone of the attachment level, optional postal code (A9) ----
-- origin: OWN (typed for this owner) or SITE_COPY (a building's copy of its site's address at creation).
ALTER TABLE sites.address ADD COLUMN zone_id UUID REFERENCES sites.zone (id);
ALTER TABLE sites.address ADD COLUMN postal_code TEXT;
ALTER TABLE sites.address ADD COLUMN origin TEXT NOT NULL DEFAULT 'OWN';
ALTER TABLE sites.address ALTER COLUMN origin DROP DEFAULT;
UPDATE sites.address a
SET zone_id = z.id
FROM sites.zone z
WHERE z.country_code = a.country_code AND z.code = a.city_code;
ALTER TABLE sites.address ALTER COLUMN zone_id SET NOT NULL;
ALTER TABLE sites.address DROP CONSTRAINT address_country_code_city_code_fkey;
ALTER TABLE sites.address DROP COLUMN country_code;
ALTER TABLE sites.address DROP COLUMN city_code;
CREATE INDEX address_zone_idx ON sites.address (zone_id) WHERE replaced_at IS NULL;

-- A building no longer inherits its site's address live (decision of 20 November): every existing building without
-- an address of its own receives a copy of its site's current address.
INSERT INTO sites.address (id, organization_id, site_id, owner_type, owner_id, label, zone_id, address_line,
                           postal_code, latitude, longitude, origin, created_at, replaced_at, version)
SELECT gen_random_uuid(), b.organization_id, b.site_id, 'BUILDING', b.id, s.label, s.zone_id, s.address_line,
       s.postal_code, s.latitude, s.longitude, 'SITE_COPY', now(), NULL, 0
FROM sites.building b
         JOIN sites.address s ON s.owner_type = 'SITE' AND s.owner_id = b.site_id AND s.replaced_at IS NULL
WHERE NOT EXISTS (SELECT 1 FROM sites.address own
                  WHERE own.owner_type = 'BUILDING' AND own.owner_id = b.id AND own.replaced_at IS NULL);

DROP TABLE sites.city;

-- ---- unit categories (A6) ----
-- scope_type: PLATFORM (defaults kept by the administrators, M7), ORGANIZATION (added by a promoteur) or SITE (added
-- by a manager for one residence). organization_id and site_id are empty for the defaults.
CREATE TABLE sites.unit_category
(
    id              UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    scope_type      TEXT        NOT NULL DEFAULT 'PLATFORM',
    organization_id UUID,
    site_id         UUID,
    unit_type       TEXT        NOT NULL,
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
CREATE UNIQUE INDEX unit_category_code_uq ON sites.unit_category (scope_type, organization_id, site_id, unit_type, code)
    NULLS NOT DISTINCT;
CREATE INDEX unit_category_organization_idx ON sites.unit_category (organization_id, unit_type);

-- One default "unclassified" category per unit type: the category of units created before categories existed, and
-- a safe default while the official lists are not loaded. Real typologies come from the reference data.
INSERT INTO sites.unit_category (unit_type, code, name_ar, name_en, name_fr, sort_order)
VALUES ('APARTMENT', 'UNCLASSIFIED', 'غير مصنف', 'Unclassified', 'Non classé', 999),
       ('SHOP', 'UNCLASSIFIED', 'غير مصنف', 'Unclassified', 'Non classé', 999),
       ('PARKING', 'UNCLASSIFIED', 'غير مصنف', 'Unclassified', 'Non classé', 999),
       ('STORAGE', 'UNCLASSIFIED', 'غير مصنف', 'Unclassified', 'Non classé', 999),
       ('OTHER', 'UNCLASSIFIED', 'غير مصنف', 'Unclassified', 'Non classé', 999);

-- ---- unit: mandatory category (A6) ----
ALTER TABLE sites.unit ADD COLUMN category_id UUID REFERENCES sites.unit_category (id);
UPDATE sites.unit u
SET category_id = c.id
FROM sites.unit_category c
WHERE c.scope_type = 'PLATFORM' AND c.unit_type = u.type AND c.code = 'UNCLASSIFIED';
ALTER TABLE sites.unit ALTER COLUMN category_id SET NOT NULL;
CREATE INDEX unit_category_idx ON sites.unit (category_id);

-- Rights of the application role: country and levels are read only; zones and categories are written by the
-- application (provisional zones, organization and site categories). Never DELETE (M2).
