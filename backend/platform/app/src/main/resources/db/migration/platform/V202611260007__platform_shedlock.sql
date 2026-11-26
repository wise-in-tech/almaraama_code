-- Locks of the scheduled jobs (ShedLock, ADR-036): one row per job, updated by the application role.
CREATE TABLE platform.shedlock
(
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMPTZ  NOT NULL,
    locked_at  TIMESTAMPTZ  NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
GRANT SELECT, INSERT, UPDATE ON platform.shedlock TO "${app-role}";
