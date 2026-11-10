-- =====================================================================
-- P03 | 04_security_tests.sql - database-level security & business-rule tests
-- Run on a fresh database after 01_schema.sql + 03_seed.sql:
--     psql -d p03 -f 04_security_tests.sql
-- Every test prints PASS or FAIL. Evidence for the IA Security Test Report.
-- (SEC-TC-303 concurrency is run separately by 05_concurrency_test.sh)
-- =====================================================================
\set ON_ERROR_STOP on
SET client_min_messages = warning;

CREATE TEMP TABLE t_results (id serial, test_id text, description text, result text, detail text);

-- run SQL as a given user; expect it to FAIL with a message matching p_pattern
CREATE FUNCTION pg_temp.expect_error(p_test text, p_desc text, p_user text, p_sql text, p_pattern text)
RETURNS void LANGUAGE plpgsql AS $$
BEGIN
  PERFORM set_config('app.current_user_id', (SELECT user_id::text FROM users WHERE username = p_user), false);
  BEGIN
    EXECUTE p_sql;
    INSERT INTO t_results (test_id, description, result, detail) VALUES (p_test, p_desc, 'FAIL', 'statement succeeded');
  EXCEPTION WHEN OTHERS THEN
    INSERT INTO t_results (test_id, description, result, detail)
    VALUES (p_test, p_desc, CASE WHEN SQLERRM ~* p_pattern THEN 'PASS' ELSE 'FAIL' END, SQLERRM);
  END;
END $$;

-- run SQL as a given user; expect it to SUCCEED
CREATE FUNCTION pg_temp.expect_ok(p_test text, p_desc text, p_user text, p_sql text)
RETURNS void LANGUAGE plpgsql AS $$
BEGIN
  PERFORM set_config('app.current_user_id', (SELECT user_id::text FROM users WHERE username = p_user), false);
  BEGIN
    EXECUTE p_sql;
    INSERT INTO t_results (test_id, description, result, detail) VALUES (p_test, p_desc, 'PASS', NULL);
  EXCEPTION WHEN OTHERS THEN
    INSERT INTO t_results (test_id, description, result, detail) VALUES (p_test, p_desc, 'FAIL', SQLERRM);
  END;
END $$;

CREATE FUNCTION pg_temp.check(p_test text, p_desc text, p_ok boolean, p_detail text DEFAULT NULL)
RETURNS void LANGUAGE sql AS $$
  INSERT INTO t_results (test_id, description, result, detail)
  VALUES (p_test, p_desc, CASE WHEN p_ok THEN 'PASS' ELSE 'FAIL' END, p_detail);
$$;

CREATE FUNCTION pg_temp.uid(p text) RETURNS bigint LANGUAGE sql AS $$ SELECT user_id FROM users WHERE username = p $$;
CREATE FUNCTION pg_temp.pid(p text) RETURNS bigint LANGUAGE sql AS $$ SELECT product_id FROM products WHERE sku = p $$;

-- ---------------------------------------------------------------------
-- A. Purchase request workflow
-- ---------------------------------------------------------------------
SELECT pg_temp.expect_error('IA-IN07', 'Mass assignment: create PR already APPROVED', 'requester01',
  $q$INSERT INTO purchase_requests (requester_id, reason, status) VALUES (pg_temp.uid('requester01'), 'x', 'APPROVED')$q$,
  'IA-IN07');

SELECT pg_temp.expect_error('IA-IN07', 'Create PR on behalf of another user', 'requester01',
  $q$INSERT INTO purchase_requests (requester_id, reason) VALUES (pg_temp.uid('requester02'), 'x')$q$,
  'IA-IN07');

SELECT pg_temp.expect_ok('IN06', 'Requester creates a draft PR with items', 'requester01',
  $q$WITH pr AS (INSERT INTO purchase_requests (requester_id, reason)
                 VALUES (pg_temp.uid('requester01'), 'Bổ sung bút bi và bàn phím dưới mức tối thiểu')
                 RETURNING purchase_request_id)
     INSERT INTO purchase_request_items (purchase_request_id, product_id, quantity)
     SELECT purchase_request_id, pg_temp.pid('VPP-BUT-002'), 30 FROM pr
     UNION ALL SELECT purchase_request_id, pg_temp.pid('IT-KEY-002'), 10 FROM pr$q$);

