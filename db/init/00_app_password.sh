#!/bin/bash
# Chạy đầu tiên khi container postgres khởi tạo volume trống (docker-entrypoint-initdb.d, theo thứ tự tên file).
# Tạo sẵn các role với mật khẩu lấy từ .env, để 02_roles.sql (IF NOT EXISTS) không dùng mật khẩu mẫu CHANGE_ME.
set -euo pipefail
: "${DB_APP_PASSWORD:?Thiếu DB_APP_PASSWORD}"
: "${DB_READONLY_PASSWORD:?Thiếu DB_READONLY_PASSWORD}"
: "${DB_BACKUP_PASSWORD:?Thiếu DB_BACKUP_PASSWORD}"
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v app_pw="$DB_APP_PASSWORD" -v ro_pw="$DB_READONLY_PASSWORD" -v bk_pw="$DB_BACKUP_PASSWORD" <<'SQL'
CREATE ROLE p03_app      LOGIN PASSWORD :'app_pw' NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE ROLE p03_readonly LOGIN PASSWORD :'ro_pw'  NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE ROLE p03_backup   LOGIN PASSWORD :'bk_pw'  NOSUPERUSER NOCREATEDB NOCREATEROLE;
SQL
