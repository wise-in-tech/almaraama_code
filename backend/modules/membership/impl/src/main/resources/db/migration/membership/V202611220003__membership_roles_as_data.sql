-- Sprint 1, spec v3.4 (ADR-026 revision 2): roles become data, an organization stores only its DIFFERENCES from the
-- default permissions of a role (C11), the permission catalogue follows the v3.4 resources, and the SMS
-- self-registration is removed (it comes back as the QR code registration in sprint 2).
-- Plain text without CHECK constraint (architecture rule). Nothing is deleted from the business tables (M2): old
-- adjustments are cancelled and replaced; role_default, role_profile and registration only ever held development
-- data or platform defaults now rebuilt below. Earlier migrations are left unchanged.
--
-- ============================================================================================================
-- FORMAT FOR LOADING BY HAND
-- ============================================================================================================
-- membership.role                     one row per role. code (upper case: 'SITE_MANAGER'); kind ('PLATFORM',
--                                     'ADMINISTRATION', 'STAFF', 'TECHNICIAN', 'OCCUPANT'); scope_types, comma-
--                                     separated ('SITE' or 'ORGANIZATION'); grants_all (true only for the promoteur);
--                                     organization_id (empty: a platform role; reserved for custom roles); names in ar,
--                                     en, fr; sort_order. Code checks permissions, never role codes.
-- membership.role_default_permission  one row per role and default permission (specification section 3.3):
--                                     (role_code, permission), permission as in the catalogue ('unit:import').
--                                     A permission added by a later sprint comes with its rows here (RB-6).
-- ============================================================================================================

CREATE TABLE membership.role
(
    code            TEXT PRIMARY KEY,
    kind            TEXT    NOT NULL,
    scope_types     TEXT    NOT NULL,
    grants_all      BOOLEAN NOT NULL DEFAULT FALSE,
    organization_id UUID,
    name_ar         TEXT    NOT NULL,
    name_en         TEXT    NOT NULL,
    name_fr         TEXT    NOT NULL,
    sort_order      INTEGER NOT NULL DEFAULT 100,
    active          BOOLEAN NOT NULL DEFAULT TRUE
);
INSERT INTO membership.role (code, kind, scope_types, grants_all, name_ar, name_en, name_fr, sort_order)
VALUES ('PLATFORM_ADMIN', 'PLATFORM', 'PLATFORM', FALSE, 'مدير المنصة', 'Platform administrator',
        'Administrateur de la plateforme', 0),
       ('ORG_ADMIN', 'ADMINISTRATION', 'ORGANIZATION', TRUE, 'المرقّي', 'Developer', 'Promoteur', 10),
       ('SITE_MANAGER', 'STAFF', 'SITE', FALSE, 'المسيّر', 'Manager', 'Gestionnaire', 20),
       ('TECHNICIAN', 'TECHNICIAN', 'SITE', FALSE, 'التقني', 'Technician', 'Technicien', 30),
       ('RESIDENT', 'OCCUPANT', 'SITE', FALSE, 'الساكن', 'Resident', 'Résident', 40);

CREATE TABLE membership.role_default_permission
(
    role_code  TEXT NOT NULL REFERENCES membership.role (code),
    permission TEXT NOT NULL,
    PRIMARY KEY (role_code, permission)
);
-- Specification v3.4, section 3.3, restricted to the permissions of sprints 0 and 1. ORG_ADMIN has grants_all.
INSERT INTO membership.role_default_permission (role_code, permission)
VALUES ('PLATFORM_ADMIN', 'organization:create'),
       ('PLATFORM_ADMIN', 'organization:update'),
       ('PLATFORM_ADMIN', 'organization:deactivate'),
       ('SITE_MANAGER', 'site:read'),
       ('SITE_MANAGER', 'building:read'),
       ('SITE_MANAGER', 'outdoor-area:read'),
       ('SITE_MANAGER', 'registration-qr:read'),
       ('SITE_MANAGER', 'unit-category:create'),
       ('SITE_MANAGER', 'unit-category:read'),
       ('SITE_MANAGER', 'unit-category:update'),
       ('SITE_MANAGER', 'unit-category:archive'),
       ('SITE_MANAGER', 'unit:create'),
       ('SITE_MANAGER', 'unit:read'),
       ('SITE_MANAGER', 'unit:update'),
       ('SITE_MANAGER', 'unit:import'),
       ('SITE_MANAGER', 'account:create'),
       ('SITE_MANAGER', 'account:read'),
       ('SITE_MANAGER', 'account:invite'),
       ('SITE_MANAGER', 'access:approve'),
       ('SITE_MANAGER', 'access:refuse'),
       ('SITE_MANAGER', 'access:restrict'),
       ('SITE_MANAGER', 'access:revoke'),
       ('SITE_MANAGER', 'occupancy:create'),
       ('SITE_MANAGER', 'occupancy:read'),
       ('SITE_MANAGER', 'occupancy:end'),
       ('RESIDENT', 'site:read'),
       ('RESIDENT', 'building:read'),
       ('RESIDENT', 'outdoor-area:read');
REVOKE INSERT, UPDATE ON membership.role, membership.role_default_permission FROM "${app-role}";

