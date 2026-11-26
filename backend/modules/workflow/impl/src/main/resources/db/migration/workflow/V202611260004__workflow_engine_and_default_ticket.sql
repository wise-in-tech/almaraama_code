-- Sprint 3 (H5, H6, H7, H9, ADR-036): circuits as data, executed by the in-house engine. Schema of the architecture
-- page (section 08) with the v3.4 adjustments: a passage names a permission instead of roles; steps have an English
-- label; a binding is made per organization and carries the client's options (read at each use); the instance keeps
-- its organization and site, the token its step code and category. No PostgreSQL enum and no CHECK on statuses.
-- JSON values (guards, actions, configs, settings, layout, payload) are stored as text, parsed by the application
-- (CF-8: the domain uses standard JPA only); a later migration may turn them into jsonb with USING col::jsonb.
--
-- Format for loading a new circuit by hand (decision of 20 November): one workflow row (owner_scope PLATFORM,
-- subject_type TICKET, code, name), one workflow_version row in status DRAFT, its nodes (code, node_type 'STATE',
-- category among OPEN, IN_PROGRESS, ON_HOLD, RESOLVED, CLOSED, CANCELLED, labels, config '{}' or
-- '{"autoClose":{"transition":"auto_close"}}') and its transitions (code, from and to node, permission such as
-- 'ticket:update-status' or empty, guards as a JSON array of {"code": ..., "config": {...}}, sort_order). Then publish
-- it from the back-office, which checks it.

