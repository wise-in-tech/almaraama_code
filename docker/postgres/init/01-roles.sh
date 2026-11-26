#!/bin/sh
# Creates the database roles on the first start of an empty PostgreSQL volume (security baseline, M2).
#   - migration role: owns the database and the module schemas; used only by Flyway.
#   - application role: used by the running platform; gets SELECT/INSERT/UPDATE through Flyway grants, no DELETE.
# Passwords come from the environment and are passed as psql variables, never pasted into SQL text.
set -eu

: "${ALMARAAMA_DB_MIGRATOR_USER:?}" "${ALMARAAMA_DB_MIGRATOR_PASSWORD:?}"
: "${ALMARAAMA_DB_APP_USER:?}" "${ALMARAAMA_DB_APP_PASSWORD:?}"

psql -v ON_ERROR_STOP=1 \
     --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
     -v migrator="$ALMARAAMA_DB_MIGRATOR_USER" -v migrator_password="$ALMARAAMA_DB_MIGRATOR_PASSWORD" \
     -v app="$ALMARAAMA_DB_APP_USER" -v app_password="$ALMARAAMA_DB_APP_PASSWORD" \
     -v db="$POSTGRES_DB" <<'SQL'
CREATE ROLE :"migrator" LOGIN PASSWORD :'migrator_password';
CREATE ROLE :"app" LOGIN PASSWORD :'app_password';

ALTER DATABASE :"db" OWNER TO :"migrator";
REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"db" TO :"app";

-- Nobody creates objects in "public"; each module has its own schema (CF-3).
REVOKE ALL ON SCHEMA public FROM PUBLIC;
SQL
