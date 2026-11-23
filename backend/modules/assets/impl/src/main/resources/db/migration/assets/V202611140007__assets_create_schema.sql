-- Module assets: its own schema (CF-3). The application role reads, inserts and updates; it never deletes (M2).
CREATE SCHEMA assets;

GRANT USAGE ON SCHEMA assets TO "${app-role}";
ALTER DEFAULT PRIVILEGES IN SCHEMA assets GRANT SELECT, INSERT, UPDATE ON TABLES TO "${app-role}";
ALTER DEFAULT PRIVILEGES IN SCHEMA assets GRANT USAGE, SELECT ON SEQUENCES TO "${app-role}";
