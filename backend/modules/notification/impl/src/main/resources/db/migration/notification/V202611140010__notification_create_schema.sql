-- Module notification: its own schema (CF-3). The application role reads, inserts and updates; it never deletes (M2).
CREATE SCHEMA notification;

GRANT USAGE ON SCHEMA notification TO "${app-role}";
ALTER DEFAULT PRIVILEGES IN SCHEMA notification GRANT SELECT, INSERT, UPDATE ON TABLES TO "${app-role}";
ALTER DEFAULT PRIVILEGES IN SCHEMA notification GRANT USAGE, SELECT ON SEQUENCES TO "${app-role}";
