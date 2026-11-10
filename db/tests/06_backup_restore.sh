#!/usr/bin/env bash
# =====================================================================
# P03 | 06_backup_restore.sh - IA-IN08 Secure Database Backup
#   backup  : pg_dump (custom format) + age encryption + SHA-256 checksum, file mode 600
#   restore : verify checksum, decrypt, restore into a NEW database, then verify
#             row counts, ledger/audit hash chains and stock reconciliation
#
# Usage:
#   AGE_RECIPIENT=age1... PGHOST=localhost PGUSER=p03_backup PGDATABASE=p03 ./06_backup_restore.sh backup
#   AGE_IDENTITY_FILE=/secure/key.txt PGHOST=localhost PGUSER=postgres ./06_backup_restore.sh restore backups/p03_YYYYmmdd_HHMMSS.dump.age p03_restore_test
# Create the key with age-keygen. Keep the private key outside the lab machine.
# Schedule (cron, every day 01:30):
#   30 1 * * * cd /opt/p03 && ./06_backup_restore.sh backup >> backups/backup.log 2>&1
# Keep the backups folder outside the web root, owned by the backup OS user, chmod 700.
# =====================================================================
set -euo pipefail
BACKUP_DIR="${BACKUP_DIR:-./backups}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
umask 077

case "${1:-}" in
  backup)
    mkdir -p "$BACKUP_DIR"; chmod 700 "$BACKUP_DIR"
    : "${AGE_RECIPIENT:?AGE_RECIPIENT (age public key) is required}"
    FILE="$BACKUP_DIR/${PGDATABASE:-p03}_$(date +%Y%m%d_%H%M%S).dump.age"
    pg_dump --format=custom --no-owner --no-privileges "${PGDATABASE:-p03}" | age --recipient "$AGE_RECIPIENT" --output "$FILE"
    chmod 600 "$FILE"
    sha256sum "$FILE" > "$FILE.sha256"
    chmod 600 "$FILE.sha256"
    echo "$(date -Is) backup OK: $FILE ($(du -h "$FILE" | cut -f1))"
    find "$BACKUP_DIR" -name '*.dump*' -mtime +"$RETENTION_DAYS" -delete
    ;;
  restore)
    : "${AGE_IDENTITY_FILE:?AGE_IDENTITY_FILE (age private key file) is required}"
    FILE="${2:?encrypted dump file required}"; TARGET="${3:?target database name required}"
    sha256sum --check "$FILE.sha256"
    PLAIN="$(mktemp)"; trap 'rm -f "$PLAIN"' EXIT
    age --decrypt --identity "$AGE_IDENTITY_FILE" --output "$PLAIN" "$FILE"
    createdb "$TARGET"
    pg_restore --no-owner --exit-on-error --dbname="$TARGET" "$PLAIN"
    echo "--- verification on $TARGET ---"
    psql -X -d "$TARGET" -v ON_ERROR_STOP=1 <<'SQL'
SELECT 'products' AS tbl, count(*) FROM products
UNION ALL SELECT 'purchase_requests', count(*) FROM purchase_requests
UNION ALL SELECT 'stock_transactions', count(*) FROM stock_transactions
UNION ALL SELECT 'audit_logs', count(*) FROM audit_logs;
SELECT CASE WHEN NOT EXISTS (SELECT 1 FROM fn_verify_stock_ledger()) THEN 'PASS' ELSE 'FAIL' END AS ledger_chain,
       CASE WHEN NOT EXISTS (SELECT 1 FROM fn_verify_audit_log())     THEN 'PASS' ELSE 'FAIL' END AS audit_chain,
       CASE WHEN NOT EXISTS (SELECT 1 FROM v_stock_reconciliation WHERE difference <> 0) THEN 'PASS' ELSE 'FAIL' END AS reconciliation;
SQL
    ;;
  *)
    echo "usage: $0 backup | restore <file.dump.age> <new_db_name>"; exit 1 ;;
esac
