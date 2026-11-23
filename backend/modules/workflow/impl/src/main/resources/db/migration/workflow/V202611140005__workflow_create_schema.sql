-- Module workflow: its own schema (CF-3). The application role reads, inserts and updates; it never deletes (M2).
CREATE SCHEMA workflow;

GRANT USAGE ON SCHEMA workflow TO "${app-role}";
ALTER DEFAULT PRIVILEGES IN SCHEMA workflow GRANT SELECT, INSERT, UPDATE ON TABLES TO "${app-role}";
ALTER DEFAULT PRIVILEGES IN SCHEMA workflow GRANT USAGE, SELECT ON SEQUENCES TO "${app-role}";
