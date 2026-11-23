-- Module membership: its own schema (CF-3). The application role reads, inserts and updates; it never deletes (M2).
CREATE SCHEMA membership;

GRANT USAGE ON SCHEMA membership TO "${app-role}";
ALTER DEFAULT PRIVILEGES IN SCHEMA membership GRANT SELECT, INSERT, UPDATE ON TABLES TO "${app-role}";
ALTER DEFAULT PRIVILEGES IN SCHEMA membership GRANT USAGE, SELECT ON SEQUENCES TO "${app-role}";
