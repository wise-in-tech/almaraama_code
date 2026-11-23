-- Module files: its own schema (CF-3). The application role reads, inserts and updates; it never deletes (M2).
CREATE SCHEMA files;

GRANT USAGE ON SCHEMA files TO "${app-role}";
ALTER DEFAULT PRIVILEGES IN SCHEMA files GRANT SELECT, INSERT, UPDATE ON TABLES TO "${app-role}";
ALTER DEFAULT PRIVILEGES IN SCHEMA files GRANT USAGE, SELECT ON SEQUENCES TO "${app-role}";
