-- Sprint 3 (H1 to H10, ADR-037): issue categories and types with their routing and visibility per site, tickets,
-- their photos, routed technicians and activities. Nothing is deleted (M2). The status of a ticket is a copy of its
-- position in its circuit (workflow module), kept in the same transaction for fast lists; no enum, no CHECK (ADR-036).
--
-- Format for loading the default lists by hand (decision of 20 November):
-- maintenance.issue_category  owner_scope = 'PLATFORM', code ('TECHNICAL', 'CLEANING', 'SECURITY'…), names, sort_order.
-- maintenance.issue_type      category_id, owner_scope = 'PLATFORM', code, names, asset_required (H3: true for the
--                             technical types), default_visibility ('COMMON' or 'PRIVATE', H8), asset_family_ids
--                             (comma-separated family ids, empty = every family), sort_order.

CREATE TABLE maintenance.issue_category
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
CREATE UNIQUE INDEX issue_category_code_uq ON maintenance.issue_category (organization_id, code) NULLS NOT DISTINCT;

CREATE TABLE maintenance.issue_type
(
    id                 UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    category_id        UUID        NOT NULL REFERENCES maintenance.issue_category (id),
    owner_scope        TEXT        NOT NULL DEFAULT 'PLATFORM',
    organization_id    UUID,
    code               TEXT        NOT NULL,
    name_ar            TEXT,
    name_en            TEXT,
    name_fr            TEXT,
    asset_required     BOOLEAN     NOT NULL DEFAULT FALSE,
    default_visibility TEXT        NOT NULL DEFAULT 'COMMON',
    asset_family_ids   TEXT        NOT NULL DEFAULT '',
    sort_order         INTEGER     NOT NULL DEFAULT 100,
    active             BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    version            BIGINT      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX issue_type_code_uq ON maintenance.issue_type (organization_id, code) NULLS NOT DISTINCT;

-- Routing (H4) and visibility (H8) of a type on one site; without a row: the manager and the type's visibility.
CREATE TABLE maintenance.issue_type_config
(
    id              UUID PRIMARY KEY,
    organization_id UUID        NOT NULL,
    site_id         UUID        NOT NULL,
    issue_type_id   UUID        NOT NULL REFERENCES maintenance.issue_type (id),
    routing         TEXT        NOT NULL,
    technician_ids  TEXT        NOT NULL DEFAULT '',
    visibility      TEXT        NOT NULL,
    active          BOOLEAN     NOT NULL DEFAULT TRUE,
    updated_at      TIMESTAMPTZ NOT NULL,
    version         BIGINT      NOT NULL DEFAULT 0,
    UNIQUE (site_id, issue_type_id)
);

-- Readable references per organization (T-000123).
CREATE TABLE maintenance.ticket_counter
(
    organization_id UUID PRIMARY KEY,
    last_value      BIGINT NOT NULL
);

CREATE TABLE maintenance.ticket
(
    id                   UUID PRIMARY KEY,
    organization_id      UUID        NOT NULL,
    site_id              UUID        NOT NULL,
    reference            TEXT        NOT NULL,
    nature               TEXT        NOT NULL,
    issue_type_id        UUID        NOT NULL REFERENCES maintenance.issue_type (id),
    building_id          UUID,
    floor                INTEGER,
    outdoor_area_id      UUID,
    unit_id              UUID,
    asset_id             UUID,
    requester_type       TEXT        NOT NULL,
    requester_id         UUID        NOT NULL,
    visibility           TEXT        NOT NULL,
    description          TEXT,
    -- The text typed by the requester, kept as typed (integration and AI later).
    original_text        TEXT,
    workflow_instance_id UUID,
    workflow_version_id  UUID,
    node_code            TEXT        NOT NULL,
    category             TEXT        NOT NULL,
    hold_reason          TEXT,
    status_since         TIMESTAMPTZ NOT NULL,
    preventive_rule_id   UUID,
    created_at           TIMESTAMPTZ NOT NULL,
    last_change_at       TIMESTAMPTZ NOT NULL,
    version              BIGINT      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ticket_reference_uq ON maintenance.ticket (organization_id, reference);
CREATE INDEX ticket_site_idx ON maintenance.ticket (site_id, last_change_at DESC);
CREATE INDEX ticket_requester_idx ON maintenance.ticket (requester_id);
-- I3 (sprint 4): one open ticket per preventive rule, whatever the circuit (architecture page).
CREATE UNIQUE INDEX uq_open_ticket_per_rule ON maintenance.ticket (preventive_rule_id)
    WHERE preventive_rule_id IS NOT NULL AND category NOT IN ('CLOSED', 'CANCELLED');

-- H10: three photos at most, checked by the application.
CREATE TABLE maintenance.ticket_photo
(
    id        UUID PRIMARY KEY,
    ticket_id UUID        NOT NULL REFERENCES maintenance.ticket (id),
    file_id   UUID        NOT NULL,
    added_by  UUID        NOT NULL,
    added_at  TIMESTAMPTZ NOT NULL,
    version   BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX ticket_photo_ticket_idx ON maintenance.ticket_photo (ticket_id);

-- J1 (routing in sprint 3, manual changes in sprint 4): ended, never deleted.
CREATE TABLE maintenance.ticket_assignment
(
    id            UUID PRIMARY KEY,
    ticket_id     UUID        NOT NULL REFERENCES maintenance.ticket (id),
    technician_id UUID        NOT NULL,
    origin        TEXT        NOT NULL,
    added_by_type TEXT        NOT NULL,
    added_by_id   UUID        NOT NULL,
    added_at      TIMESTAMPTZ NOT NULL,
    removed_by    UUID,
    removed_at    TIMESTAMPTZ,
    version       BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX ticket_assignment_technician_idx ON maintenance.ticket_assignment (technician_id) WHERE removed_at IS NULL;
CREATE INDEX ticket_assignment_ticket_idx ON maintenance.ticket_assignment (ticket_id);

-- H9: changes of a ticket other than its steps (those are in the workflow history). Append only.
CREATE TABLE maintenance.ticket_activity
(
    id          UUID PRIMARY KEY,
    ticket_id   UUID        NOT NULL REFERENCES maintenance.ticket (id),
    type        TEXT        NOT NULL,
    actor_type  TEXT        NOT NULL,
    actor_id    UUID        NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    details     TEXT        NOT NULL DEFAULT '{}'
);
CREATE INDEX ticket_activity_ticket_idx ON maintenance.ticket_activity (ticket_id, occurred_at);
REVOKE UPDATE ON maintenance.ticket_activity FROM "${app-role}";
