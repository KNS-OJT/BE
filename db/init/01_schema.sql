-- =====================================================================
-- P03 - Inventory & Purchase Request Management System
-- PostgreSQL 14+  |  01_schema.sql
--
-- Covers: SRS business rules BR-01..BR-22 and SecRS IA-IN01..IA-IN08
--
-- HOW THE BACKEND MUST CALL THE DATABASE
--   At the start of every business transaction, after authenticating the JWT:
--       BEGIN;
--       SET LOCAL app.current_user_id = '<user_id from JWT>';
--       SET LOCAL app.client_ip       = '<client ip>';      -- optional
--       ... business statements ...
--       COMMIT;
--   The database uses app.current_user_id to (a) stop a client from
--   claiming someone else as reviewer/confirmer (mass assignment, IA-IN07)
--   and (b) record the real actor in audit_logs (IA-IN05).
--
--   Stock is NEVER changed directly. The backend only changes a document's
--   status to CONFIRMED; triggers then post the stock ledger and update the
--   balance atomically inside the same transaction (BR-04, BR-05, IA-IN04).
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- 0. ENUMS
-- ---------------------------------------------------------------------
CREATE TYPE user_role AS ENUM ('REQUESTER', 'WAREHOUSE_STAFF', 'PURCHASING_STAFF', 'MANAGER');

-- BR-11 uses "Submitted"; BR-16 needs a cancelled state
CREATE TYPE purchase_request_status AS ENUM ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'CANCELED');

CREATE TYPE purchase_order_status AS ENUM ('DRAFT', 'ORDERED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CANCELED');

-- used by goods receipts, stock issues and stock adjustments
CREATE TYPE document_status AS ENUM ('DRAFT', 'CONFIRMED', 'CANCELED');

-- BR-22: adjustments (opening stock, stocktake, correction) go through the ledger too
CREATE TYPE stock_transaction_type AS ENUM ('IN', 'OUT', 'ADJUST_IN', 'ADJUST_OUT');

CREATE TYPE stock_adjustment_type AS ENUM ('OPENING', 'STOCKTAKE', 'CORRECTION');

-- ---------------------------------------------------------------------
-- 1. HELPER FUNCTIONS (session context, role checks)
-- ---------------------------------------------------------------------

-- Authenticated user of the current transaction (set by the backend)
CREATE FUNCTION fn_actor() RETURNS bigint
LANGUAGE sql STABLE AS $$
  SELECT NULLIF(current_setting('app.current_user_id', true), '')::bigint
$$;

-- TRUE only while a posting trigger is changing stock
CREATE FUNCTION fn_posting_on() RETURNS boolean
LANGUAGE sql STABLE AS $$
  SELECT COALESCE(current_setting('app.posting', true), 'off') = 'on'
$$;

-- IA-IN07: a client must not set reviewer/confirmer to someone else
CREATE FUNCTION fn_assert_is_actor(p_claimed bigint, p_field text) RETURNS void
LANGUAGE plpgsql AS $$
BEGIN
  -- Fail closed (Design section 6): a business write without an actor is refused
  IF fn_actor() IS NULL THEN
    RAISE EXCEPTION 'IA-IN07: app.current_user_id is not set' USING ERRCODE = '42501';
  END IF;
  IF p_claimed IS DISTINCT FROM fn_actor() THEN
    RAISE EXCEPTION 'IA-IN07: % must be the authenticated user (claimed %, actual %)',
      p_field, p_claimed, fn_actor()
      USING ERRCODE = '42501';
  END IF;
END $$;

-- IA-IN02 / BR-09: user must exist, be active and hold one of the roles
CREATE FUNCTION fn_assert_role(p_user bigint, p_roles user_role[], p_rule text) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
  v_role   user_role;
  v_active boolean;
BEGIN
  IF p_user IS NULL THEN
    RAISE EXCEPTION '%: user is required', p_rule USING ERRCODE = '42501';
  END IF;
  SELECT r.role_name, u.is_active INTO v_role, v_active
    FROM users u JOIN roles r ON r.role_id = u.role_id
   WHERE u.user_id = p_user;
  IF NOT FOUND OR NOT v_active THEN
    RAISE EXCEPTION '%: user % does not exist or is inactive', p_rule, p_user USING ERRCODE = '42501';
  END IF;
  IF NOT (v_role = ANY (p_roles)) THEN
    RAISE EXCEPTION '%: user % has role %, required one of %', p_rule, p_user, v_role, p_roles
      USING ERRCODE = '42501';
  END IF;
END $$;

-- BR-16: documents are never physically deleted
CREATE FUNCTION trg_block_delete() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'BR-16: rows in % cannot be deleted; set status CANCELED instead', TG_TABLE_NAME
    USING ERRCODE = '42501';
END $$;

-- BR-07 / IA-IN06: append-only tables
CREATE FUNCTION trg_append_only() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'IA-IN06: % is append-only; % is not allowed', TG_TABLE_NAME, TG_OP
    USING ERRCODE = '42501';
END $$;

-- ---------------------------------------------------------------------
-- 2. USERS & ROLES
-- ---------------------------------------------------------------------
CREATE TABLE roles (
  role_id    smallint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  role_name  user_role NOT NULL UNIQUE
);

