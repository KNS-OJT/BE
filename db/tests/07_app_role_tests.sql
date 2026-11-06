-- =====================================================================
-- P03 | 07_app_role_tests.sql - permission and fail-closed tests for the backend account
-- Run after 01, 02, 03:  psql -h localhost -U p03_app -d p03 -f 07_app_role_tests.sql
-- All changes are rolled back. The run stops with an error if any test fails.
-- =====================================================================
\set ON_ERROR_STOP on
BEGIN;
DO $test$
DECLARE
  t text[]; v_state text; v_msg text; v_fail int := 0; v_n int := 0;
  v_tests text[][] := ARRAY[
    -- id | actor username ('' = none) | SQL | expected SQLSTATE ('' = must succeed)
    ['fail-closed: PR','', $$INSERT INTO purchase_requests(requester_id,reason) VALUES(1,'x')$$,'42501'],
    ['fail-closed: stock issue','', $$INSERT INTO stock_issues(requested_by,reason) VALUES(1,'x')$$,'42501'],
    ['fail-closed: product','', $$INSERT INTO products(sku,product_name,category_id,unit) VALUES('T-1','t',1,'c')$$,'42501'],
    ['fail-closed: supplier','', $$UPDATE suppliers SET phone='1' WHERE supplier_id=1$$,'42501'],
    ['fail-closed: minimum stock','', $$UPDATE stock_balances SET minimum_stock=1 WHERE product_id=1$$,'42501'],
    ['login bookkeeping without actor','', $$UPDATE users SET failed_login_count=1 WHERE username='requester01'$$,''],
    ['security event without actor','', $$SELECT fn_log_security_event('LOGIN_FAILED','FAILED')$$,''],
    ['posting function is not callable','warehouse01', $$SELECT fn_post_stock(1,'IN',1000,3,NULL,NULL,NULL,'x')$$,'42501'],
    ['direct stock update','warehouse01', $$UPDATE stock_balances SET quantity=999 WHERE product_id=1$$,'42501'],
    ['insert ledger row','warehouse01', $$INSERT INTO stock_transactions(product_id,transaction_type,quantity,balance_after,created_by,prev_hash,row_hash) VALUES(1,'IN',1,1,3,'a','b')$$,'42501'],
    ['delete audit log','manager01', $$DELETE FROM audit_logs$$,'42501'],
    ['IN01: requester creates user','requester01', $$INSERT INTO users(username,password_hash,full_name,role_id) VALUES('hack1',repeat('x',60),'H',1)$$,'42501'],
    ['IN01: requester changes role','requester01', $$UPDATE users SET role_id=(SELECT role_id FROM roles WHERE role_name='MANAGER') WHERE username='requester01'$$,'42501'],
    ['IN01: manager creates user','manager01', $$INSERT INTO users(username,password_hash,full_name,role_id) VALUES('newuser1',repeat('x',60),'N',1)$$,''],
    ['IN01: manager locks user','manager01', $$UPDATE users SET is_active=false WHERE username='newuser1'$$,''],
    ['requester creates a draft PR','requester01', $$WITH p AS (INSERT INTO purchase_requests(requester_id,reason) SELECT user_id,'x' FROM users WHERE username='requester01' RETURNING 1) SELECT count(*) FROM p$$,''],
    ['409: approve a DRAFT PR','manager01', $$UPDATE purchase_requests SET status='APPROVED',reviewed_by=(SELECT user_id FROM users WHERE username='manager01') WHERE status='DRAFT'$$,'P0409']
  ];
BEGIN
  FOREACH t SLICE 1 IN ARRAY v_tests LOOP
    v_n := v_n + 1;
    PERFORM set_config('app.current_user_id', COALESCE((SELECT user_id::text FROM users WHERE username = t[2]), ''), true);
    v_state := '';
    BEGIN
      EXECUTE t[3];
      v_msg := 'ok';
      IF t[4] = '' THEN NULL; END IF;
    EXCEPTION WHEN OTHERS THEN
      v_state := SQLSTATE; v_msg := SQLERRM;
    END;
    IF v_state = t[4] THEN
      RAISE NOTICE 'PASS | %', t[1];
    ELSE
      v_fail := v_fail + 1;
      RAISE NOTICE 'FAIL | % | expected [%] got [%] %', t[1], t[4], v_state, v_msg;
    END IF;
  END LOOP;
  IF v_fail > 0 THEN RAISE EXCEPTION '% of % tests failed', v_fail, v_n; END IF;
  RAISE NOTICE 'ALL PASS: % tests', v_n;
END $test$;
ROLLBACK;
