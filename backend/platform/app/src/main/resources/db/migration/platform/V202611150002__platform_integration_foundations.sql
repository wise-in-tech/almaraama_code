-- Integration foundations prepared in P1 (architecture section 14), used by the future integration module.
-- They live in schema "platform" until that module exists; it will take them over without changing their shape.

-- Link between one of our public IDs and the ID of the same thing in an external system (SAP notification number,
-- ServiceNow sys_id, IFC GlobalId...). No external ID is ever stored in a business table.
CREATE TABLE platform.external_reference
(
    id          UUID PRIMARY KEY,
    entity_type TEXT        NOT NULL,
    entity_id   UUID        NOT NULL,
    system      TEXT        NOT NULL,
    external_id TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    created_by  UUID        NOT NULL,
    UNIQUE (system, entity_type, external_id)
);
CREATE INDEX external_reference_entity_idx ON platform.external_reference (entity_type, entity_id);

-- A command replayed with the same key (offline queue, partner retry) is applied once: the stored response is
-- returned instead. Expired keys will be purged by a reviewed job with its own explicit grant (M2).
CREATE TABLE platform.idempotency_key
(
    actor_id        UUID        NOT NULL,
    key             TEXT        NOT NULL,
    request_hash    TEXT        NOT NULL,
    response_status INTEGER,
    response_body   JSONB,
    created_at      TIMESTAMPTZ NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (actor_id, key)
);
