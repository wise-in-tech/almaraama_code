-- Module audit: its own schema (CF-3). The application role reads, inserts and updates; it never deletes (M2).
CREATE SCHEMA audit;

GRANT USAGE ON SCHEMA audit TO "${app-role}";
ALTER DEFAULT PRIVILEGES IN SCHEMA audit GRANT SELECT, INSERT, UPDATE ON TABLES TO "${app-role}";
ALTER DEFAULT PRIVILEGES IN SCHEMA audit GRANT USAGE, SELECT ON SEQUENCES TO "${app-role}";