-- Differences of an organization from a role's defaults (C9, C11): ADD a permission the role does not have by
-- default, REMOVE one it has. "Back to defaults" cancels them (cancelled_at), nothing is deleted (M2).
CREATE TABLE membership.organization_role_delta
(
    id              UUID PRIMARY KEY,
    organization_id UUID        NOT NULL,
    role_code       TEXT        NOT NULL REFERENCES membership.role (code),
    permission      TEXT        NOT NULL,
    effect          TEXT        NOT NULL,
    reason          TEXT,
    created_by      UUID        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    cancelled_by    UUID,
    cancelled_at    TIMESTAMPTZ,
    version         BIGINT      NOT NULL
);
CREATE UNIQUE INDEX organization_role_delta_active_uq ON membership.organization_role_delta
    (organization_id, role_code, permission) WHERE cancelled_at IS NULL;
CREATE INDEX organization_role_delta_organization_idx ON membership.organization_role_delta (organization_id)
    WHERE cancelled_at IS NULL;

-- ---- conversion of the sprint 1 (v3.2) permission codes ----
CREATE TEMPORARY TABLE permission_rename (old_code TEXT NOT NULL, new_code TEXT NOT NULL) ON COMMIT DROP;
INSERT INTO permission_rename (old_code, new_code)
VALUES ('organization:create', 'organization:create'),
       ('organization:update', 'organization:update'),
       ('site:create', 'site:create'),
       ('site:update', 'site:update'),
       ('building:manage', 'building:create'),
       ('building:manage', 'building:update'),
       ('outdoor-area:manage', 'outdoor-area:create'),
       ('outdoor-area:manage', 'outdoor-area:update'),
       ('unit:manage', 'unit:create'),
       ('unit:manage', 'unit:update'),
       ('unit:import', 'unit:import'),
       ('person:read', 'account:read'),
       ('person:invite', 'account:create'),
       ('person:invite', 'account:invite'),
       ('member:read', 'account:read'),
       ('member:manage', 'access:restrict'),
       ('member:manage', 'access:revoke'),
       ('member:manage', 'registration-qr:read'),
       ('member:manage', 'registration-qr:manage'),
       ('access-request:decide', 'access:approve'),
       ('access-request:decide', 'access:refuse'),
       ('occupancy:manage', 'occupancy:create'),
       ('occupancy:manage', 'occupancy:read'),
       ('occupancy:manage', 'occupancy:end');

-- Customized role profiles (full lists) become differences from the new defaults.
WITH profile_permission AS (
    SELECT DISTINCT p.organization_id, p.role, r.new_code AS permission, p.updated_by, p.updated_at
    FROM membership.role_profile p
             CROSS JOIN LATERAL unnest(string_to_array(p.permissions, ',')) AS old(code)
             JOIN permission_rename r ON r.old_code = btrim(old.code)
    WHERE p.customized
), customized AS (
    SELECT DISTINCT organization_id, role, updated_by, updated_at
    FROM membership.role_profile
    WHERE customized AND role IN (SELECT code FROM membership.role WHERE NOT grants_all)
)
INSERT INTO membership.organization_role_delta (id, organization_id, role_code, permission, effect, reason,
                                                created_by, created_at, version)
SELECT gen_random_uuid(), pp.organization_id, pp.role, pp.permission, 'ADD', 'Converted from the sprint 1 profile',
       pp.updated_by, pp.updated_at, 0
FROM profile_permission pp
WHERE pp.role IN (SELECT code FROM membership.role WHERE NOT grants_all)
  AND NOT EXISTS (SELECT 1 FROM membership.role_default_permission d
                  WHERE d.role_code = pp.role AND d.permission = pp.permission)
UNION ALL
SELECT gen_random_uuid(), c.organization_id, c.role, d.permission, 'REMOVE', 'Converted from the sprint 1 profile',
       c.updated_by, c.updated_at, 0
FROM customized c
         JOIN membership.role_default_permission d ON d.role_code = c.role
WHERE NOT EXISTS (SELECT 1 FROM profile_permission pp
                  WHERE pp.organization_id = c.organization_id AND pp.role = c.role AND pp.permission = d.permission);

-- Active person adjustments on an old code are replaced by adjustments on the new codes, then cancelled.
INSERT INTO membership.permission_adjustment (id, person_id, organization_id, scope_type, site_id, permission, effect,
                                              reason, created_by, created_at, version)
SELECT DISTINCT ON (a.person_id, a.organization_id, a.site_id, r.new_code)
       gen_random_uuid(), a.person_id, a.organization_id, a.scope_type, a.site_id, r.new_code, a.effect, a.reason,
       a.created_by, a.created_at, 0
FROM membership.permission_adjustment a
         JOIN permission_rename r ON r.old_code = a.permission AND r.new_code <> a.permission
WHERE a.cancelled_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM membership.permission_adjustment same
                  WHERE same.cancelled_at IS NULL AND same.person_id = a.person_id
                    AND same.organization_id = a.organization_id
                    AND same.site_id IS NOT DISTINCT FROM a.site_id AND same.permission = r.new_code);
UPDATE membership.permission_adjustment a
SET cancelled_at = now(), cancelled_by = a.created_by
WHERE a.cancelled_at IS NULL
  AND a.permission IN (SELECT old_code FROM permission_rename WHERE old_code <> new_code);

DROP TABLE membership.role_profile;
DROP TABLE membership.role_default;

-- Role assignments now name a row of membership.role.
ALTER TABLE membership.role_assignment
    ADD CONSTRAINT role_assignment_role_fkey FOREIGN KEY (role) REFERENCES membership.role (code);

-- ---- SMS self-registration removed (spec v3.4, D5); replaced by the QR code registration in sprint 2 ----
DROP TABLE membership.registration;