SELECT pg_temp.expect_error('BR-12', 'PR item with quantity 0', 'requester01',
  $q$INSERT INTO purchase_request_items (purchase_request_id, product_id, quantity)
     VALUES ((SELECT max(purchase_request_id) FROM purchase_requests), pg_temp.pid('VPP-A4-001'), 0)$q$,
  'ck_pri_qty_positive');

SELECT pg_temp.expect_ok('IN06', 'Requester02 creates an empty draft PR', 'requester02',
  $q$INSERT INTO purchase_requests (requester_id, reason) VALUES (pg_temp.uid('requester02'), 'empty')$q$);
SELECT pg_temp.expect_error('BR-12', 'Submit an empty PR', 'requester02',
  $q$UPDATE purchase_requests SET status = 'SUBMITTED' WHERE reason = 'empty'$q$,
  'BR-12');
SELECT pg_temp.expect_ok('BR-16', 'Requester02 cancels the empty PR with a reason', 'requester02',
  $q$UPDATE purchase_requests SET status = 'CANCELED', cancel_reason = 'Tạo nhầm' WHERE reason = 'empty'$q$);

SELECT pg_temp.expect_error('IA-IN03', 'Purchasing creates PO from a DRAFT PR', 'purchasing01',
  $q$INSERT INTO purchase_orders (purchase_request_id, supplier_id, created_by)
     VALUES ((SELECT purchase_request_id FROM purchase_requests WHERE reason LIKE 'Bổ sung bút%'),
             (SELECT min(supplier_id) FROM suppliers), pg_temp.uid('purchasing01'))$q$,
  'IA-IN03');

SELECT pg_temp.expect_ok('IN06', 'Requester submits the PR', 'requester01',
  $q$UPDATE purchase_requests SET status = 'SUBMITTED' WHERE reason LIKE 'Bổ sung bút%'$q$);

SELECT pg_temp.expect_error('BR-11', 'Edit item quantity after submit', 'requester01',
  $q$UPDATE purchase_request_items SET quantity = 999
      WHERE purchase_request_id = (SELECT purchase_request_id FROM purchase_requests WHERE reason LIKE 'Bổ sung bút%')$q$,
  'BR-11');

SELECT pg_temp.expect_error('IA-IN03', 'Purchasing creates PO from a SUBMITTED (not approved) PR', 'purchasing01',
  $q$INSERT INTO purchase_orders (purchase_request_id, supplier_id, created_by)
     VALUES ((SELECT purchase_request_id FROM purchase_requests WHERE reason LIKE 'Bổ sung bút%'),
             (SELECT min(supplier_id) FROM suppliers), pg_temp.uid('purchasing01'))$q$,
  'IA-IN03');

SELECT pg_temp.expect_error('SEC-TC-301', 'Requester calls API to approve own PR', 'requester01',
  $q$UPDATE purchase_requests SET status = 'APPROVED', reviewed_by = pg_temp.uid('requester01')
      WHERE reason LIKE 'Bổ sung bút%'$q$,
  'IA-IN02|IA-IN01|ck_pr_no_self_approval');

SELECT pg_temp.expect_error('SEC-TC-302', 'Requester sets reviewed_by = manager (spoofed reviewer)', 'requester01',
  $q$UPDATE purchase_requests SET status = 'APPROVED', reviewed_by = pg_temp.uid('manager01')
      WHERE reason LIKE 'Bổ sung bút%'$q$,
  'IA-IN07');

SELECT pg_temp.expect_error('IA-IN02', 'Warehouse staff approves PR', 'warehouse01',
  $q$UPDATE purchase_requests SET status = 'APPROVED', reviewed_by = pg_temp.uid('warehouse01')
      WHERE reason LIKE 'Bổ sung bút%'$q$,
  'IA-IN02');

