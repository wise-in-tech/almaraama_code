-- Module maintenance: its own schema (CF-3). The application role reads, inserts and updates; it never deletes (M2).
CREATE SCHEMA maintenance;

GRANT USAGE ON SCHEMA maintenance TO "${app-role}";
ALTER DEFAULT PRIVILEGES IN SCHEMA maintenance GRANT SELECT, INSERT, UPDATE ON TABLES TO "${app-role}";
ALTER DEFAULT PRIVILEGES IN SCHEMA maintenance GRANT USAGE, SELECT ON SEQUENCES TO "${app-role}";