CREATE TABLE workflow.workflow
(
    id                    UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    owner_scope           TEXT        NOT NULL,
    owner_organization_id UUID,
    owner_site_id         UUID,
    subject_type          TEXT        NOT NULL,
    code                  TEXT        NOT NULL,
    name                  TEXT        NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    version               BIGINT      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX workflow_code_uq ON workflow.workflow (owner_organization_id, owner_site_id, code) NULLS NOT DISTINCT;

CREATE TABLE workflow.workflow_version
(
    id           UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    workflow_id  UUID        NOT NULL REFERENCES workflow.workflow (id),
    number       INTEGER     NOT NULL,
    status       TEXT        NOT NULL DEFAULT 'DRAFT',
    published_at TIMESTAMPTZ,
    layout       TEXT        NOT NULL DEFAULT '{}',
    version      BIGINT      NOT NULL DEFAULT 0,
    UNIQUE (workflow_id, number)
);

CREATE TABLE workflow.workflow_node
(
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    version_id UUID NOT NULL REFERENCES workflow.workflow_version (id),
    code       TEXT NOT NULL,
    node_type  TEXT NOT NULL DEFAULT 'STATE',
    category   TEXT NOT NULL,
    label_ar   TEXT,
    label_en   TEXT,
    label_fr   TEXT,
    config     TEXT NOT NULL DEFAULT '{}',
    UNIQUE (version_id, code)
);

CREATE TABLE workflow.workflow_transition
(
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    version_id   UUID    NOT NULL REFERENCES workflow.workflow_version (id),
    code         TEXT    NOT NULL,
    from_node_id UUID    NOT NULL REFERENCES workflow.workflow_node (id),
    to_node_id   UUID    NOT NULL REFERENCES workflow.workflow_node (id),
    permission   TEXT,
    guards       TEXT    NOT NULL DEFAULT '[]',
    actions      TEXT    NOT NULL DEFAULT '[]',
    sort_order   INTEGER NOT NULL DEFAULT 100,
    UNIQUE (version_id, code, from_node_id)
);

-- Which circuit applies where. P1: one row per organization (site_id empty); the most precise binding wins.
CREATE TABLE workflow.workflow_binding
(
    id              UUID PRIMARY KEY,
    organization_id UUID        NOT NULL,
    site_id         UUID,
    subject_type    TEXT        NOT NULL,
    issue_type_id   UUID,
    building_use    TEXT,
    workflow_id     UUID        NOT NULL REFERENCES workflow.workflow (id),
    settings        TEXT        NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    version         BIGINT      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX workflow_binding_uq
    ON workflow.workflow_binding (organization_id, site_id, subject_type, issue_type_id, building_use) NULLS NOT DISTINCT;

CREATE TABLE workflow.workflow_instance
(
    id              UUID PRIMARY KEY,
    version_id      UUID        NOT NULL REFERENCES workflow.workflow_version (id),
    subject_type    TEXT        NOT NULL,
    subject_id      UUID        NOT NULL,
    organization_id UUID        NOT NULL,
    site_id         UUID        NOT NULL,
    started_at      TIMESTAMPTZ NOT NULL,
    ended_at        TIMESTAMPTZ,
    version         BIGINT      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX workflow_instance_subject_uq ON workflow.workflow_instance (subject_type, subject_id);

CREATE TABLE workflow.workflow_token
(
    id          UUID PRIMARY KEY,
    instance_id UUID        NOT NULL REFERENCES workflow.workflow_instance (id),
    node_id     UUID        NOT NULL REFERENCES workflow.workflow_node (id),
    node_code   TEXT        NOT NULL,
    category    TEXT        NOT NULL,
    active      BOOLEAN     NOT NULL,
    entered_at  TIMESTAMPTZ NOT NULL,
    left_at     TIMESTAMPTZ,
    version     BIGINT      NOT NULL DEFAULT 0
);
-- P1: one active token per instance.
CREATE UNIQUE INDEX workflow_token_active_uq ON workflow.workflow_token (instance_id) WHERE active;
CREATE INDEX workflow_token_node_idx ON workflow.workflow_token (node_id, entered_at) WHERE active;

-- Append only (H9): who did what, when, with which input.
CREATE TABLE workflow.workflow_event
(
    id                 UUID PRIMARY KEY,
    instance_id        UUID        NOT NULL REFERENCES workflow.workflow_instance (id),
    transition_id      UUID REFERENCES workflow.workflow_transition (id),
    transition_code    TEXT,
    from_node_code     TEXT,
    to_node_code       TEXT        NOT NULL,
    actor_type         TEXT        NOT NULL,
    actor_id           UUID        NOT NULL,
    support_session_id UUID,
    occurred_at        TIMESTAMPTZ NOT NULL,
    payload            TEXT        NOT NULL DEFAULT '{}'
);
CREATE INDEX workflow_event_instance_idx ON workflow.workflow_event (instance_id, occurred_at);
REVOKE UPDATE ON workflow.workflow_event FROM "${app-role}";

-- ---- default-ticket, version 1: the circuit of the specification (H5, H6, H7), published --------------------------
INSERT INTO workflow.workflow (id, owner_scope, subject_type, code, name)
VALUES ('0194a000-0000-7000-8000-000000000001', 'PLATFORM', 'TICKET', 'default-ticket',
        'Circuit des tickets de la spécification');
INSERT INTO workflow.workflow_version (id, workflow_id, number, status, published_at)
VALUES ('0194a000-0000-7000-8000-000000000002', '0194a000-0000-7000-8000-000000000001', 1, 'PUBLISHED', now());

INSERT INTO workflow.workflow_node (id, version_id, code, category, label_ar, label_en, label_fr, config)
VALUES ('0194a000-0000-7000-8000-000000000011', '0194a000-0000-7000-8000-000000000002', 'REQUESTED', 'OPEN',
        'مطلوب', 'Requested', 'Demandé', '{}'),
       ('0194a000-0000-7000-8000-000000000012', '0194a000-0000-7000-8000-000000000002', 'IN_PROGRESS', 'IN_PROGRESS',
        'قيد المعالجة', 'In progress', 'En cours', '{}'),
       ('0194a000-0000-7000-8000-000000000013', '0194a000-0000-7000-8000-000000000002', 'ON_HOLD', 'ON_HOLD',
        'في الانتظار', 'On hold', 'En attente', '{}'),
       ('0194a000-0000-7000-8000-000000000014', '0194a000-0000-7000-8000-000000000002', 'RESOLVED', 'RESOLVED',
        'تمت المعالجة', 'Resolved', 'Traité', '{"autoClose":{"transition":"auto_close"}}'),
       ('0194a000-0000-7000-8000-000000000015', '0194a000-0000-7000-8000-000000000002', 'CLOSED', 'CLOSED',
        'مغلق', 'Closed', 'Clôturé', '{}'),
       ('0194a000-0000-7000-8000-000000000016', '0194a000-0000-7000-8000-000000000002', 'CANCELLED', 'CANCELLED',
        'ملغى', 'Cancelled', 'Annulé', '{}');

INSERT INTO workflow.workflow_transition (version_id, code, from_node_id, to_node_id, permission, guards, sort_order)
VALUES
    -- take_charge, put_on_hold, resume, resolve (H5)
    ('0194a000-0000-7000-8000-000000000002', 'take_charge', '0194a000-0000-7000-8000-000000000011',
     '0194a000-0000-7000-8000-000000000012', 'ticket:update-status', '[]', 10),
    ('0194a000-0000-7000-8000-000000000002', 'put_on_hold', '0194a000-0000-7000-8000-000000000011',
     '0194a000-0000-7000-8000-000000000013', 'ticket:update-status',
     '[{"code":"ReasonRequired","config":{"reasons":["PART","QUOTE","ACCESS","OTHER"],"commentRequiredFor":["OTHER"]}}]', 20),
    ('0194a000-0000-7000-8000-000000000002', 'put_on_hold', '0194a000-0000-7000-8000-000000000012',
     '0194a000-0000-7000-8000-000000000013', 'ticket:update-status',
     '[{"code":"ReasonRequired","config":{"reasons":["PART","QUOTE","ACCESS","OTHER"],"commentRequiredFor":["OTHER"]}}]', 20),
    ('0194a000-0000-7000-8000-000000000002', 'resume', '0194a000-0000-7000-8000-000000000013',
     '0194a000-0000-7000-8000-000000000012', 'ticket:update-status', '[]', 30),
    ('0194a000-0000-7000-8000-000000000002', 'resolve', '0194a000-0000-7000-8000-000000000012',
     '0194a000-0000-7000-8000-000000000014', 'ticket:update-status', '[]', 40),
    -- requester validation (H5 option) and reopening (H5)
    ('0194a000-0000-7000-8000-000000000002', 'confirm_resolution', '0194a000-0000-7000-8000-000000000014',
     '0194a000-0000-7000-8000-000000000014', NULL,
     '[{"code":"IsRequester"},{"code":"OptionEnabled","config":{"option":"requesterValidation"}}]', 50),
    ('0194a000-0000-7000-8000-000000000002', 'reopen', '0194a000-0000-7000-8000-000000000014',
     '0194a000-0000-7000-8000-000000000012', 'ticket:reopen', '[]', 60),
    ('0194a000-0000-7000-8000-000000000002', 'reopen_by_requester', '0194a000-0000-7000-8000-000000000014',
     '0194a000-0000-7000-8000-000000000012', NULL, '[{"code":"IsRequester"}]', 61),
    -- closing (H7, C6) by a person or automatically (H5 option)
    ('0194a000-0000-7000-8000-000000000002', 'close', '0194a000-0000-7000-8000-000000000014',
     '0194a000-0000-7000-8000-000000000015', 'ticket:close', '[{"code":"RequesterConfirmation"}]', 70),
    ('0194a000-0000-7000-8000-000000000002', 'auto_close', '0194a000-0000-7000-8000-000000000014',
     '0194a000-0000-7000-8000-000000000015', NULL, '[{"code":"SystemOnly"}]', 71),
    -- cancelling (H6)
    ('0194a000-0000-7000-8000-000000000002', 'cancel_by_requester', '0194a000-0000-7000-8000-000000000011',
     '0194a000-0000-7000-8000-000000000016', NULL, '[{"code":"IsRequester"}]', 80),
    ('0194a000-0000-7000-8000-000000000002', 'cancel_by_manager', '0194a000-0000-7000-8000-000000000011',
     '0194a000-0000-7000-8000-000000000016', 'ticket:cancel', '[{"code":"CommentRequired"}]', 90),
    ('0194a000-0000-7000-8000-000000000002', 'cancel_by_manager', '0194a000-0000-7000-8000-000000000012',
     '0194a000-0000-7000-8000-000000000016', 'ticket:cancel', '[{"code":"CommentRequired"}]', 90),
    ('0194a000-0000-7000-8000-000000000002', 'cancel_by_manager', '0194a000-0000-7000-8000-000000000013',
     '0194a000-0000-7000-8000-000000000016', 'ticket:cancel', '[{"code":"CommentRequired"}]', 90),
    ('0194a000-0000-7000-8000-000000000002', 'cancel_by_manager', '0194a000-0000-7000-8000-000000000014',
     '0194a000-0000-7000-8000-000000000016', 'ticket:cancel', '[{"code":"CommentRequired"}]', 90);
