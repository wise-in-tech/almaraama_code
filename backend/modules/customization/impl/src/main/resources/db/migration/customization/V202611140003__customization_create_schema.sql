-- Module customization: its own schema (CF-3). The application role reads, inserts and updates; it never deletes (M2).
CREATE SCHEMA customization;

GRANT USAGE ON SCHEMA customization TO "${app-role}";
ALTER DEFAULT PRIVILEGES IN SCHEMA customization GRANT SELECT, INSERT, UPDATE ON TABLES TO "${app-role}";
ALTER DEFAULT PRIVILEGES IN SCHEMA customization GRANT USAGE, SELECT ON SEQUENCES TO "${app-role}";
