#!/usr/bin/env bash
# =====================================================================
# P03 | 05_concurrency_test.sh - SEC-TC-303
# Two stock issues for the same product are confirmed at the same time.
# Stock = 30, each issue asks for 20. Exactly one must succeed; stock must
# end at 10 (never negative) and the ledger must still verify.
#
# Usage:  PGHOST=localhost PGUSER=postgres PGDATABASE=p03 ./05_concurrency_test.sh
# Needs:  01_schema.sql + 03_seed.sql already loaded.
# =====================================================================
set -u
PSQL="psql -X -q -t -A -v ON_ERROR_STOP=1"
SKU="IT-HDMI-003"

uid() { $PSQL -c "SELECT user_id FROM users WHERE username = '$1'"; }
REQ1=$(uid requester01); REQ2=$(uid requester02); WH1=$(uid warehouse01); WH2=$(uid warehouse02)

make_issue() {   # $1 = requester id, $2 = tag
  $PSQL <<SQL
BEGIN;
SET LOCAL app.current_user_id = '$1';
WITH si AS (INSERT INTO stock_issues (requested_by, reason) VALUES ($1, '$2') RETURNING stock_issue_id)
INSERT INTO stock_issue_items (stock_issue_id, product_id, quantity)
SELECT stock_issue_id, (SELECT product_id FROM products WHERE sku = '$SKU'), 20 FROM si;
COMMIT;
SQL
}

BEFORE=$($PSQL -c "SELECT quantity FROM stock_balances JOIN products USING (product_id) WHERE sku = '$SKU'")
echo "Stock of $SKU before: $BEFORE"
make_issue "$REQ1" "SEC-TC-303-A"
make_issue "$REQ2" "SEC-TC-303-B"

confirm() {      # $1 = warehouse user id, $2 = tag ; holds the transaction 2s to force overlap
  $PSQL <<SQL 2>&1
BEGIN;
SET LOCAL app.current_user_id = '$1';
UPDATE stock_issues SET status = 'CONFIRMED', confirmed_by = $1 WHERE reason = '$2';
SELECT pg_sleep(2);
COMMIT;
SQL
}

confirm "$WH1" "SEC-TC-303-A" > /tmp/sec303_a.log &
sleep 0.3
confirm "$WH2" "SEC-TC-303-B" > /tmp/sec303_b.log &
wait

echo "Session A: $(grep -o 'ERROR.*' /tmp/sec303_a.log || echo committed)"
echo "Session B: $(grep -o 'ERROR.*' /tmp/sec303_b.log || echo committed)"

AFTER=$($PSQL -c "SELECT quantity FROM stock_balances JOIN products USING (product_id) WHERE sku = '$SKU'")
CONFIRMED=$($PSQL -c "SELECT count(*) FROM stock_issues WHERE reason LIKE 'SEC-TC-303-%' AND status = 'CONFIRMED'")
CHAIN=$($PSQL -c "SELECT count(*) FROM fn_verify_stock_ledger()")
RECON=$($PSQL -c "SELECT count(*) FROM v_stock_reconciliation WHERE difference <> 0")

echo "Stock after: $AFTER | confirmed issues: $CONFIRMED | ledger problems: $CHAIN | balance mismatches: $RECON"
if [ "$CONFIRMED" = "1" ] && [ "$CHAIN" = "0" ] && [ "$RECON" = "0" ] \
   && awk "BEGIN{exit !($AFTER == $BEFORE - 20 && $AFTER >= 0)}"; then
  echo "SEC-TC-303: PASS"
else
  echo "SEC-TC-303: FAIL"; exit 1
fi
