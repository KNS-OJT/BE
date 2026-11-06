#!/bin/bash
# Chạy MỘT LẦN khi volume pgdata còn trống (docker-entrypoint-initdb.d), bằng superuser postgres.
# Tạo database p03 và các role tách quyền theo SRS mục 8 / SDD mục 8:
#   p03_owner    – sở hữu schema, chạy file schema (01_schema.sql)
#   p03_app      – backend; quyền chi tiết (GRANT) nằm trong migration
#   p03_readonly – detector của IA, chỉ SELECT
#   p03_backup   – pg_dump
# Mật khẩu lấy từ biến môi trường của container postgres (file .env).
set -euo pipefail

: "${DB_OWNER_PASSWORD:?Thiếu DB_OWNER_PASSWORD}"
: "${DB_APP_PASSWORD:?Thiếu DB_APP_PASSWORD}"
: "${DB_READONLY_PASSWORD:?Thiếu DB_READONLY_PASSWORD}"
: "${DB_BACKUP_PASSWORD:?Thiếu DB_BACKUP_PASSWORD}"
DB_NAME="${DB_NAME:-p03}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  -v owner_pw="$DB_OWNER_PASSWORD" -v app_pw="$DB_APP_PASSWORD" \
  -v ro_pw="$DB_READONLY_PASSWORD" -v bk_pw="$DB_BACKUP_PASSWORD" -v db="$DB_NAME" <<'SQL'
CREATE ROLE p03_owner    LOGIN PASSWORD :'owner_pw';
CREATE ROLE p03_app      LOGIN PASSWORD :'app_pw'  NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE ROLE p03_readonly LOGIN PASSWORD :'ro_pw'   NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE ROLE p03_backup   LOGIN PASSWORD :'bk_pw'   NOSUPERUSER NOCREATEDB NOCREATEROLE;

CREATE DATABASE :"db" OWNER p03_owner ENCODING 'UTF8' TEMPLATE template0;
SQL

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$DB_NAME" <<'SQL'
REVOKE ALL ON DATABASE p03 FROM PUBLIC;
GRANT CONNECT ON DATABASE p03 TO p03_app, p03_readonly, p03_backup;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
ALTER SCHEMA public OWNER TO p03_owner;
SQL