SELECT pg_temp.expect_error('BR-10', 'Manager rejects without a reason', 'manager01',
  $q$UPDATE purchase_requests SET status = 'REJECTED', reviewed_by = pg_temp.uid('manager01')
      WHERE reason LIKE 'Bổ sung bút%'$q$,
  'ck_pr_reject_reason');

-- Manager who is also the requester cannot approve own PR
SELECT pg_temp.expect_ok('IN06', 'Manager01 creates and submits own PR', 'manager01',
  $q$WITH pr AS (INSERT INTO purchase_requests (requester_id, reason) VALUES (pg_temp.uid('manager01'), 'PR của manager')
                 RETURNING purchase_request_id),
          it AS (INSERT INTO purchase_request_items (purchase_request_id, product_id, quantity)
                 SELECT purchase_request_id, pg_temp.pid('VS-KHG-001'), 5 FROM pr RETURNING purchase_request_id)
     SELECT 1$q$);
SELECT pg_temp.expect_ok('IN06', 'Manager01 submits own PR', 'manager01',
  $q$UPDATE purchase_requests SET status = 'SUBMITTED' WHERE reason = 'PR của manager'$q$);
SELECT pg_temp.expect_error('IA-IN01', 'Manager approves a PR they created (SoD)', 'manager01',
  $q$UPDATE purchase_requests SET status = 'APPROVED', reviewed_by = pg_temp.uid('manager01')
      WHERE reason = 'PR của manager'$q$,
  'IA-IN01');

SELECT pg_temp.expect_ok('IN07', 'Manager01 approves requester01 PR', 'manager01',
  $q$UPDATE purchase_requests SET status = 'APPROVED', reviewed_by = pg_temp.uid('manager01')
      WHERE reason LIKE 'Bổ sung bút%'$q$);

SELECT pg_temp.expect_error('BR-11', 'Change an APPROVED PR back to DRAFT', 'manager01',
  $q$UPDATE purchase_requests SET status = 'DRAFT', reviewed_by = NULL, reviewed_at = NULL
      WHERE reason LIKE 'Bổ sung bút%'$q$,
  'Invalid purchase request transition|ck_pr');

-- ---------------------------------------------------------------------
-- B. Purchase order
-- ---------------------------------------------------------------------
SELECT pg_temp.expect_error('IA-IN02', 'Manager (not Purchasing) creates PO', 'manager01',
  $q$INSERT INTO purchase_orders (purchase_request_id, supplier_id, created_by)
     VALUES ((SELECT purchase_request_id FROM purchase_requests WHERE reason LIKE 'Bổ sung bút%'),
             (SELECT min(supplier_id) FROM suppliers), pg_temp.uid('manager01'))$q$,
  'IA-IN02');

SELECT pg_temp.expect_ok('IN08', 'Purchasing creates PO from the APPROVED PR', 'purchasing01',
  $q$INSERT INTO purchase_orders (purchase_request_id, supplier_id, created_by, expected_delivery_date)
     VALUES ((SELECT purchase_request_id FROM purchase_requests WHERE reason LIKE 'Bổ sung bút%'),
             (SELECT min(supplier_id) FROM suppliers), pg_temp.uid('purchasing01'), current_date + 7)$q$);

SELECT pg_temp.expect_error('BR-19', 'Second PO for the same PR', 'purchasing01',
  $q$INSERT INTO purchase_orders (purchase_request_id, supplier_id, created_by)
     VALUES ((SELECT purchase_request_id FROM purchase_requests WHERE reason LIKE 'Bổ sung bút%'),
             (SELECT min(supplier_id) FROM suppliers), pg_temp.uid('purchasing01'))$q$,
  'purchase_orders_purchase_request_id_key');

