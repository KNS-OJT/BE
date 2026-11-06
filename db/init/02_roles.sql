-- =====================================================================
-- P03 | 02_roles.sql - least-privilege database accounts (IA-IN08, NFR01)
-- Run as the database owner AFTER 01_schema.sql.
-- Replace the passwords (or load them from Docker secrets / .env, never commit them).
-- =====================================================================

-- p03_app       : account used by the backend (Spring Boot / ASP.NET)
-- p03_readonly  : reporting and IA verification (read only)
-- p03_backup    : pg_dump only
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'p03_app') THEN
    CREATE ROLE p03_app LOGIN PASSWORD 'CHANGE_ME_app' NOSUPERUSER NOCREATEDB NOCREATEROLE;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'p03_readonly') THEN
    CREATE ROLE p03_readonly LOGIN PASSWORD 'CHANGE_ME_readonly' NOSUPERUSER NOCREATEDB NOCREATEROLE;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'p03_backup') THEN
    CREATE ROLE p03_backup LOGIN PASSWORD 'CHANGE_ME_backup' NOSUPERUSER NOCREATEDB NOCREATEROLE;
  END IF;
END $$;

-- Nobody but the owner may create objects
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO p03_app, p03_readonly, p03_backup;

-- ---------- p03_app ----------
GRANT SELECT ON ALL TABLES IN SCHEMA public TO p03_app;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO p03_app;

-- master data and documents: create and update, never delete
GRANT INSERT, UPDATE ON users, categories, products, suppliers,
                        purchase_requests, purchase_orders, goods_receipts,
                        stock_issues, stock_adjustments TO p03_app;

-- line items: may be deleted while the document is still DRAFT (enforced by triggers)
GRANT INSERT, UPDATE, DELETE ON purchase_request_items, purchase_order_items, goods_receipt_items,
                                stock_issue_items, stock_adjustment_items TO p03_app;

-- stock balance: only the minimum level is editable; quantity changes only through posting
GRANT UPDATE (minimum_stock) ON stock_balances TO p03_app;

-- stock_transactions and audit_logs: SELECT only. Rows are written by SECURITY DEFINER
-- posting/audit functions, so the backend cannot insert, edit or delete them (IA-IN06).
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON stock_transactions, audit_logs FROM p03_app;
REVOKE TRUNCATE ON ALL TABLES IN SCHEMA public FROM p03_app;

-- Functions are executable by PUBLIC by default. fn_post_stock runs only through the
-- SECURITY DEFINER posting triggers, so the backend must not be able to call it directly.
REVOKE EXECUTE ON FUNCTION fn_post_stock(bigint, stock_transaction_type, numeric, bigint, bigint, bigint, bigint, text) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION fn_log_security_event(varchar, varchar, bigint, varchar, bigint, text, jsonb) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION fn_log_security_event(varchar, varchar, bigint, varchar, bigint, text, jsonb) TO p03_app;

-- ---------- p03_readonly ----------
GRANT SELECT ON ALL TABLES IN SCHEMA public TO p03_readonly;
GRANT EXECUTE ON FUNCTION fn_verify_stock_ledger(), fn_verify_audit_log() TO p03_readonly;
-- password hashes are not needed for reporting
REVOKE SELECT ON users FROM p03_readonly;
GRANT SELECT (user_id, username, full_name, email, role_id, is_active, last_login_at, created_at)
  ON users TO p03_readonly;

-- ---------- p03_backup ----------
GRANT SELECT ON ALL TABLES IN SCHEMA public TO p03_backup;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO p03_backup;

-- Brute force / connection limits for the app account
ALTER ROLE p03_app CONNECTION LIMIT 50;
ALTER ROLE p03_app SET statement_timeout = '15s';
ALTER ROLE p03_app SET lock_timeout = '5s';
