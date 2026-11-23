-- Roles, permissions and access (C1 to C10, D2, D3, D5, E1 to E5, ADR-026). Statuses, roles and permissions are plain
-- text without CHECK constraint (architecture rule). Nothing is ever deleted (M2): assignments end, adjustments are
-- cancelled, accesses are deactivated, occupancies end. Every table carries organization_id (and site_id when it
-- applies) so that perimeter filters stay simple. No foreign key to another module's schema (CF-3).

-- Platform default permissions of each role, from the specification (section 3). Read only for the application.
CREATE TABLE membership.role_default
(
    role        TEXT PRIMARY KEY,
    permissions TEXT NOT NULL
);
INSERT INTO membership.role_default (role, permissions)
VALUES ('PLATFORM_ADMIN', 'organization:create'),
       ('ORG_ADMIN', ''), -- always every permission of its organization, computed in code (C8)
       ('SITE_MANAGER', 'access-request:decide,member:manage,member:read,occupancy:manage,person:invite,unit:import,unit:manage'),
       ('TECHNICIAN', ''),
       ('RESIDENT', '');
REVOKE INSERT, UPDATE ON membership.role_default FROM "${app-role}";

-- The permissions of a role in one organization, when its ORG_ADMIN changed the defaults (C9).
CREATE TABLE membership.role_profile
(
    id              UUID PRIMARY KEY,
    organization_id UUID        NOT NULL,
    role            TEXT        NOT NULL,
    permissions     TEXT        NOT NULL,
    customized      BOOLEAN     NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    updated_by      UUID        NOT NULL,
    version         BIGINT      NOT NULL,
    UNIQUE (organization_id, role)
);

CREATE TABLE membership.role_assignment
(
    id               UUID PRIMARY KEY,
    person_id        UUID        NOT NULL,
    role             TEXT        NOT NULL,
    scope_type       TEXT        NOT NULL,
    organization_id  UUID,
    site_id          UUID,
    assigned_by_type TEXT        NOT NULL,
    assigned_by_id   UUID        NOT NULL,
    assigned_at      TIMESTAMPTZ NOT NULL,
    ended_by_id      UUID,
    ended_at         TIMESTAMPTZ,
    end_reason       TEXT,
    version          BIGINT      NOT NULL
);
CREATE UNIQUE INDEX role_assignment_active_uq ON membership.role_assignment
    (person_id, role, scope_type, organization_id, site_id) NULLS NOT DISTINCT WHERE ended_at IS NULL;
CREATE INDEX role_assignment_person_idx ON membership.role_assignment (person_id) WHERE ended_at IS NULL;
CREATE INDEX role_assignment_organization_idx ON membership.role_assignment (organization_id, assigned_at);

CREATE TABLE membership.permission_adjustment
(
    id              UUID PRIMARY KEY,
    person_id       UUID        NOT NULL,
    organization_id UUID        NOT NULL,
    scope_type      TEXT        NOT NULL,
    site_id         UUID,
    permission      TEXT        NOT NULL,
    effect          TEXT        NOT NULL,
    reason          TEXT,
    created_by      UUID        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    cancelled_by    UUID,
    cancelled_at    TIMESTAMPTZ,
    version         BIGINT      NOT NULL
);
CREATE UNIQUE INDEX permission_adjustment_active_uq ON membership.permission_adjustment
    (person_id, organization_id, site_id, permission) NULLS NOT DISTINCT WHERE cancelled_at IS NULL;
CREATE INDEX permission_adjustment_person_idx ON membership.permission_adjustment (person_id)
    WHERE cancelled_at IS NULL;

CREATE TABLE membership.site_membership
(
    id                   UUID PRIMARY KEY,
    person_id            UUID        NOT NULL,
    organization_id      UUID        NOT NULL,
    site_id              UUID        NOT NULL,
    status               TEXT        NOT NULL,
    origin               TEXT        NOT NULL,
    allowed_building_ids TEXT        NOT NULL,
    declared_units       TEXT        NOT NULL,
    decided_by           UUID,
    decided_at           TIMESTAMPTZ,
    decision_reason      TEXT,
    created_at           TIMESTAMPTZ NOT NULL,
    ended_at             TIMESTAMPTZ,
    updated_at           TIMESTAMPTZ NOT NULL,
    version              BIGINT      NOT NULL
);
-- One current access per person and site; a rejected request does not prevent a new one.
CREATE UNIQUE INDEX site_membership_current_uq ON membership.site_membership (person_id, site_id)
    WHERE status <> 'REJECTED';
CREATE INDEX site_membership_site_idx ON membership.site_membership (site_id, status, created_at);
CREATE INDEX site_membership_person_idx ON membership.site_membership (person_id);

CREATE TABLE membership.occupancy
(
    id                UUID PRIMARY KEY,
    person_id         UUID        NOT NULL,
    unit_id           UUID        NOT NULL,
    organization_id   UUID        NOT NULL,
    site_id           UUID        NOT NULL,
    role              TEXT        NOT NULL,
    primary_tenant    BOOLEAN     NOT NULL,
    validation_status TEXT        NOT NULL,
    started_on        DATE        NOT NULL,
    ended_on          DATE,
    end_reason        TEXT,
    created_by        UUID        NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL,
    version           BIGINT      NOT NULL
);
-- E5: at most one active owner per unit. E3: at most one active primary tenant. One active occupancy per person and unit.
CREATE UNIQUE INDEX occupancy_owner_uq ON membership.occupancy (unit_id) WHERE role = 'OWNER' AND ended_on IS NULL;
CREATE UNIQUE INDEX occupancy_primary_tenant_uq ON membership.occupancy (unit_id) WHERE primary_tenant AND ended_on IS NULL;
CREATE UNIQUE INDEX occupancy_person_unit_uq ON membership.occupancy (person_id, unit_id) WHERE ended_on IS NULL;
CREATE INDEX occupancy_person_idx ON membership.occupancy (person_id) WHERE ended_on IS NULL;

CREATE TABLE membership.join_code
(
    site_id         UUID PRIMARY KEY,
    organization_id UUID        NOT NULL,
    code            TEXT        NOT NULL UNIQUE,
    enabled         BOOLEAN     NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    version         BIGINT      NOT NULL
);

CREATE TABLE membership.registration
(
    id              UUID PRIMARY KEY,
    organization_id UUID        NOT NULL,
    site_id         UUID        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    completed_at    TIMESTAMPTZ,
    person_id       UUID,
    language        TEXT        NOT NULL,
    version         BIGINT      NOT NULL
);