SELECT pg_temp.expect_error('IA-IN04', 'PO line quantity above approved quantity', 'purchasing01',
  $q$INSERT INTO purchase_order_items (purchase_order_id, purchase_request_item_id, quantity)
     SELECT po.purchase_order_id, pri.purchase_request_item_id, pri.quantity + 1
       FROM purchase_orders po JOIN purchase_request_items pri ON pri.purchase_request_id = po.purchase_request_id
      WHERE po.status = 'DRAFT' LIMIT 1$q$,
  'IA-IN04');

SELECT pg_temp.expect_ok('IN08', 'Purchasing adds PO lines and marks ORDERED', 'purchasing01',
  $q$WITH ins AS (
       INSERT INTO purchase_order_items (purchase_order_id, purchase_request_item_id, quantity)
       SELECT po.purchase_order_id, pri.purchase_request_item_id, pri.quantity
         FROM purchase_orders po JOIN purchase_request_items pri ON pri.purchase_request_id = po.purchase_request_id
        WHERE po.status = 'DRAFT' RETURNING purchase_order_id)
     SELECT 1$q$);
SELECT pg_temp.expect_ok('IN08', 'PO set to ORDERED', 'purchasing01',
  $q$UPDATE purchase_orders SET status = 'ORDERED' WHERE status = 'DRAFT'$q$);

SELECT pg_temp.expect_error('BR-21', 'Client sets PO to RECEIVED without a receipt', 'purchasing01',
  $q$UPDATE purchase_orders SET status = 'RECEIVED' WHERE status = 'ORDERED'$q$,
  'BR-21');

-- ---------------------------------------------------------------------
-- C. Goods receipt (partial, over-receipt, ledger)
-- ---------------------------------------------------------------------
SELECT pg_temp.expect_ok('IN09', 'Warehouse01 creates receipt: 20 pens (of 30) + 10 keyboards', 'warehouse01',
  $q$WITH gr AS (INSERT INTO goods_receipts (purchase_order_id, received_by, document_reference)
                 SELECT purchase_order_id, pg_temp.uid('warehouse01'), 'DN-0001' FROM purchase_orders WHERE status = 'ORDERED'
                 RETURNING goods_receipt_id, purchase_order_id)
     INSERT INTO goods_receipt_items (goods_receipt_id, purchase_order_item_id, received_quantity)
     SELECT gr.goods_receipt_id, poi.purchase_order_item_id,
            CASE WHEN pri.product_id = pg_temp.pid('VPP-BUT-002') THEN 20 ELSE 10 END
       FROM gr JOIN purchase_order_items poi ON poi.purchase_order_id = gr.purchase_order_id
               JOIN purchase_request_items pri ON pri.purchase_request_item_id = poi.purchase_request_item_id$q$);

SELECT pg_temp.expect_error('IA-IN02', 'Purchasing confirms goods receipt', 'purchasing01',
  $q$UPDATE goods_receipts SET status = 'CONFIRMED', confirmed_by = pg_temp.uid('purchasing01') WHERE status = 'DRAFT'$q$,
  'IN10');

SELECT pg_temp.check('BR-04', 'DRAFT receipt does not change stock',
  (SELECT quantity FROM stock_balances WHERE product_id = pg_temp.pid('VPP-BUT-002')) = 15);

SELECT pg_temp.expect_ok('IN10', 'Warehouse confirms receipt', 'warehouse01',
  $q$UPDATE goods_receipts SET status = 'CONFIRMED', confirmed_by = pg_temp.uid('warehouse01') WHERE status = 'DRAFT'$q$);

SELECT pg_temp.check('BR-04', 'Confirmed receipt increased stock (pens 15 -> 35)',
  (SELECT quantity FROM stock_balances WHERE product_id = pg_temp.pid('VPP-BUT-002')) = 35);
SELECT pg_temp.check('BR-21', 'PO is PARTIALLY_RECEIVED',
  EXISTS (SELECT 1 FROM purchase_orders WHERE status = 'PARTIALLY_RECEIVED'));

SELECT pg_temp.expect_error('BR-04', 'Edit quantity on a confirmed receipt', 'warehouse01',
  $q$UPDATE goods_receipt_items SET received_quantity = 100
      WHERE goods_receipt_id = (SELECT max(goods_receipt_id) FROM goods_receipts)$q$,
  'BR-04');