CREATE TABLE users (
  user_id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  username            varchar(100) NOT NULL,
  password_hash       varchar(255) NOT NULL,          -- bcrypt/argon2 hash produced by the backend
  full_name           varchar(150) NOT NULL,
  email               varchar(254),
  role_id             smallint NOT NULL REFERENCES roles(role_id),
  is_active           boolean NOT NULL DEFAULT true,  -- deactivate instead of delete
  failed_login_count  smallint NOT NULL DEFAULT 0,
  locked_until        timestamptz,
  last_login_at       timestamptz,
  password_changed_at timestamptz NOT NULL DEFAULT now(),
  created_at          timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_users_username_format CHECK (username ~ '^[A-Za-z0-9._-]{3,100}$'),
  CONSTRAINT ck_users_password_is_hash CHECK (length(password_hash) >= 50),   -- refuses plain text
  CONSTRAINT ck_users_email CHECK (email IS NULL OR email ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$'),
  CONSTRAINT ck_users_failed_login CHECK (failed_login_count >= 0)
);
CREATE UNIQUE INDEX ux_users_username_ci ON users (lower(username));

-- ---------------------------------------------------------------------
-- 3. PRODUCT CATALOG
-- ---------------------------------------------------------------------
CREATE TABLE categories (
  category_id    bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  category_name  varchar(100) NOT NULL,
  description    text,
  is_active      boolean NOT NULL DEFAULT true
);
CREATE UNIQUE INDEX ux_categories_name_ci ON categories (lower(category_name));

CREATE TABLE products (
  product_id    bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  sku           varchar(50) NOT NULL,
  product_name  varchar(200) NOT NULL,
  category_id   bigint NOT NULL REFERENCES categories(category_id),
  unit          varchar(30) NOT NULL,
  is_active     boolean NOT NULL DEFAULT true,
  created_at    timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_products_sku_format CHECK (sku ~ '^[A-Z0-9][A-Z0-9._-]{0,49}$')
);
-- BR-01: SKU unique, also among inactive products (no partial index)
CREATE UNIQUE INDEX ux_products_sku ON products (sku);
CREATE INDEX ix_products_category ON products (category_id);
CREATE INDEX ix_products_name ON products (lower(product_name));

CREATE TABLE suppliers (
  supplier_id     bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  supplier_name   varchar(200) NOT NULL,
  contact_person  varchar(150),
  phone           varchar(30),
  email           varchar(254),
  address         text,
  is_active       boolean NOT NULL DEFAULT true,
  CONSTRAINT ck_suppliers_email CHECK (email IS NULL OR email ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$')
);

-- ---------------------------------------------------------------------
-- 4. STOCK BALANCE (one physical warehouse in MVP)
-- ---------------------------------------------------------------------
CREATE TABLE stock_balances (
  stock_balance_id  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  product_id        bigint NOT NULL UNIQUE REFERENCES products(product_id),
  quantity          numeric(14,3) NOT NULL DEFAULT 0,
  minimum_stock     numeric(14,3) NOT NULL DEFAULT 0,
  updated_at        timestamptz NOT NULL DEFAULT now(),
  -- IA-IN04 / BR-05: last line of defence against negative stock
  CONSTRAINT ck_stock_balances_qty_non_negative CHECK (quantity >= 0),
  CONSTRAINT ck_stock_balances_min_non_negative CHECK (minimum_stock >= 0)
);

-- Every product gets a balance row, so posting can always lock it
CREATE FUNCTION trg_products_create_balance() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp AS $$
BEGIN
  INSERT INTO stock_balances (product_id, quantity) VALUES (NEW.product_id, 0);
  RETURN NULL;
END $$;
CREATE TRIGGER tg_products_create_balance AFTER INSERT ON products
  FOR EACH ROW EXECUTE FUNCTION trg_products_create_balance();

-- Quantity may change only through posting (BR-04, BR-22)
CREATE FUNCTION trg_stock_balances_guard() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'INSERT' THEN
    IF NEW.quantity <> 0 THEN
      RAISE EXCEPTION 'BR-22: opening stock must be entered with a stock adjustment, not directly'
        USING ERRCODE = '42501';
    END IF;
  ELSIF NEW.quantity IS DISTINCT FROM OLD.quantity AND NOT fn_posting_on() THEN
    RAISE EXCEPTION 'BR-04: stock quantity can only change by confirming a receipt, issue or adjustment'
      USING ERRCODE = '42501';
  ELSIF NEW.product_id IS DISTINCT FROM OLD.product_id THEN
    RAISE EXCEPTION 'stock_balances.product_id is immutable' USING ERRCODE = '42501';
  END IF;
  NEW.updated_at := now();
  RETURN NEW;
END $$;
CREATE TRIGGER tg_stock_balances_guard BEFORE INSERT OR UPDATE ON stock_balances
  FOR EACH ROW EXECUTE FUNCTION trg_stock_balances_guard();
CREATE TRIGGER tg_stock_balances_no_delete BEFORE DELETE ON stock_balances
  FOR EACH ROW EXECUTE FUNCTION trg_block_delete();

-- ---------------------------------------------------------------------
-- 5. DOCUMENT CODE SEQUENCES (BR-15: auto, unique, never reused)
-- ---------------------------------------------------------------------
CREATE SEQUENCE seq_pr_code;
CREATE SEQUENCE seq_po_code;
CREATE SEQUENCE seq_gr_code;
CREATE SEQUENCE seq_si_code;
CREATE SEQUENCE seq_sa_code;

-- ---------------------------------------------------------------------
-- 6. PURCHASE REQUEST
-- ---------------------------------------------------------------------
CREATE TABLE purchase_requests (
  purchase_request_id  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  request_code         varchar(30) NOT NULL UNIQUE
                       DEFAULT 'PR-' || to_char(now(), 'YYYY') || '-' || lpad(nextval('seq_pr_code')::text, 5, '0'),
  requester_id         bigint NOT NULL REFERENCES users(user_id),
  reason               text NOT NULL,
  status               purchase_request_status NOT NULL DEFAULT 'DRAFT',
  submitted_at         timestamptz,
  reviewed_by          bigint REFERENCES users(user_id),
  reviewed_at          timestamptz,
  review_reason        text,
  cancel_reason        text,
  created_at           timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_pr_reason_not_blank CHECK (btrim(reason) <> ''),
  -- IA-IN01 / BR-08: segregation of duties
  CONSTRAINT ck_pr_no_self_approval CHECK (reviewed_by IS NULL OR reviewed_by <> requester_id),
  CONSTRAINT ck_pr_submitted_at CHECK (status IN ('DRAFT', 'CANCELED') OR submitted_at IS NOT NULL),
  CONSTRAINT ck_pr_review_fields CHECK (
    (status IN ('APPROVED', 'REJECTED')) = (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)
  ),
  -- BR-10: rejection needs a reason
  CONSTRAINT ck_pr_reject_reason CHECK (status <> 'REJECTED' OR btrim(coalesce(review_reason, '')) <> ''),
  CONSTRAINT ck_pr_cancel_reason CHECK (status <> 'CANCELED' OR btrim(coalesce(cancel_reason, '')) <> '')
);
CREATE INDEX ix_pr_status ON purchase_requests (status);
CREATE INDEX ix_pr_requester ON purchase_requests (requester_id);

CREATE TABLE purchase_request_items (
  purchase_request_item_id  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  purchase_request_id       bigint NOT NULL REFERENCES purchase_requests(purchase_request_id),
  product_id                bigint NOT NULL REFERENCES products(product_id),
  quantity                  numeric(14,3) NOT NULL,
  note                      text,
  CONSTRAINT ck_pri_qty_positive CHECK (quantity > 0),      -- BR-12
  CONSTRAINT ux_pri_request_product UNIQUE (purchase_request_id, product_id)
);
CREATE INDEX ix_pri_product ON purchase_request_items (product_id);

-- PR workflow: insert rules, allowed transitions, locking (BR-08..BR-12, IA-IN01/02/07)
CREATE FUNCTION trg_purchase_requests_workflow() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'INSERT' THEN
    -- IA-IN07 mass assignment: a new PR is always a clean draft
    IF NEW.status <> 'DRAFT' OR NEW.reviewed_by IS NOT NULL OR NEW.reviewed_at IS NOT NULL
       OR NEW.submitted_at IS NOT NULL OR NEW.review_reason IS NOT NULL THEN
      RAISE EXCEPTION 'IA-IN07: a purchase request must be created as DRAFT without review data'
        USING ERRCODE = '42501';
    END IF;
    PERFORM fn_assert_is_actor(NEW.requester_id, 'requester_id');
    PERFORM fn_assert_role(NEW.requester_id,
      ARRAY['REQUESTER','WAREHOUSE_STAFF','PURCHASING_STAFF','MANAGER']::user_role[], 'IN06');
    NEW.created_at := now();
    RETURN NEW;
  END IF;

  -- immutable columns
  IF NEW.request_code <> OLD.request_code OR NEW.requester_id <> OLD.requester_id
     OR NEW.created_at <> OLD.created_at THEN
    RAISE EXCEPTION 'BR-15: request_code, requester_id and created_at are immutable' USING ERRCODE = '42501';
  END IF;

  IF NEW.status = OLD.status THEN
    -- BR-11: only a DRAFT may be edited
    IF OLD.status <> 'DRAFT' THEN
      RAISE EXCEPTION 'BR-11: purchase request % is % and locked', OLD.request_code, OLD.status
        USING ERRCODE = 'P0409';
    END IF;
    IF NEW.reviewed_by IS NOT NULL OR NEW.submitted_at IS NOT NULL THEN
      RAISE EXCEPTION 'IA-IN07: review/submit fields cannot be set while editing a draft' USING ERRCODE = '42501';
    END IF;
    RETURN NEW;
  END IF;

  -- status transition
  IF OLD.status = 'DRAFT' AND NEW.status = 'SUBMITTED' THEN
    PERFORM fn_assert_is_actor(OLD.requester_id, 'submitter');
    IF NOT EXISTS (SELECT 1 FROM purchase_request_items WHERE purchase_request_id = OLD.purchase_request_id) THEN
      RAISE EXCEPTION 'BR-12: a purchase request needs at least one item before submit' USING ERRCODE = '23514';
    END IF;
    IF NEW.reason IS DISTINCT FROM OLD.reason THEN
      RAISE EXCEPTION 'BR-11: content cannot change in the submit step' USING ERRCODE = '42501';
    END IF;
    NEW.submitted_at := now();

  ELSIF OLD.status = 'SUBMITTED' AND NEW.status IN ('APPROVED', 'REJECTED') THEN
    PERFORM fn_assert_is_actor(NEW.reviewed_by, 'reviewed_by');
    PERFORM fn_assert_role(NEW.reviewed_by, ARRAY['MANAGER']::user_role[], 'IA-IN02/BR-09');
    IF NEW.reviewed_by = OLD.requester_id THEN
      RAISE EXCEPTION 'IA-IN01/BR-08: the requester cannot approve or reject their own purchase request'
        USING ERRCODE = '42501';
    END IF;
    NEW.reviewed_at := now();

  ELSIF OLD.status IN ('DRAFT', 'SUBMITTED') AND NEW.status = 'CANCELED' THEN
    PERFORM fn_assert_is_actor(OLD.requester_id, 'canceller');

  ELSE
    RAISE EXCEPTION 'Invalid purchase request transition % -> %', OLD.status, NEW.status
      USING ERRCODE = 'P0409';
  END IF;

  -- nothing else may change together with a status transition
  IF NEW.reason IS DISTINCT FROM OLD.reason THEN
    RAISE EXCEPTION 'BR-11: reason cannot change after DRAFT' USING ERRCODE = '42501';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER tg_purchase_requests_workflow BEFORE INSERT OR UPDATE ON purchase_requests
  FOR EACH ROW EXECUTE FUNCTION trg_purchase_requests_workflow();
CREATE TRIGGER tg_purchase_requests_no_delete BEFORE DELETE ON purchase_requests
  FOR EACH ROW EXECUTE FUNCTION trg_block_delete();

-- Items are editable only while the PR is DRAFT; product must be active (BR-11, BR-12)
CREATE FUNCTION trg_purchase_request_items_guard() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  v_pr_id  bigint := COALESCE(NEW.purchase_request_id, OLD.purchase_request_id);
  v_status purchase_request_status;
BEGIN
  IF TG_OP = 'UPDATE' AND NEW.purchase_request_id <> OLD.purchase_request_id THEN
    RAISE EXCEPTION 'An item cannot be moved to another purchase request' USING ERRCODE = '42501';
  END IF;
  SELECT status INTO v_status FROM purchase_requests WHERE purchase_request_id = v_pr_id FOR UPDATE;
  IF v_status <> 'DRAFT' THEN
    RAISE EXCEPTION 'BR-11: items of a % purchase request cannot be changed', v_status USING ERRCODE = 'P0409';
  END IF;
  IF TG_OP <> 'DELETE' AND NOT EXISTS (SELECT 1 FROM products WHERE product_id = NEW.product_id AND is_active) THEN
    RAISE EXCEPTION 'BR-12: product % is inactive', NEW.product_id USING ERRCODE = '23514';
  END IF;
  RETURN COALESCE(NEW, OLD);
END $$;
CREATE TRIGGER tg_purchase_request_items_guard BEFORE INSERT OR UPDATE OR DELETE ON purchase_request_items
  FOR EACH ROW EXECUTE FUNCTION trg_purchase_request_items_guard();

-- ---------------------------------------------------------------------
-- 7. PURCHASE ORDER
-- ---------------------------------------------------------------------
CREATE TABLE purchase_orders (
  purchase_order_id       bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  order_code              varchar(30) NOT NULL UNIQUE
                          DEFAULT 'PO-' || to_char(now(), 'YYYY') || '-' || lpad(nextval('seq_po_code')::text, 5, '0'),
  -- BR-03 / BR-19: each PO references exactly one approved PR, and a PR becomes at most one PO
  purchase_request_id     bigint NOT NULL UNIQUE REFERENCES purchase_requests(purchase_request_id),
  supplier_id             bigint NOT NULL REFERENCES suppliers(supplier_id),
  created_by              bigint NOT NULL REFERENCES users(user_id),
  order_date              date NOT NULL DEFAULT current_date,
  expected_delivery_date  date,
  status                  purchase_order_status NOT NULL DEFAULT 'DRAFT',
  note                    text,
  cancel_reason           text,
  created_at              timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_po_delivery_date CHECK (expected_delivery_date IS NULL OR expected_delivery_date >= order_date),
  CONSTRAINT ck_po_cancel_reason CHECK (status <> 'CANCELED' OR btrim(coalesce(cancel_reason, '')) <> '')
);
CREATE INDEX ix_po_status ON purchase_orders (status);
CREATE INDEX ix_po_supplier ON purchase_orders (supplier_id);

CREATE TABLE purchase_order_items (
  purchase_order_item_id    bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  purchase_order_id         bigint NOT NULL REFERENCES purchase_orders(purchase_order_id),
  purchase_request_item_id  bigint NOT NULL REFERENCES purchase_request_items(purchase_request_item_id),
  quantity                  numeric(14,3) NOT NULL,
  CONSTRAINT ck_poi_qty_positive CHECK (quantity > 0),
  CONSTRAINT ux_poi_order_pr_item UNIQUE (purchase_order_id, purchase_request_item_id)
);
CREATE INDEX ix_poi_pr_item ON purchase_order_items (purchase_request_item_id);

CREATE FUNCTION trg_purchase_orders_workflow() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  v_pr_status purchase_request_status;
BEGIN
  IF TG_OP = 'INSERT' THEN
    IF NEW.status <> 'DRAFT' THEN
      RAISE EXCEPTION 'IA-IN07: a purchase order must be created as DRAFT' USING ERRCODE = '42501';
    END IF;
    PERFORM fn_assert_is_actor(NEW.created_by, 'created_by');
    PERFORM fn_assert_role(NEW.created_by, ARRAY['PURCHASING_STAFF']::user_role[], 'IA-IN02/BR-09');
    -- IA-IN03 / BR-02: PO only from an APPROVED PR (row lock prevents a race with cancel)
    SELECT status INTO v_pr_status FROM purchase_requests
     WHERE purchase_request_id = NEW.purchase_request_id FOR SHARE;
    IF v_pr_status IS DISTINCT FROM 'APPROVED' THEN
      RAISE EXCEPTION 'IA-IN03/BR-02: purchase request % is %, only APPROVED requests can become a PO',
        NEW.purchase_request_id, v_pr_status USING ERRCODE = 'P0409';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM suppliers WHERE supplier_id = NEW.supplier_id AND is_active) THEN
      RAISE EXCEPTION 'Supplier % is inactive', NEW.supplier_id USING ERRCODE = '23514';
    END IF;
    NEW.created_at := now();
    RETURN NEW;
  END IF;

  IF NEW.order_code <> OLD.order_code OR NEW.purchase_request_id <> OLD.purchase_request_id
     OR NEW.created_by <> OLD.created_by OR NEW.created_at <> OLD.created_at THEN
    RAISE EXCEPTION 'order_code, purchase_request_id, created_by and created_at are immutable' USING ERRCODE = '42501';
  END IF;

  IF NEW.status = OLD.status THEN
    IF OLD.status <> 'DRAFT' AND (NEW.supplier_id <> OLD.supplier_id OR NEW.order_date <> OLD.order_date) THEN
      RAISE EXCEPTION 'Supplier and order date are locked once the PO is %', OLD.status USING ERRCODE = '42501';
    END IF;
    RETURN NEW;
  END IF;

  IF OLD.status = 'DRAFT' AND NEW.status = 'ORDERED' THEN
    PERFORM fn_assert_role(COALESCE(fn_actor(), OLD.created_by), ARRAY['PURCHASING_STAFF']::user_role[], 'IA-IN02');
    IF NOT EXISTS (SELECT 1 FROM purchase_order_items WHERE purchase_order_id = OLD.purchase_order_id) THEN
      RAISE EXCEPTION 'A purchase order needs at least one item before ORDERED' USING ERRCODE = '23514';
    END IF;
  ELSIF OLD.status IN ('ORDERED', 'PARTIALLY_RECEIVED') AND NEW.status IN ('PARTIALLY_RECEIVED', 'RECEIVED') THEN
    -- receiving progress is set only by goods-receipt posting (BR-21)
    IF NOT fn_posting_on() THEN
      RAISE EXCEPTION 'BR-21: receiving status is set automatically by confirming goods receipts'
        USING ERRCODE = '42501';
    END IF;
  ELSIF OLD.status IN ('DRAFT', 'ORDERED') AND NEW.status = 'CANCELED' THEN
    PERFORM fn_assert_role(COALESCE(fn_actor(), OLD.created_by),
      ARRAY['PURCHASING_STAFF','MANAGER']::user_role[], 'IA-IN02');
    -- BR-18
    IF EXISTS (SELECT 1 FROM goods_receipts WHERE purchase_order_id = OLD.purchase_order_id AND status = 'CONFIRMED') THEN
      RAISE EXCEPTION 'BR-18: purchase order % already has a confirmed goods receipt', OLD.order_code
        USING ERRCODE = 'P0409';
    END IF;
  ELSE
    RAISE EXCEPTION 'BR-18/BR-21: invalid purchase order transition % -> %', OLD.status, NEW.status
      USING ERRCODE = 'P0409';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER tg_purchase_orders_workflow BEFORE INSERT OR UPDATE ON purchase_orders
  FOR EACH ROW EXECUTE FUNCTION trg_purchase_orders_workflow();
CREATE TRIGGER tg_purchase_orders_no_delete BEFORE DELETE ON purchase_orders
  FOR EACH ROW EXECUTE FUNCTION trg_block_delete();

-- PO item must belong to the PO's PR and not exceed the requested quantity
CREATE FUNCTION trg_purchase_order_items_guard() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  v_po_id     bigint := COALESCE(NEW.purchase_order_id, OLD.purchase_order_id);
  v_status    purchase_order_status;
  v_po_pr_id  bigint;
  v_item_pr   bigint;
  v_req_qty   numeric;
BEGIN
  IF TG_OP = 'UPDATE' AND (NEW.purchase_order_id <> OLD.purchase_order_id
                           OR NEW.purchase_request_item_id <> OLD.purchase_request_item_id) THEN
    RAISE EXCEPTION 'A purchase order item cannot be re-pointed' USING ERRCODE = '42501';
  END IF;
  SELECT status, purchase_request_id INTO v_status, v_po_pr_id
    FROM purchase_orders WHERE purchase_order_id = v_po_id FOR UPDATE;
  IF v_status <> 'DRAFT' THEN
    RAISE EXCEPTION 'Items of a % purchase order cannot be changed', v_status USING ERRCODE = 'P0409';
  END IF;
  IF TG_OP <> 'DELETE' THEN
    SELECT purchase_request_id, quantity INTO v_item_pr, v_req_qty
      FROM purchase_request_items WHERE purchase_request_item_id = NEW.purchase_request_item_id;
    IF v_item_pr IS DISTINCT FROM v_po_pr_id THEN
      RAISE EXCEPTION 'IA-IN03: item % does not belong to the purchase request of this PO',
        NEW.purchase_request_item_id USING ERRCODE = '42501';
    END IF;
    IF NEW.quantity > v_req_qty THEN
      RAISE EXCEPTION 'IA-IN04: ordered quantity % exceeds approved quantity %', NEW.quantity, v_req_qty
        USING ERRCODE = '23514';
    END IF;
  END IF;
  RETURN COALESCE(NEW, OLD);
END $$;
CREATE TRIGGER tg_purchase_order_items_guard BEFORE INSERT OR UPDATE OR DELETE ON purchase_order_items
  FOR EACH ROW EXECUTE FUNCTION trg_purchase_order_items_guard();

-- ---------------------------------------------------------------------
-- 8. GOODS RECEIPT
-- ---------------------------------------------------------------------
CREATE TABLE goods_receipts (
  goods_receipt_id    bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  receipt_code        varchar(30) NOT NULL UNIQUE
                      DEFAULT 'GR-' || to_char(now(), 'YYYY') || '-' || lpad(nextval('seq_gr_code')::text, 5, '0'),
  purchase_order_id   bigint NOT NULL REFERENCES purchase_orders(purchase_order_id),
  received_by         bigint NOT NULL REFERENCES users(user_id),   -- who prepared the receipt
  received_at         timestamptz,                                   -- set on confirm
  confirmed_by        bigint REFERENCES users(user_id),
  confirmed_at        timestamptz,
  status              document_status NOT NULL DEFAULT 'DRAFT',
  document_reference  varchar(100),                                  -- supplier delivery note / invoice no.
  note                text,
  cancel_reason       text,
  created_at          timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_gr_confirm_fields CHECK ((status = 'CONFIRMED') = (confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL)),
  -- IN10 / SCR-33: a confirmed receipt must carry the delivery document number
  CONSTRAINT ck_gr_document_reference CHECK (status <> 'CONFIRMED' OR btrim(coalesce(document_reference, '')) <> ''),
  CONSTRAINT ck_gr_cancel_reason CHECK (status <> 'CANCELED' OR btrim(coalesce(cancel_reason, '')) <> '')
);
CREATE INDEX ix_gr_po ON goods_receipts (purchase_order_id);
CREATE INDEX ix_gr_status ON goods_receipts (status);

CREATE TABLE goods_receipt_items (
  goods_receipt_item_id   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  goods_receipt_id        bigint NOT NULL REFERENCES goods_receipts(goods_receipt_id),
  purchase_order_item_id  bigint NOT NULL REFERENCES purchase_order_items(purchase_order_item_id),
  received_quantity       numeric(14,3) NOT NULL,
  CONSTRAINT ck_gri_qty_positive CHECK (received_quantity > 0),
  CONSTRAINT ux_gri_receipt_po_item UNIQUE (goods_receipt_id, purchase_order_item_id)
);
CREATE INDEX ix_gri_po_item ON goods_receipt_items (purchase_order_item_id);

-- ---------------------------------------------------------------------
-- 9. STOCK ISSUE
-- ---------------------------------------------------------------------
CREATE TABLE stock_issues (
  stock_issue_id  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  issue_code      varchar(30) NOT NULL UNIQUE
                  DEFAULT 'SI-' || to_char(now(), 'YYYY') || '-' || lpad(nextval('seq_si_code')::text, 5, '0'),
  requested_by    bigint NOT NULL REFERENCES users(user_id),
  confirmed_by    bigint REFERENCES users(user_id),
  status          document_status NOT NULL DEFAULT 'DRAFT',
  requested_at    timestamptz NOT NULL DEFAULT now(),
  confirmed_at    timestamptz,
  reason          text NOT NULL,
  note            text,
  cancel_reason   text,
  CONSTRAINT ck_si_reason_not_blank CHECK (btrim(reason) <> ''),
  CONSTRAINT ck_si_confirm_fields CHECK ((status = 'CONFIRMED') = (confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL)),
  -- SoD: the person who asks for goods does not confirm handing them out
  CONSTRAINT ck_si_no_self_confirm CHECK (confirmed_by IS NULL OR confirmed_by <> requested_by),
  CONSTRAINT ck_si_cancel_reason CHECK (status <> 'CANCELED' OR btrim(coalesce(cancel_reason, '')) <> '')
);
CREATE INDEX ix_si_status ON stock_issues (status);
CREATE INDEX ix_si_requested_by ON stock_issues (requested_by);

CREATE TABLE stock_issue_items (
  stock_issue_item_id  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  stock_issue_id       bigint NOT NULL REFERENCES stock_issues(stock_issue_id),
  product_id           bigint NOT NULL REFERENCES products(product_id),
  quantity             numeric(14,3) NOT NULL,
  CONSTRAINT ck_sii_qty_positive CHECK (quantity > 0),
  CONSTRAINT ux_sii_issue_product UNIQUE (stock_issue_id, product_id)
);
CREATE INDEX ix_sii_product ON stock_issue_items (product_id);

-- ---------------------------------------------------------------------
-- 10. STOCK ADJUSTMENT (opening stock, stocktake IN18, correction) - BR-22
-- ---------------------------------------------------------------------
CREATE TABLE stock_adjustments (
  stock_adjustment_id  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  adjustment_code      varchar(30) NOT NULL UNIQUE
                       DEFAULT 'SA-' || to_char(now(), 'YYYY') || '-' || lpad(nextval('seq_sa_code')::text, 5, '0'),
  adjustment_type      stock_adjustment_type NOT NULL,
  created_by           bigint NOT NULL REFERENCES users(user_id),
  confirmed_by         bigint REFERENCES users(user_id),
  status               document_status NOT NULL DEFAULT 'DRAFT',
  reason               text NOT NULL,
  cancel_reason        text,
  created_at           timestamptz NOT NULL DEFAULT now(),
  confirmed_at         timestamptz,
  CONSTRAINT ck_sa_reason_not_blank CHECK (btrim(reason) <> ''),
  CONSTRAINT ck_sa_confirm_fields CHECK ((status = 'CONFIRMED') = (confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL)),
  CONSTRAINT ck_sa_no_self_confirm CHECK (confirmed_by IS NULL OR confirmed_by <> created_by),
  CONSTRAINT ck_sa_cancel_reason CHECK (status <> 'CANCELED' OR btrim(coalesce(cancel_reason, '')) <> '')
);

CREATE TABLE stock_adjustment_items (
  stock_adjustment_item_id  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  stock_adjustment_id       bigint NOT NULL REFERENCES stock_adjustments(stock_adjustment_id),
  product_id                bigint NOT NULL REFERENCES products(product_id),
  counted_quantity          numeric(14,3) NOT NULL,     -- physical quantity; balance is set to this value
  system_quantity           numeric(14,3),              -- snapshot taken at confirm time
  note                      text,
  CONSTRAINT ck_sai_counted_non_negative CHECK (counted_quantity >= 0),
  CONSTRAINT ux_sai_adjustment_product UNIQUE (stock_adjustment_id, product_id)
);

-- ---------------------------------------------------------------------
-- 11. STOCK LEDGER (append-only, hash-chained) - BR-07, IA-IN05, IA-IN06
-- ---------------------------------------------------------------------
CREATE TABLE stock_transactions (
  stock_transaction_id      bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  chain_seq                 bigint NOT NULL UNIQUE,          -- gap-free order of the hash chain
  product_id                bigint NOT NULL REFERENCES products(product_id),
  transaction_type          stock_transaction_type NOT NULL,
  quantity                  numeric(14,3) NOT NULL,
  balance_after             numeric(14,3) NOT NULL,
  goods_receipt_item_id     bigint UNIQUE REFERENCES goods_receipt_items(goods_receipt_item_id),
  stock_issue_item_id       bigint UNIQUE REFERENCES stock_issue_items(stock_issue_item_id),
  stock_adjustment_item_id  bigint UNIQUE REFERENCES stock_adjustment_items(stock_adjustment_item_id),
  created_by                bigint NOT NULL REFERENCES users(user_id),
  created_at                timestamptz NOT NULL DEFAULT now(),
  note                      text,
  prev_hash                 char(64) NOT NULL,
  row_hash                  char(64) NOT NULL UNIQUE,
  CONSTRAINT ck_st_qty_positive CHECK (quantity > 0),
  CONSTRAINT ck_st_balance_non_negative CHECK (balance_after >= 0),
  -- exactly one source document, and it must match the transaction type
  CONSTRAINT ck_st_single_source CHECK (num_nonnulls(goods_receipt_item_id, stock_issue_item_id, stock_adjustment_item_id) = 1),
  CONSTRAINT ck_st_type_matches_source CHECK (
       (transaction_type = 'IN'  AND goods_receipt_item_id IS NOT NULL)
    OR (transaction_type = 'OUT' AND stock_issue_item_id   IS NOT NULL)
    OR (transaction_type IN ('ADJUST_IN', 'ADJUST_OUT') AND stock_adjustment_item_id IS NOT NULL)
  )
);
CREATE INDEX ix_st_product_time ON stock_transactions (product_id, created_at);

-- ---------------------------------------------------------------------
-- 12. AUDIT LOG (append-only, hash-chained) - BR-17, NFR05, IA-IN05, IA-IN06
-- ---------------------------------------------------------------------
CREATE TABLE audit_logs (
  audit_log_id  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  chain_seq     bigint NOT NULL UNIQUE,
  occurred_at   timestamptz NOT NULL DEFAULT now(),
  actor_id      bigint REFERENCES users(user_id),
  action        varchar(40) NOT NULL,          -- INSERT, UPDATE, DELETE, STATUS_CHANGE, LOGIN, ACCESS_DENIED, ...
  result        varchar(10) NOT NULL DEFAULT 'SUCCESS',
  entity_type   varchar(60) NOT NULL,
  entity_id     bigint,
  entity_code   varchar(30),
  old_status    varchar(30),
  new_status    varchar(30),
  reason        text,
  changed_data  jsonb,                          -- {"column": {"old": .., "new": ..}}
  client_ip     inet,
  prev_hash     char(64) NOT NULL,
  row_hash      char(64) NOT NULL UNIQUE,
  CONSTRAINT ck_audit_result CHECK (result IN ('SUCCESS', 'DENIED', 'FAILED'))
);
CREATE INDEX ix_audit_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX ix_audit_actor_time ON audit_logs (actor_id, occurred_at);
CREATE INDEX ix_audit_action_time ON audit_logs (action, occurred_at);

-- Hash helpers. A single advisory lock per chain keeps chain_seq gap-free.
CREATE FUNCTION fn_sha256(p text) RETURNS char(64)
LANGUAGE sql IMMUTABLE AS $$ SELECT encode(sha256(convert_to(p, 'UTF8')), 'hex') $$;

CREATE FUNCTION fn_stock_tx_payload(r stock_transactions) RETURNS text
LANGUAGE sql IMMUTABLE AS $$
  SELECT concat_ws('|', r.chain_seq, r.product_id, r.transaction_type, r.quantity, r.balance_after,
                   r.goods_receipt_item_id, r.stock_issue_item_id, r.stock_adjustment_item_id,
                   r.created_by, extract(epoch FROM r.created_at), r.note)
$$;

CREATE FUNCTION fn_audit_payload(r audit_logs) RETURNS text
LANGUAGE sql IMMUTABLE AS $$
  SELECT concat_ws('|', r.chain_seq, extract(epoch FROM r.occurred_at), r.actor_id, r.action, r.result,
                   r.entity_type, r.entity_id, r.entity_code, r.old_status, r.new_status, r.reason,
                   r.changed_data::text, host(r.client_ip))
$$;

CREATE FUNCTION trg_stock_transactions_chain() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  v_last_seq  bigint;
  v_last_hash char(64);
BEGIN
  IF NOT fn_posting_on() THEN
    RAISE EXCEPTION 'BR-04/IA-IN06: ledger rows are written only by document posting' USING ERRCODE = '42501';
  END IF;
  PERFORM pg_advisory_xact_lock(hashtext('stock_transactions_chain'));
  SELECT chain_seq, row_hash INTO v_last_seq, v_last_hash
    FROM stock_transactions ORDER BY chain_seq DESC LIMIT 1;
  NEW.chain_seq  := COALESCE(v_last_seq, 0) + 1;
  NEW.prev_hash  := COALESCE(v_last_hash, repeat('0', 64));
  NEW.created_at := now();
  NEW.row_hash   := fn_sha256(NEW.prev_hash || '|' || fn_stock_tx_payload(NEW));
  RETURN NEW;
END $$;
CREATE TRIGGER tg_stock_transactions_chain BEFORE INSERT ON stock_transactions
  FOR EACH ROW EXECUTE FUNCTION trg_stock_transactions_chain();
CREATE TRIGGER tg_stock_transactions_append_only BEFORE UPDATE OR DELETE ON stock_transactions
  FOR EACH ROW EXECUTE FUNCTION trg_append_only();
CREATE TRIGGER tg_stock_transactions_no_truncate BEFORE TRUNCATE ON stock_transactions
  FOR EACH STATEMENT EXECUTE FUNCTION trg_append_only();

CREATE FUNCTION trg_audit_logs_chain() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  v_last_seq  bigint;
  v_last_hash char(64);
BEGIN
  PERFORM pg_advisory_xact_lock(hashtext('audit_logs_chain'));
  SELECT chain_seq, row_hash INTO v_last_seq, v_last_hash
    FROM audit_logs ORDER BY chain_seq DESC LIMIT 1;
  NEW.chain_seq   := COALESCE(v_last_seq, 0) + 1;
  NEW.prev_hash   := COALESCE(v_last_hash, repeat('0', 64));
  NEW.occurred_at := now();
  NEW.row_hash    := fn_sha256(NEW.prev_hash || '|' || fn_audit_payload(NEW));
  RETURN NEW;
END $$;
CREATE TRIGGER tg_audit_logs_chain BEFORE INSERT ON audit_logs
  FOR EACH ROW EXECUTE FUNCTION trg_audit_logs_chain();
CREATE TRIGGER tg_audit_logs_append_only BEFORE UPDATE OR DELETE ON audit_logs
  FOR EACH ROW EXECUTE FUNCTION trg_append_only();
CREATE TRIGGER tg_audit_logs_no_truncate BEFORE TRUNCATE ON audit_logs
  FOR EACH STATEMENT EXECUTE FUNCTION trg_append_only();

-- Generic data-change audit for every business table
CREATE FUNCTION trg_audit_row() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp AS $$
DECLARE
  v_old     jsonb := CASE WHEN TG_OP <> 'INSERT' THEN to_jsonb(OLD) END;
  v_new     jsonb := CASE WHEN TG_OP <> 'DELETE' THEN to_jsonb(NEW) END;
  v_row     jsonb := COALESCE(v_new, v_old);
  v_pk      text  := TG_ARGV[0];
  v_code    text  := TG_ARGV[1];
  v_diff    jsonb;
  v_action  text  := TG_OP;
  v_actor   bigint;
  v_ostat   text  := v_old ->> 'status';
  v_nstat   text  := v_new ->> 'status';
BEGIN
  SELECT jsonb_object_agg(k,
           CASE WHEN k = 'password_hash' THEN jsonb_build_object('old', '***', 'new', '***')
                ELSE jsonb_build_object('old', v_old -> k, 'new', v_new -> k) END)
    INTO v_diff
    FROM (SELECT jsonb_object_keys(COALESCE(v_new, v_old)) AS k) keys
   WHERE (v_old -> k) IS DISTINCT FROM (v_new -> k);

  IF TG_OP = 'UPDATE' AND v_diff IS NULL THEN
    RETURN NULL;                                   -- no real change
  END IF;
  IF TG_OP = 'UPDATE' AND v_ostat IS DISTINCT FROM v_nstat THEN
    v_action := 'STATUS_CHANGE';
  END IF;

  -- real actor from the session; otherwise the user column that matches the step
  v_actor := COALESCE(
    fn_actor(),
    CASE WHEN v_nstat IN ('APPROVED', 'REJECTED') THEN (v_row ->> 'reviewed_by')::bigint
         WHEN v_nstat = 'CONFIRMED'               THEN (v_row ->> 'confirmed_by')::bigint END,
    (v_row ->> 'created_by')::bigint, (v_row ->> 'requester_id')::bigint,
    (v_row ->> 'requested_by')::bigint, (v_row ->> 'received_by')::bigint);

  INSERT INTO audit_logs (actor_id, action, entity_type, entity_id, entity_code,
                          old_status, new_status, reason, changed_data, client_ip)
  VALUES (v_actor, v_action, TG_TABLE_NAME, (v_row ->> v_pk)::bigint,
          CASE WHEN v_code IS NOT NULL THEN v_row ->> v_code END,
          v_ostat, v_nstat,
          CASE WHEN v_action = 'STATUS_CHANGE'
               THEN COALESCE(CASE WHEN v_nstat = 'CANCELED' THEN v_row ->> 'cancel_reason' END,
                             v_row ->> 'review_reason') END,
          v_diff,
          NULLIF(current_setting('app.client_ip', true), '')::inet);
  RETURN NULL;
END $$;

DO $$
DECLARE
  t record;
BEGIN
  FOR t IN SELECT * FROM (VALUES
      ('users',                  'user_id',                  NULL),
      ('categories',             'category_id',              NULL),
      ('products',               'product_id',               'sku'),
      ('suppliers',              'supplier_id',              NULL),
      ('stock_balances',         'product_id',               NULL),
      ('purchase_requests',      'purchase_request_id',      'request_code'),
      ('purchase_request_items', 'purchase_request_item_id', NULL),
      ('purchase_orders',        'purchase_order_id',        'order_code'),
      ('purchase_order_items',   'purchase_order_item_id',   NULL),
      ('goods_receipts',         'goods_receipt_id',         'receipt_code'),
      ('goods_receipt_items',    'goods_receipt_item_id',    NULL),
      ('stock_issues',           'stock_issue_id',           'issue_code'),
      ('stock_issue_items',      'stock_issue_item_id',      NULL),
      ('stock_adjustments',      'stock_adjustment_id',      'adjustment_code'),
      ('stock_adjustment_items', 'stock_adjustment_item_id', NULL)
    ) AS v(tbl, pk, code)
  LOOP
    EXECUTE format(
      'CREATE TRIGGER tg_%1$s_audit AFTER INSERT OR UPDATE OR DELETE ON %1$I
         FOR EACH ROW EXECUTE FUNCTION trg_audit_row(%2$L, %3$L)', t.tbl, t.pk, t.code);
  END LOOP;
END $$;

-- Security events from the backend: login, logout, access denied, ... (IA-IN05, SEC-TC-302)
CREATE FUNCTION fn_log_security_event(
  p_action      varchar,
  p_result      varchar,
  p_actor_id    bigint DEFAULT NULL,
  p_entity_type varchar DEFAULT 'security',
  p_entity_id   bigint DEFAULT NULL,
  p_reason      text DEFAULT NULL,
  p_details     jsonb DEFAULT NULL
) RETURNS bigint
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp AS $$
DECLARE
  v_id bigint;
BEGIN
  INSERT INTO audit_logs (actor_id, action, result, entity_type, entity_id, reason, changed_data, client_ip)
  VALUES (COALESCE(p_actor_id, fn_actor()), upper(p_action), upper(p_result), p_entity_type, p_entity_id,
          p_reason, p_details, NULLIF(current_setting('app.client_ip', true), '')::inet)
  RETURNING audit_log_id INTO v_id;
  RETURN v_id;
END $$;

-- ---------------------------------------------------------------------
-- 13. POSTING: goods receipt, stock issue, stock adjustment
--     All checks + ledger + balance happen in ONE transaction (IA-IN04)
-- ---------------------------------------------------------------------

-- Insert one ledger row and move the balance. Locks the balance row.
CREATE FUNCTION fn_post_stock(
  p_product bigint, p_type stock_transaction_type, p_qty numeric, p_user bigint,
  p_gri bigint, p_sii bigint, p_sai bigint, p_note text
) RETURNS numeric
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp AS $$
DECLARE
  v_new_qty numeric;
BEGIN
  PERFORM set_config('app.posting', 'on', true);
  IF p_type IN ('IN', 'ADJUST_IN') THEN
    UPDATE stock_balances SET quantity = quantity + p_qty
     WHERE product_id = p_product RETURNING quantity INTO v_new_qty;
  ELSE
    -- BR-05 / IA-IN04: row lock + condition; a concurrent issue waits and re-checks
    UPDATE stock_balances SET quantity = quantity - p_qty
     WHERE product_id = p_product AND quantity >= p_qty RETURNING quantity INTO v_new_qty;
    IF NOT FOUND THEN
      RAISE EXCEPTION 'BR-05/IA-IN04: insufficient stock for product % (requested %, available %)',
        p_product, p_qty, (SELECT quantity FROM stock_balances WHERE product_id = p_product)
        USING ERRCODE = 'P0409';
    END IF;
  END IF;
  INSERT INTO stock_transactions (product_id, transaction_type, quantity, balance_after,
                                  goods_receipt_item_id, stock_issue_item_id, stock_adjustment_item_id,
                                  created_by, note)
  VALUES (p_product, p_type, p_qty, v_new_qty, p_gri, p_sii, p_sai, p_user, p_note);
  PERFORM set_config('app.posting', 'off', true);
  RETURN v_new_qty;
END $$;

-- ---- Goods receipt ---------------------------------------------------
CREATE FUNCTION trg_goods_receipts_workflow() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  v_po_status purchase_order_status;
BEGIN
  IF TG_OP = 'INSERT' THEN
    IF NEW.status <> 'DRAFT' OR NEW.confirmed_by IS NOT NULL OR NEW.confirmed_at IS NOT NULL THEN
      RAISE EXCEPTION 'IA-IN07: a goods receipt must be created as DRAFT' USING ERRCODE = '42501';
    END IF;
    PERFORM fn_assert_is_actor(NEW.received_by, 'received_by');
    PERFORM fn_assert_role(NEW.received_by, ARRAY['WAREHOUSE_STAFF']::user_role[], 'IN09');
    SELECT status INTO v_po_status FROM purchase_orders WHERE purchase_order_id = NEW.purchase_order_id;
    IF v_po_status NOT IN ('ORDERED', 'PARTIALLY_RECEIVED') THEN
      RAISE EXCEPTION 'IN09: goods can only be received for an ORDERED or PARTIALLY_RECEIVED PO (PO is %)', v_po_status
        USING ERRCODE = 'P0409';
    END IF;
    NEW.created_at := now();
    NEW.received_at := NULL;
    RETURN NEW;
  END IF;

  IF NEW.receipt_code <> OLD.receipt_code OR NEW.purchase_order_id <> OLD.purchase_order_id
     OR NEW.received_by <> OLD.received_by OR NEW.created_at <> OLD.created_at THEN
    RAISE EXCEPTION 'receipt_code, purchase_order_id, received_by and created_at are immutable' USING ERRCODE = '42501';
  END IF;

  IF NEW.status = OLD.status THEN
    IF OLD.status <> 'DRAFT' THEN
      RAISE EXCEPTION 'BR-04: a % goods receipt is locked', OLD.status USING ERRCODE = 'P0409';
    END IF;
    IF NEW.confirmed_by IS NOT NULL THEN
      RAISE EXCEPTION 'IA-IN07: confirmed_by can only be set by confirming' USING ERRCODE = '42501';
    END IF;
    RETURN NEW;
  END IF;

  IF OLD.status = 'DRAFT' AND NEW.status = 'CONFIRMED' THEN
    PERFORM fn_assert_is_actor(NEW.confirmed_by, 'confirmed_by');
    PERFORM fn_assert_role(NEW.confirmed_by, ARRAY['WAREHOUSE_STAFF']::user_role[], 'IN10');
    IF NOT EXISTS (SELECT 1 FROM goods_receipt_items WHERE goods_receipt_id = OLD.goods_receipt_id) THEN
      RAISE EXCEPTION 'IN10: a goods receipt needs at least one item' USING ERRCODE = '23514';
    END IF;
    SELECT status INTO v_po_status FROM purchase_orders WHERE purchase_order_id = OLD.purchase_order_id FOR UPDATE;
    IF v_po_status NOT IN ('ORDERED', 'PARTIALLY_RECEIVED') THEN
      RAISE EXCEPTION 'IN09: PO is %, cannot confirm receipt', v_po_status USING ERRCODE = 'P0409';
    END IF;
    NEW.confirmed_at := now();
    NEW.received_at  := COALESCE(NEW.received_at, now());
  ELSIF OLD.status = 'DRAFT' AND NEW.status = 'CANCELED' THEN
    NULL;
  ELSE
    RAISE EXCEPTION 'BR-16: invalid goods receipt transition % -> %', OLD.status, NEW.status USING ERRCODE = 'P0409';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER tg_goods_receipts_workflow BEFORE INSERT OR UPDATE ON goods_receipts
  FOR EACH ROW EXECUTE FUNCTION trg_goods_receipts_workflow();
CREATE TRIGGER tg_goods_receipts_no_delete BEFORE DELETE ON goods_receipts
  FOR EACH ROW EXECUTE FUNCTION trg_block_delete();

CREATE FUNCTION trg_goods_receipts_post() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp AS $$
DECLARE
  r              record;
  v_received     numeric;
  v_all_received boolean;
BEGIN
  FOR r IN
    SELECT gri.goods_receipt_item_id, gri.received_quantity, poi.purchase_order_item_id,
           poi.quantity AS ordered_qty, pri.product_id
      FROM goods_receipt_items gri
      JOIN purchase_order_items poi   ON poi.purchase_order_item_id = gri.purchase_order_item_id
      JOIN purchase_request_items pri ON pri.purchase_request_item_id = poi.purchase_request_item_id
     WHERE gri.goods_receipt_id = NEW.goods_receipt_id
     ORDER BY pri.product_id                    -- fixed lock order avoids deadlocks
       FOR UPDATE OF poi
  LOOP
    -- BR-06 / BR-21: cumulative receipts must not exceed the ordered quantity
    SELECT COALESCE(sum(gri2.received_quantity), 0) INTO v_received
      FROM goods_receipt_items gri2
      JOIN goods_receipts gr2 ON gr2.goods_receipt_id = gri2.goods_receipt_id
     WHERE gri2.purchase_order_item_id = r.purchase_order_item_id
       AND (gr2.status = 'CONFIRMED' AND gr2.goods_receipt_id <> NEW.goods_receipt_id);
    IF v_received + r.received_quantity > r.ordered_qty THEN
      RAISE EXCEPTION 'BR-06/BR-21: receiving % would exceed ordered quantity % (already received %)',
        r.received_quantity, r.ordered_qty, v_received USING ERRCODE = 'P0409';
    END IF;
    PERFORM fn_post_stock(r.product_id, 'IN', r.received_quantity, NEW.confirmed_by,
                          r.goods_receipt_item_id, NULL, NULL, NEW.receipt_code);
  END LOOP;

  -- BR-21: PO status reflects receiving progress
  SELECT bool_and(COALESCE(rcv.qty, 0) >= poi.quantity) INTO v_all_received
    FROM purchase_order_items poi
    LEFT JOIN (SELECT gri.purchase_order_item_id, sum(gri.received_quantity) AS qty
                 FROM goods_receipt_items gri
                 JOIN goods_receipts gr ON gr.goods_receipt_id = gri.goods_receipt_id
                WHERE gr.status = 'CONFIRMED'
                GROUP BY gri.purchase_order_item_id) rcv
      ON rcv.purchase_order_item_id = poi.purchase_order_item_id
   WHERE poi.purchase_order_id = NEW.purchase_order_id;

  PERFORM set_config('app.posting', 'on', true);
  UPDATE purchase_orders
     SET status = CASE WHEN v_all_received THEN 'RECEIVED' ELSE 'PARTIALLY_RECEIVED' END::purchase_order_status
   WHERE purchase_order_id = NEW.purchase_order_id
     AND status <> CASE WHEN v_all_received THEN 'RECEIVED' ELSE 'PARTIALLY_RECEIVED' END::purchase_order_status;
  PERFORM set_config('app.posting', 'off', true);
  RETURN NULL;
END $$;
CREATE TRIGGER tg_goods_receipts_post AFTER UPDATE OF status ON goods_receipts
  FOR EACH ROW WHEN (OLD.status = 'DRAFT' AND NEW.status = 'CONFIRMED')
  EXECUTE FUNCTION trg_goods_receipts_post();

CREATE FUNCTION trg_goods_receipt_items_guard() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  v_gr_id     bigint := COALESCE(NEW.goods_receipt_id, OLD.goods_receipt_id);
  v_status    document_status;
  v_gr_po     bigint;
  v_item_po   bigint;
BEGIN
  IF TG_OP = 'UPDATE' AND (NEW.goods_receipt_id <> OLD.goods_receipt_id
                           OR NEW.purchase_order_item_id <> OLD.purchase_order_item_id) THEN
    RAISE EXCEPTION 'A goods receipt item cannot be re-pointed' USING ERRCODE = '42501';
  END IF;
  SELECT status, purchase_order_id INTO v_status, v_gr_po
    FROM goods_receipts WHERE goods_receipt_id = v_gr_id FOR UPDATE;
  IF v_status <> 'DRAFT' THEN
    RAISE EXCEPTION 'BR-04: items of a % goods receipt cannot be changed', v_status USING ERRCODE = 'P0409';
  END IF;
  IF TG_OP <> 'DELETE' THEN
    SELECT purchase_order_id INTO v_item_po FROM purchase_order_items
     WHERE purchase_order_item_id = NEW.purchase_order_item_id;
    IF v_item_po IS DISTINCT FROM v_gr_po THEN
      RAISE EXCEPTION 'IN09: PO item % does not belong to the PO of this receipt', NEW.purchase_order_item_id
        USING ERRCODE = '42501';
    END IF;
  END IF;
  RETURN COALESCE(NEW, OLD);
END $$;
CREATE TRIGGER tg_goods_receipt_items_guard BEFORE INSERT OR UPDATE OR DELETE ON goods_receipt_items
  FOR EACH ROW EXECUTE FUNCTION trg_goods_receipt_items_guard();

-- ---- Stock issue -----------------------------------------------------
CREATE FUNCTION trg_stock_issues_workflow() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'INSERT' THEN
    IF NEW.status <> 'DRAFT' OR NEW.confirmed_by IS NOT NULL OR NEW.confirmed_at IS NOT NULL THEN
      RAISE EXCEPTION 'IA-IN07: a stock issue must be created as DRAFT' USING ERRCODE = '42501';
    END IF;
    PERFORM fn_assert_is_actor(NEW.requested_by, 'requested_by');
    PERFORM fn_assert_role(NEW.requested_by,
      ARRAY['REQUESTER','WAREHOUSE_STAFF','PURCHASING_STAFF','MANAGER']::user_role[], 'IN11');
    NEW.requested_at := now();
    RETURN NEW;
  END IF;

  IF NEW.issue_code <> OLD.issue_code OR NEW.requested_by <> OLD.requested_by
     OR NEW.requested_at <> OLD.requested_at THEN
    RAISE EXCEPTION 'issue_code, requested_by and requested_at are immutable' USING ERRCODE = '42501';
  END IF;

  IF NEW.status = OLD.status THEN
    IF OLD.status <> 'DRAFT' THEN
      RAISE EXCEPTION 'BR-04: a % stock issue is locked', OLD.status USING ERRCODE = 'P0409';
    END IF;
    IF NEW.confirmed_by IS NOT NULL THEN
      RAISE EXCEPTION 'IA-IN07: confirmed_by can only be set by confirming' USING ERRCODE = '42501';
    END IF;
    RETURN NEW;
  END IF;

  IF OLD.status = 'DRAFT' AND NEW.status = 'CONFIRMED' THEN
    PERFORM fn_assert_is_actor(NEW.confirmed_by, 'confirmed_by');
    PERFORM fn_assert_role(NEW.confirmed_by, ARRAY['WAREHOUSE_STAFF']::user_role[], 'IN12');
    IF NOT EXISTS (SELECT 1 FROM stock_issue_items WHERE stock_issue_id = OLD.stock_issue_id) THEN
      RAISE EXCEPTION 'IN12: a stock issue needs at least one item' USING ERRCODE = '23514';
    END IF;
    NEW.confirmed_at := now();
  ELSIF OLD.status = 'DRAFT' AND NEW.status = 'CANCELED' THEN
    NULL;   -- requester withdraws, or warehouse rejects with cancel_reason
  ELSE
    RAISE EXCEPTION 'BR-16: invalid stock issue transition % -> %', OLD.status, NEW.status USING ERRCODE = 'P0409';
  END IF;
  IF NEW.reason IS DISTINCT FROM OLD.reason THEN
    RAISE EXCEPTION 'reason cannot change together with a status transition' USING ERRCODE = '42501';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER tg_stock_issues_workflow BEFORE INSERT OR UPDATE ON stock_issues
  FOR EACH ROW EXECUTE FUNCTION trg_stock_issues_workflow();
CREATE TRIGGER tg_stock_issues_no_delete BEFORE DELETE ON stock_issues
  FOR EACH ROW EXECUTE FUNCTION trg_block_delete();

CREATE FUNCTION trg_stock_issues_post() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp AS $$
DECLARE
  r record;
BEGIN
  FOR r IN SELECT stock_issue_item_id, product_id, quantity
             FROM stock_issue_items WHERE stock_issue_id = NEW.stock_issue_id
            ORDER BY product_id
  LOOP
    PERFORM fn_post_stock(r.product_id, 'OUT', r.quantity, NEW.confirmed_by,
                          NULL, r.stock_issue_item_id, NULL, NEW.issue_code);
  END LOOP;
  RETURN NULL;
END $$;
CREATE TRIGGER tg_stock_issues_post AFTER UPDATE OF status ON stock_issues
  FOR EACH ROW WHEN (OLD.status = 'DRAFT' AND NEW.status = 'CONFIRMED')
  EXECUTE FUNCTION trg_stock_issues_post();

CREATE FUNCTION trg_stock_issue_items_guard() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  v_si_id  bigint := COALESCE(NEW.stock_issue_id, OLD.stock_issue_id);
  v_status document_status;
BEGIN
  IF TG_OP = 'UPDATE' AND NEW.stock_issue_id <> OLD.stock_issue_id THEN
    RAISE EXCEPTION 'A stock issue item cannot be moved' USING ERRCODE = '42501';
  END IF;
  SELECT status INTO v_status FROM stock_issues WHERE stock_issue_id = v_si_id FOR UPDATE;
  IF v_status <> 'DRAFT' THEN
    RAISE EXCEPTION 'BR-04: items of a % stock issue cannot be changed', v_status USING ERRCODE = 'P0409';
  END IF;
  IF TG_OP <> 'DELETE' AND NOT EXISTS (SELECT 1 FROM products WHERE product_id = NEW.product_id AND is_active) THEN
    RAISE EXCEPTION 'Product % is inactive', NEW.product_id USING ERRCODE = '23514';
  END IF;
  RETURN COALESCE(NEW, OLD);
END $$;
CREATE TRIGGER tg_stock_issue_items_guard BEFORE INSERT OR UPDATE OR DELETE ON stock_issue_items
  FOR EACH ROW EXECUTE FUNCTION trg_stock_issue_items_guard();

-- ---- Stock adjustment (BR-22) ---------------------------------------
CREATE FUNCTION trg_stock_adjustments_workflow() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'INSERT' THEN
    IF NEW.status <> 'DRAFT' OR NEW.confirmed_by IS NOT NULL OR NEW.confirmed_at IS NOT NULL THEN
      RAISE EXCEPTION 'IA-IN07: a stock adjustment must be created as DRAFT' USING ERRCODE = '42501';
    END IF;
    PERFORM fn_assert_is_actor(NEW.created_by, 'created_by');
    PERFORM fn_assert_role(NEW.created_by, ARRAY['WAREHOUSE_STAFF']::user_role[], 'BR-22');
    NEW.created_at := now();
    RETURN NEW;
  END IF;

  IF NEW.adjustment_code <> OLD.adjustment_code OR NEW.created_by <> OLD.created_by
     OR NEW.created_at <> OLD.created_at THEN
    RAISE EXCEPTION 'adjustment_code, created_by and created_at are immutable' USING ERRCODE = '42501';
  END IF;

  IF NEW.status = OLD.status THEN
    IF OLD.status <> 'DRAFT' THEN
      RAISE EXCEPTION 'BR-22: a % stock adjustment is locked', OLD.status USING ERRCODE = 'P0409';
    END IF;
    IF NEW.confirmed_by IS NOT NULL THEN
      RAISE EXCEPTION 'IA-IN07: confirmed_by can only be set by confirming' USING ERRCODE = '42501';
    END IF;
    RETURN NEW;
  END IF;

  IF OLD.status = 'DRAFT' AND NEW.status = 'CONFIRMED' THEN
    -- adjustments change stock without a supplier document, so a Manager approves them
    PERFORM fn_assert_is_actor(NEW.confirmed_by, 'confirmed_by');
    PERFORM fn_assert_role(NEW.confirmed_by, ARRAY['MANAGER']::user_role[], 'BR-22');
    IF NOT EXISTS (SELECT 1 FROM stock_adjustment_items WHERE stock_adjustment_id = OLD.stock_adjustment_id) THEN
      RAISE EXCEPTION 'BR-22: a stock adjustment needs at least one item' USING ERRCODE = '23514';
    END IF;
    NEW.confirmed_at := now();
  ELSIF OLD.status = 'DRAFT' AND NEW.status = 'CANCELED' THEN
    NULL;
  ELSE
    RAISE EXCEPTION 'BR-16: invalid stock adjustment transition % -> %', OLD.status, NEW.status USING ERRCODE = 'P0409';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER tg_stock_adjustments_workflow BEFORE INSERT OR UPDATE ON stock_adjustments
  FOR EACH ROW EXECUTE FUNCTION trg_stock_adjustments_workflow();
CREATE TRIGGER tg_stock_adjustments_no_delete BEFORE DELETE ON stock_adjustments
  FOR EACH ROW EXECUTE FUNCTION trg_block_delete();

CREATE FUNCTION trg_stock_adjustments_post() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp AS $$
DECLARE
  r        record;
  v_system numeric;
  v_diff   numeric;
BEGIN
  FOR r IN SELECT stock_adjustment_item_id, product_id, counted_quantity
             FROM stock_adjustment_items WHERE stock_adjustment_id = NEW.stock_adjustment_id
            ORDER BY product_id
  LOOP
    SELECT quantity INTO v_system FROM stock_balances WHERE product_id = r.product_id FOR UPDATE;
    IF NEW.adjustment_type = 'OPENING' AND v_system <> 0 THEN
      RAISE EXCEPTION 'BR-22: OPENING adjustment only for products with no stock yet (product %)', r.product_id
        USING ERRCODE = 'P0409';
    END IF;
    v_diff := r.counted_quantity - v_system;

    PERFORM set_config('app.posting', 'on', true);
    UPDATE stock_adjustment_items SET system_quantity = v_system
     WHERE stock_adjustment_item_id = r.stock_adjustment_item_id;

    IF v_diff > 0 THEN
      PERFORM fn_post_stock(r.product_id, 'ADJUST_IN', v_diff, NEW.confirmed_by,
                            NULL, NULL, r.stock_adjustment_item_id, NEW.adjustment_code || ' ' || NEW.adjustment_type);
    ELSIF v_diff < 0 THEN
      PERFORM fn_post_stock(r.product_id, 'ADJUST_OUT', -v_diff, NEW.confirmed_by,
                            NULL, NULL, r.stock_adjustment_item_id, NEW.adjustment_code || ' ' || NEW.adjustment_type);
    END IF;
    PERFORM set_config('app.posting', 'off', true);
  END LOOP;
  RETURN NULL;
END $$;
CREATE TRIGGER tg_stock_adjustments_post AFTER UPDATE OF status ON stock_adjustments
  FOR EACH ROW WHEN (OLD.status = 'DRAFT' AND NEW.status = 'CONFIRMED')
  EXECUTE FUNCTION trg_stock_adjustments_post();

CREATE FUNCTION trg_stock_adjustment_items_guard() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  v_sa_id  bigint := COALESCE(NEW.stock_adjustment_id, OLD.stock_adjustment_id);
  v_status document_status;
BEGIN
  IF fn_posting_on() AND TG_OP = 'UPDATE' THEN
    RETURN NEW;                       -- system_quantity snapshot written by posting
  END IF;
  IF TG_OP = 'UPDATE' AND NEW.stock_adjustment_id <> OLD.stock_adjustment_id THEN
    RAISE EXCEPTION 'An adjustment item cannot be moved' USING ERRCODE = '42501';
  END IF;
  SELECT status INTO v_status FROM stock_adjustments WHERE stock_adjustment_id = v_sa_id FOR UPDATE;
  IF v_status <> 'DRAFT' THEN
    RAISE EXCEPTION 'BR-22: items of a % stock adjustment cannot be changed', v_status USING ERRCODE = 'P0409';
  END IF;
  IF TG_OP <> 'DELETE' AND NEW.system_quantity IS NOT NULL THEN
    RAISE EXCEPTION 'IA-IN07: system_quantity is filled in by the system' USING ERRCODE = '42501';
  END IF;
  RETURN COALESCE(NEW, OLD);
END $$;
CREATE TRIGGER tg_stock_adjustment_items_guard BEFORE INSERT OR UPDATE OR DELETE ON stock_adjustment_items
  FOR EACH ROW EXECUTE FUNCTION trg_stock_adjustment_items_guard();

-- ---------------------------------------------------------------------
-- 14. MASTER DATA GUARDS
-- ---------------------------------------------------------------------
-- Master data and users are deactivated, not deleted (keeps FK history intact)
CREATE TRIGGER tg_users_no_delete      BEFORE DELETE ON users      FOR EACH ROW EXECUTE FUNCTION trg_block_delete();
CREATE TRIGGER tg_products_no_delete   BEFORE DELETE ON products   FOR EACH ROW EXECUTE FUNCTION trg_block_delete();
CREATE TRIGGER tg_suppliers_no_delete  BEFORE DELETE ON suppliers  FOR EACH ROW EXECUTE FUNCTION trg_block_delete();
CREATE TRIGGER tg_categories_no_delete BEFORE DELETE ON categories FOR EACH ROW EXECUTE FUNCTION trg_block_delete();

-- BR-01: SKU never changes once used in a document or the ledger
CREATE FUNCTION trg_products_sku_lock() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.sku <> OLD.sku AND (
       EXISTS (SELECT 1 FROM purchase_request_items WHERE product_id = OLD.product_id)
    OR EXISTS (SELECT 1 FROM stock_issue_items     WHERE product_id = OLD.product_id)
    OR EXISTS (SELECT 1 FROM stock_transactions    WHERE product_id = OLD.product_id)) THEN
    RAISE EXCEPTION 'BR-01: SKU % is already used in documents and cannot change', OLD.sku USING ERRCODE = '42501';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER tg_products_sku_lock BEFORE UPDATE OF sku ON products
  FOR EACH ROW EXECUTE FUNCTION trg_products_sku_lock();

-- ---------------------------------------------------------------------
-- 14b. ACTOR GUARDS FOR THE BACKEND ACCOUNT (Design section 6, IN01)
-- The checks apply to p03_app only; the owner still loads seed data.
-- ---------------------------------------------------------------------
CREATE FUNCTION trg_require_actor() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF current_user = 'p03_app' AND fn_actor() IS NULL THEN
    RAISE EXCEPTION 'IA-IN07: app.current_user_id is not set' USING ERRCODE = '42501';
  END IF;
  RETURN COALESCE(NEW, OLD);
END $$;

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['categories','products','suppliers','stock_balances',
                           'purchase_request_items','purchase_order_items','goods_receipt_items',
                           'stock_issue_items','stock_adjustment_items'] LOOP
    EXECUTE format('CREATE TRIGGER tg_00_require_actor BEFORE INSERT OR UPDATE OR DELETE ON %I
                    FOR EACH ROW EXECUTE FUNCTION trg_require_actor()', t);
  END LOOP;
END $$;

-- IN01: only a Manager creates accounts, locks them or changes roles.
-- Login bookkeeping (failed_login_count, locked_until, last_login_at) needs no actor.
CREATE FUNCTION trg_users_manager_only() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF current_user = 'p03_app'
     AND (TG_OP = 'INSERT' OR NEW.role_id IS DISTINCT FROM OLD.role_id
          OR NEW.is_active IS DISTINCT FROM OLD.is_active) THEN
    PERFORM fn_assert_role(fn_actor(), ARRAY['MANAGER']::user_role[], 'IN01');
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER tg_00_users_manager_only BEFORE INSERT OR UPDATE ON users
  FOR EACH ROW EXECUTE FUNCTION trg_users_manager_only();

-- ---------------------------------------------------------------------
-- 15. VIEWS for dashboard, low-stock alert and IA verification
-- ---------------------------------------------------------------------
-- IN14 / BR-14: products below minimum stock
CREATE VIEW v_low_stock AS
SELECT p.product_id, p.sku, p.product_name, c.category_name, p.unit,
       sb.quantity, sb.minimum_stock, sb.minimum_stock - sb.quantity AS shortage
  FROM stock_balances sb
  JOIN products p   ON p.product_id = sb.product_id
  JOIN categories c ON c.category_id = p.category_id
 WHERE p.is_active AND sb.quantity < sb.minimum_stock;

-- IN09 / IN17: ordered vs received per PO line
CREATE VIEW v_po_receiving_progress AS
SELECT po.purchase_order_id, po.order_code, po.status AS po_status,
       poi.purchase_order_item_id, p.sku, p.product_name,
       poi.quantity AS ordered_quantity,
       COALESCE(rcv.qty, 0) AS received_quantity,
       poi.quantity - COALESCE(rcv.qty, 0) AS remaining_quantity
  FROM purchase_orders po
  JOIN purchase_order_items poi   ON poi.purchase_order_id = po.purchase_order_id
  JOIN purchase_request_items pri ON pri.purchase_request_item_id = poi.purchase_request_item_id
  JOIN products p                 ON p.product_id = pri.product_id
  LEFT JOIN (SELECT gri.purchase_order_item_id, sum(gri.received_quantity) AS qty
               FROM goods_receipt_items gri
               JOIN goods_receipts gr ON gr.goods_receipt_id = gri.goods_receipt_id
              WHERE gr.status = 'CONFIRMED'
              GROUP BY gri.purchase_order_item_id) rcv
    ON rcv.purchase_order_item_id = poi.purchase_order_item_id;

-- IN16: dashboard counters
CREATE VIEW v_dashboard_summary AS
SELECT (SELECT count(*) FROM products WHERE is_active)                                AS active_products,
       (SELECT count(*) FROM v_low_stock)                                             AS low_stock_products,
       (SELECT count(*) FROM purchase_requests WHERE status = 'SUBMITTED')            AS pr_waiting_approval,
       (SELECT count(*) FROM purchase_orders WHERE status IN ('ORDERED', 'PARTIALLY_RECEIVED')) AS po_waiting_receipt,
       (SELECT count(*) FROM stock_issues WHERE status = 'DRAFT')                     AS issues_waiting_confirm;

-- IA-IN06: balance must equal the sum of the ledger
CREATE VIEW v_stock_reconciliation AS
SELECT sb.product_id, p.sku, sb.quantity AS balance_quantity,
       COALESCE(l.ledger_quantity, 0) AS ledger_quantity,
       sb.quantity - COALESCE(l.ledger_quantity, 0) AS difference
  FROM stock_balances sb
  JOIN products p ON p.product_id = sb.product_id
  LEFT JOIN (SELECT product_id,
                    sum(CASE WHEN transaction_type IN ('IN', 'ADJUST_IN') THEN quantity ELSE -quantity END) AS ledger_quantity
               FROM stock_transactions GROUP BY product_id) l
    ON l.product_id = sb.product_id;

-- IA-IN06: verify the hash chains. Empty result = no tampering detected.
CREATE FUNCTION fn_verify_stock_ledger()
RETURNS TABLE (chain_seq bigint, stock_transaction_id bigint, problem text)
LANGUAGE plpgsql STABLE AS $$
DECLARE
  r          stock_transactions;
  v_prev     char(64) := repeat('0', 64);
  v_expected bigint := 1;
BEGIN
  FOR r IN SELECT * FROM stock_transactions st ORDER BY st.chain_seq LOOP
    IF r.chain_seq <> v_expected THEN
      chain_seq := r.chain_seq; stock_transaction_id := r.stock_transaction_id;
      problem := format('missing rows before chain_seq %s (expected %s)', r.chain_seq, v_expected); RETURN NEXT;
    END IF;
    IF r.prev_hash <> v_prev THEN
      chain_seq := r.chain_seq; stock_transaction_id := r.stock_transaction_id;
      problem := 'prev_hash does not match previous row'; RETURN NEXT;
    END IF;
    IF r.row_hash <> fn_sha256(r.prev_hash || '|' || fn_stock_tx_payload(r)) THEN
      chain_seq := r.chain_seq; stock_transaction_id := r.stock_transaction_id;
      problem := 'row content was modified (hash mismatch)'; RETURN NEXT;
    END IF;
    v_prev := r.row_hash;
    v_expected := r.chain_seq + 1;
  END LOOP;
  RETURN;
END $$;

CREATE FUNCTION fn_verify_audit_log()
RETURNS TABLE (chain_seq bigint, audit_log_id bigint, problem text)
LANGUAGE plpgsql STABLE AS $$
DECLARE
  r          audit_logs;
  v_prev     char(64) := repeat('0', 64);
  v_expected bigint := 1;
BEGIN
  FOR r IN SELECT * FROM audit_logs a ORDER BY a.chain_seq LOOP
    IF r.chain_seq <> v_expected THEN
      chain_seq := r.chain_seq; audit_log_id := r.audit_log_id;
      problem := format('missing rows before chain_seq %s (expected %s)', r.chain_seq, v_expected); RETURN NEXT;
    END IF;
    IF r.prev_hash <> v_prev THEN
      chain_seq := r.chain_seq; audit_log_id := r.audit_log_id;
      problem := 'prev_hash does not match previous row'; RETURN NEXT;
    END IF;
    IF r.row_hash <> fn_sha256(r.prev_hash || '|' || fn_audit_payload(r)) THEN
      chain_seq := r.chain_seq; audit_log_id := r.audit_log_id;
      problem := 'row content was modified (hash mismatch)'; RETURN NEXT;
    END IF;
    v_prev := r.row_hash;
    v_expected := r.chain_seq + 1;
  END LOOP;
  RETURN;
END $$;

-- Reference data
INSERT INTO roles (role_name) VALUES ('REQUESTER'), ('WAREHOUSE_STAFF'), ('PURCHASING_STAFF'), ('MANAGER');

COMMIT;
