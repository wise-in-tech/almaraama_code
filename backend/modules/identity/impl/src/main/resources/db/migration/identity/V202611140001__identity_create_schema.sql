-- Module identity: its own schema (CF-3). The application role reads, inserts and updates; it never deletes (M2).
CREATE SCHEMA identity;

GRANT USAGE ON SCHEMA identity TO "${app-role}";
ALTER DEFAULT PRIVILEGES IN SCHEMA identity GRANT SELECT, INSERT, UPDATE ON TABLES TO "${app-role}";
ALTER DEFAULT PRIVILEGES IN SCHEMA identity GRANT USAGE, SELECT ON SEQUENCES TO "${app-role}";