SELECT pg_temp.expect_ok('IN17', 'Second receipt draft with 11 pens', 'warehouse01',
  $q$WITH gr AS (INSERT INTO goods_receipts (purchase_order_id, received_by, document_reference, note)
                 SELECT purchase_order_id, pg_temp.uid('warehouse01'), 'DN-0002', 'over' FROM purchase_orders WHERE status = 'PARTIALLY_RECEIVED'
                 RETURNING goods_receipt_id, purchase_order_id)
     INSERT INTO goods_receipt_items (goods_receipt_id, purchase_order_item_id, received_quantity)
     SELECT gr.goods_receipt_id, poi.purchase_order_item_id, 11
       FROM gr JOIN purchase_order_items poi ON poi.purchase_order_id = gr.purchase_order_id
               JOIN purchase_request_items pri ON pri.purchase_request_item_id = poi.purchase_request_item_id
      WHERE pri.product_id = pg_temp.pid('VPP-BUT-002')$q$);
SELECT pg_temp.expect_error('BR-06', 'Confirm over-receipt (20 + 11 > 30 ordered)', 'warehouse02',
  $q$UPDATE goods_receipts SET status = 'CONFIRMED', confirmed_by = pg_temp.uid('warehouse02') WHERE note = 'over'$q$,
  'BR-06');
SELECT pg_temp.expect_ok('BR-16', 'Cancel the over-receipt draft with a reason', 'warehouse01',
  $q$UPDATE goods_receipts SET status = 'CANCELED', cancel_reason = 'Nhập sai số lượng' WHERE note = 'over'$q$);

SELECT pg_temp.expect_ok('IN17', 'Third receipt: remaining 10 pens', 'warehouse01',
  $q$WITH gr AS (INSERT INTO goods_receipts (purchase_order_id, received_by, document_reference, note)
                 SELECT purchase_order_id, pg_temp.uid('warehouse01'), 'DN-0003', 'rest' FROM purchase_orders WHERE status = 'PARTIALLY_RECEIVED'
                 RETURNING goods_receipt_id, purchase_order_id)
     INSERT INTO goods_receipt_items (goods_receipt_id, purchase_order_item_id, received_quantity)
     SELECT gr.goods_receipt_id, poi.purchase_order_item_id, 10
       FROM gr JOIN purchase_order_items poi ON poi.purchase_order_id = gr.purchase_order_id
               JOIN purchase_request_items pri ON pri.purchase_request_item_id = poi.purchase_request_item_id
      WHERE pri.product_id = pg_temp.pid('VPP-BUT-002')$q$);
SELECT pg_temp.expect_ok('IN17', 'Confirm third receipt', 'warehouse02',
  $q$UPDATE goods_receipts SET status = 'CONFIRMED', confirmed_by = pg_temp.uid('warehouse02') WHERE note = 'rest'$q$);
SELECT pg_temp.check('BR-21', 'PO becomes RECEIVED after full receipt',
  EXISTS (SELECT 1 FROM purchase_orders WHERE status = 'RECEIVED'));

SELECT pg_temp.expect_error('BR-18', 'Cancel a PO that has confirmed receipts', 'purchasing01',
  $q$UPDATE purchase_orders SET status = 'CANCELED', cancel_reason = 'test' WHERE status = 'RECEIVED'$q$,
  'BR-18');

-- ---------------------------------------------------------------------
-- D. Stock issue
-- ---------------------------------------------------------------------
SELECT pg_temp.expect_ok('IN11', 'Requester02 asks for 5 mice', 'requester02',
  $q$WITH si AS (INSERT INTO stock_issues (requested_by, reason) VALUES (pg_temp.uid('requester02'), 'Cấp chuột cho phòng kế toán')
                 RETURNING stock_issue_id)
     INSERT INTO stock_issue_items (stock_issue_id, product_id, quantity)
     SELECT stock_issue_id, pg_temp.pid('IT-MOU-001'), 5 FROM si$q$);

