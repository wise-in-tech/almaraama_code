-- Module sites: its own schema (CF-3). The application role reads, inserts and updates; it never deletes (M2).
CREATE SCHEMA sites;

GRANT USAGE ON SCHEMA sites TO "${app-role}";
ALTER DEFAULT PRIVILEGES IN SCHEMA sites GRANT SELECT, INSERT, UPDATE ON TABLES TO "${app-role}";
ALTER DEFAULT PRIVILEGES IN SCHEMA sites GRANT USAGE, SELECT ON SEQUENCES TO "${app-role}";
