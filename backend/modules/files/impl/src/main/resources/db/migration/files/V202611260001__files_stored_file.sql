-- Sprint 3 (ADR-038): stored files. The content lives outside the database, in the FileStorage (local disk in P1);
-- this table holds what was checked (detected type, size, checksum) and who may attach the file. Never deleted (M2):
-- a file never attached stays here; a purge of orphans is an operations decision for later.
CREATE TABLE files.stored_file
(
    id              UUID PRIMARY KEY,
    purpose         TEXT        NOT NULL,
    content_type    TEXT        NOT NULL,
    size_bytes      BIGINT      NOT NULL,
    sha256          TEXT        NOT NULL,
    storage_key     TEXT        NOT NULL UNIQUE,
    original_name   TEXT,
    uploaded_by_type TEXT       NOT NULL,
    uploaded_by_id  UUID        NOT NULL,
    uploaded_at     TIMESTAMPTZ NOT NULL,
    owner_module    TEXT,
    owner_id        UUID,
    attached_at     TIMESTAMPTZ,
    version         BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX stored_file_uploader_idx ON files.stored_file (uploaded_by_id, uploaded_at);
CREATE INDEX stored_file_owner_idx ON files.stored_file (owner_module, owner_id);