SELECT pg_temp.expect_error('IA-IN02', 'Requester confirms own stock issue', 'requester02',
  $q$UPDATE stock_issues SET status = 'CONFIRMED', confirmed_by = pg_temp.uid('requester02') WHERE reason = 'Cấp chuột cho phòng kế toán'$q$,
  'IN12|ck_si_no_self_confirm');

SELECT pg_temp.expect_ok('IN12', 'Warehouse confirms stock issue (25 -> 20)', 'warehouse01',
  $q$UPDATE stock_issues SET status = 'CONFIRMED', confirmed_by = pg_temp.uid('warehouse01') WHERE reason = 'Cấp chuột cho phòng kế toán'$q$);
SELECT pg_temp.check('IN12', 'Mouse stock is 20',
  (SELECT quantity FROM stock_balances WHERE product_id = pg_temp.pid('IT-MOU-001')) = 20);

SELECT pg_temp.expect_ok('IN11', 'Requester02 asks for 999 mice', 'requester02',
  $q$WITH si AS (INSERT INTO stock_issues (requested_by, reason) VALUES (pg_temp.uid('requester02'), 'Quá tồn')
                 RETURNING stock_issue_id)
     INSERT INTO stock_issue_items (stock_issue_id, product_id, quantity)
     SELECT stock_issue_id, pg_temp.pid('IT-MOU-001'), 999 FROM si$q$);
SELECT pg_temp.expect_error('BR-05', 'Confirm issue above available stock', 'warehouse01',
  $q$UPDATE stock_issues SET status = 'CONFIRMED', confirmed_by = pg_temp.uid('warehouse01') WHERE reason = 'Quá tồn'$q$,
  'BR-05');
SELECT pg_temp.check('BR-05', 'Failed issue left stock unchanged (20)',
  (SELECT quantity FROM stock_balances WHERE product_id = pg_temp.pid('IT-MOU-001')) = 20);

-- ---------------------------------------------------------------------
-- E. Direct tampering (as a client bypassing the workflow)
-- ---------------------------------------------------------------------
SELECT pg_temp.expect_error('BR-04', 'Directly set stock quantity', 'warehouse01',
  $q$UPDATE stock_balances SET quantity = 9999 WHERE product_id = pg_temp.pid('IT-MOU-001')$q$, 'BR-04');

SELECT pg_temp.expect_error('IA-IN06', 'Insert a fake ledger row', 'warehouse01',
  $q$INSERT INTO stock_transactions (chain_seq, product_id, transaction_type, quantity, balance_after,
                                     goods_receipt_item_id, created_by, prev_hash, row_hash)
     VALUES (999, pg_temp.pid('IT-MOU-001'), 'IN', 100, 120, 1, pg_temp.uid('warehouse01'), repeat('0',64), repeat('1',64))$q$,
  'IA-IN06|BR-04');

SELECT pg_temp.expect_error('BR-07', 'Update a ledger row', 'warehouse01',
  $q$UPDATE stock_transactions SET quantity = 1 WHERE chain_seq = 1$q$, 'IA-IN06');

SELECT pg_temp.expect_error('BR-07', 'Delete a ledger row', 'warehouse01',
  $q$DELETE FROM stock_transactions WHERE chain_seq = 1$q$, 'IA-IN06');

SELECT pg_temp.expect_error('IA-IN06', 'Delete audit log rows', 'manager01',
  $q$DELETE FROM audit_logs$q$, 'IA-IN06');

SELECT pg_temp.expect_error('BR-16', 'Physically delete a purchase request', 'manager01',
  $q$DELETE FROM purchase_requests WHERE reason LIKE 'Bổ sung bút%'$q$, 'BR-16');

SELECT pg_temp.expect_error('BR-01', 'Duplicate SKU', 'manager01',
  $q$INSERT INTO products (sku, product_name, category_id, unit)
     VALUES ('VPP-A4-001', 'dup', (SELECT min(category_id) FROM categories), 'ram')$q$, 'ux_products_sku');

