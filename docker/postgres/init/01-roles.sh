#!/bin/bash
# Runs only on an empty data directory. Creates the two least-privilege roles and
# DB-level settings; it deliberately creates NO business tables. Flyway owns those.
set -euo pipefail

psql -v ON_ERROR_STOP=1 \
  -v migrator_pw="$POSTGRES_MIGRATOR_PASSWORD" \
  -v app_pw="$POSTGRES_APP_PASSWORD" \
  -v dbname="$POSTGRES_DB" \
  --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'EOSQL'
CREATE ROLE vision_migrator LOGIN PASSWORD :'migrator_pw'
  NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE ROLE vision_app LOGIN PASSWORD :'app_pw'
  NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;

ALTER DATABASE :"dbname" OWNER TO vision_migrator;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO vision_app;

CREATE EXTENSION IF NOT EXISTS pgcrypto;
ALTER DATABASE :"dbname" SET timezone = 'Asia/Shanghai';
EOSQL
