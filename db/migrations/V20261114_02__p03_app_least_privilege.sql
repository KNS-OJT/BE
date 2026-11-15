-- Restrict the backend account to business operations; keep owner access for human operators.
-- Provision p03_app with a strong secret out of band before applying this migration.
BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'p03_app' AND rolcanlogin) THEN
        RAISE EXCEPTION 'p03_app must be provisioned with LOGIN and an out-of-band password first';
    END IF;
    IF EXISTS (
        SELECT 1 FROM pg_roles
         WHERE rolname = 'p03_app'
           AND (rolsuper OR rolcreatedb OR rolcreaterole)
    ) THEN
        RAISE EXCEPTION 'p03_app has elevated role attributes';
    END IF;
END $$;

REVOKE CREATE ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO p03_app;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO p03_app;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO p03_app;

GRANT INSERT, UPDATE ON users, categories, products, suppliers,
                        purchase_requests, purchase_orders, goods_receipts,
                        stock_issues, stock_adjustments TO p03_app;
GRANT INSERT, UPDATE, DELETE ON purchase_request_items, purchase_order_items,
                                goods_receipt_items, stock_issue_items,
                                stock_adjustment_items TO p03_app;
GRANT UPDATE (minimum_stock) ON stock_balances TO p03_app;

REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON stock_transactions, audit_logs FROM p03_app;
REVOKE TRUNCATE ON ALL TABLES IN SCHEMA public FROM p03_app;
REVOKE EXECUTE ON FUNCTION fn_post_stock(bigint, stock_transaction_type, numeric, bigint, bigint, bigint, bigint, text) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION fn_log_security_event(varchar, varchar, bigint, varchar, bigint, text, jsonb) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION fn_log_security_event(varchar, varchar, bigint, varchar, bigint, text, jsonb) TO p03_app;

ALTER ROLE p03_app CONNECTION LIMIT 50;
ALTER ROLE p03_app SET statement_timeout = '15s';
ALTER ROLE p03_app SET lock_timeout = '5s';

COMMIT;