SELECT pg_temp.expect_error('BR-01', 'Change SKU of a product already used in documents', 'manager01',
  $q$UPDATE products SET sku = 'NEW-SKU' WHERE sku = 'IT-MOU-001'$q$, 'BR-01');

SELECT pg_temp.expect_error('NFR01', 'Store a plain-text password', 'manager01',
  $q$INSERT INTO users (username, password_hash, full_name, role_id) VALUES ('hacker', '123456', 'x', 1)$q$,
  'ck_users_password_is_hash');

-- ---------------------------------------------------------------------
-- F. Audit trail and integrity checks
-- ---------------------------------------------------------------------
SELECT pg_temp.check('IA-IN05', 'Approval is in audit log with actor, old/new status',
  EXISTS (SELECT 1 FROM audit_logs a
           WHERE a.entity_type = 'purchase_requests' AND a.action = 'STATUS_CHANGE'
             AND a.old_status = 'SUBMITTED' AND a.new_status = 'APPROVED' AND a.actor_id = pg_temp.uid('manager01')));
SELECT pg_temp.check('IA-IN05', 'Confirmed issue is in audit log with warehouse actor',
  EXISTS (SELECT 1 FROM audit_logs a
           WHERE a.entity_type = 'stock_issues' AND a.new_status = 'CONFIRMED' AND a.actor_id = pg_temp.uid('warehouse01')));
SELECT pg_temp.check('IA-IN05', 'Password change is masked in audit log',
  NOT EXISTS (SELECT 1 FROM audit_logs WHERE changed_data::text LIKE '%$2a$%' OR changed_data::text LIKE '%$2b$%'));

SELECT pg_temp.expect_ok('SEC-TC-302', 'Backend logs an ACCESS_DENIED event', 'requester01',
  $q$SELECT fn_log_security_event('ACCESS_DENIED', 'DENIED', NULL, 'purchase_requests',
                                  (SELECT max(purchase_request_id) FROM purchase_requests),
                                  'PATCH /api/purchase-requests/{id} status=APPROVED by REQUESTER',
                                  '{"http_status":403}'::jsonb)$q$);

SELECT pg_temp.check('IA-IN06', 'Ledger hash chain verifies', NOT EXISTS (SELECT 1 FROM fn_verify_stock_ledger()));
SELECT pg_temp.check('IA-IN06', 'Audit hash chain verifies', NOT EXISTS (SELECT 1 FROM fn_verify_audit_log()));
SELECT pg_temp.check('IA-IN06', 'Balance equals ledger for every product', NOT EXISTS (SELECT 1 FROM v_stock_reconciliation WHERE difference <> 0));

-- Simulated attacker with superuser rights edits the ledger: detection must catch it
DO $$
BEGIN
  ALTER TABLE stock_transactions DISABLE TRIGGER tg_stock_transactions_append_only;
  UPDATE stock_transactions SET quantity = quantity + 100 WHERE chain_seq = 2;
  ALTER TABLE stock_transactions ENABLE TRIGGER tg_stock_transactions_append_only;
END $$;
SELECT pg_temp.check('IA-IN06', 'Tamper detection: modified ledger row is detected',
  EXISTS (SELECT 1 FROM fn_verify_stock_ledger() WHERE chain_seq = 2),
  (SELECT string_agg(problem, '; ') FROM fn_verify_stock_ledger()));
DO $$
BEGIN
  ALTER TABLE stock_transactions DISABLE TRIGGER tg_stock_transactions_append_only;
  UPDATE stock_transactions SET quantity = quantity - 100 WHERE chain_seq = 2;
  ALTER TABLE stock_transactions ENABLE TRIGGER tg_stock_transactions_append_only;
END $$;

-- ---------------------------------------------------------------------
SELECT test_id, result, description, left(coalesce(detail, ''), 90) AS detail FROM t_results ORDER BY id;
SELECT result, count(*) FROM t_results GROUP BY result ORDER BY result;
