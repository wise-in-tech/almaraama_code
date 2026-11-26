-- Database and roles of almaraama on a PostgreSQL installed without Docker (same result as init/01-roles.sh).
-- Run ONCE as a PostgreSQL superuser, passwords given as psql variables (never written in this file):
--
--   psql -U postgres -v ON_ERROR_STOP=1 \
--        -v migrator=almaraama_migrator -v migrator_password='...' \
--        -v app=almaraama_app -v app_password='...' \
--        -f docker/postgres/manual-setup.sql
--
-- Two roles (security baseline, M2):
--   - migration role: owns the database and every module schema; used only by Flyway at application start-up.
--   - application role: used by the running platform; receives SELECT/INSERT/UPDATE from the Flyway migrations,
--     never DELETE on business tables.
-- The tables themselves are NOT created here: Flyway creates and updates them when the application starts.

CREATE ROLE :"migrator" LOGIN PASSWORD :'migrator_password';
CREATE ROLE :"app" LOGIN PASSWORD :'app_password';

CREATE DATABASE almaraama OWNER :"migrator" ENCODING 'UTF8' TEMPLATE template0;
REVOKE ALL ON DATABASE almaraama FROM PUBLIC;
GRANT CONNECT ON DATABASE almaraama TO :"app";

\connect almaraama
-- Nobody creates objects in "public"; each module has its own schema (CF-3).
REVOKE ALL ON SCHEMA public FROM PUBLIC;
