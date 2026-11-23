-- Platform baseline (ADR-003, ADR-023, CF-6, M2).
--
-- Schema "platform" holds what belongs to no bounded context: the Flyway history and the Spring Modulith outbox.
-- Flyway creates it (spring.flyway.default-schema). Each module creates its own schema in its own migrations,
-- shipped inside its impl artifact under db/migration/<module>/.
--
-- ${app-role} is the application DB role, injected by Flyway (spring.flyway.placeholders.app-role).

-- 1. Rights of the application role: no DELETE, nothing is deleted (M2) ------------------------------------------

GRANT USAGE ON SCHEMA platform TO "${app-role}";
ALTER DEFAULT PRIVILEGES IN SCHEMA platform GRANT SELECT, INSERT, UPDATE ON TABLES TO "${app-role}";
ALTER DEFAULT PRIVILEGES IN SCHEMA platform GRANT USAGE, SELECT ON SEQUENCES TO "${app-role}";

-- 2. Event publication registry (outbox) ----------------------------------------------------------------------------

CREATE TABLE platform.event_publication
(
    id                     UUID                     NOT NULL,
    listener_id            TEXT                     NOT NULL,
    event_type             TEXT                     NOT NULL,
    serialized_event       TEXT                     NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    status                 TEXT,
    completion_attempts    INT,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);
CREATE INDEX event_publication_serialized_event_hash_idx ON platform.event_publication USING hash (serialized_event);
CREATE INDEX event_publication_by_completion_date_idx ON platform.event_publication (completion_date);

CREATE TABLE platform.event_publication_archive
(
    id                     UUID                     NOT NULL,
    listener_id            TEXT                     NOT NULL,
    event_type             TEXT                     NOT NULL,
    serialized_event       TEXT                     NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    status                 TEXT,
    completion_attempts    INT,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);
CREATE INDEX event_publication_archive_serialized_event_hash_idx ON platform.event_publication_archive USING hash (serialized_event);
CREATE INDEX event_publication_archive_by_completion_date_idx ON platform.event_publication_archive (completion_date);
