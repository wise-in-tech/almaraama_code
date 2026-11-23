-- Module preventive: its own schema (CF-3). The application role reads, inserts and updates; it never deletes (M2).
CREATE SCHEMA preventive;

GRANT USAGE ON SCHEMA preventive TO "${app-role}";
ALTER DEFAULT PRIVILEGES IN SCHEMA preventive GRANT SELECT, INSERT, UPDATE ON TABLES TO "${app-role}";
ALTER DEFAULT PRIVILEGES IN SCHEMA preventive GRANT USAGE, SELECT ON SEQUENCES TO "${app-role}";
