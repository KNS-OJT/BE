-- =====================================================================
-- P03 | 03_seed.sql - demo data (fictional, no real personal data)
-- Demo password for every account: Demo@12345   (bcrypt, compatible with
-- Spring Security BCryptPasswordEncoder and BCrypt.Net)
-- =====================================================================
BEGIN;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO users (username, password_hash, full_name, email, role_id)
SELECT v.username, crypt('Demo@12345', gen_salt('bf', 10)), v.full_name, v.email, r.role_id
  FROM (VALUES
    ('requester01', 'Nguyen Van An',   'requester01@demo.local', 'REQUESTER'),
    ('requester02', 'Tran Thi Binh',   'requester02@demo.local', 'REQUESTER'),
    ('warehouse01', 'Le Van Cuong',    'warehouse01@demo.local', 'WAREHOUSE_STAFF'),
    ('warehouse02', 'Pham Thi Dung',   'warehouse02@demo.local', 'WAREHOUSE_STAFF'),
    ('purchasing01','Hoang Van Em',    'purchasing01@demo.local','PURCHASING_STAFF'),
    ('purchasing02','Bui Thi Hanh',    'purchasing02@demo.local','PURCHASING_STAFF'),
    ('manager01',   'Vo Thi Phuong',   'manager01@demo.local',   'MANAGER'),
    ('manager02',   'Dang Van Giang',  'manager02@demo.local',   'MANAGER')
  ) AS v(username, full_name, email, role)
  JOIN roles r ON r.role_name = v.role::user_role;

INSERT INTO categories (category_name, description) VALUES
  ('Văn phòng phẩm', 'Giấy, bút, kẹp, sổ'),
  ('Thiết bị IT',    'Chuột, bàn phím, cáp, ổ cứng'),
  ('Vệ sinh',        'Dung dịch, khăn giấy');

INSERT INTO products (sku, product_name, category_id, unit)
SELECT v.sku, v.name, c.category_id, v.unit
  FROM (VALUES
    ('VPP-A4-001', 'Giấy A4 Double A 70gsm', 'Văn phòng phẩm', 'ram'),
    ('VPP-BUT-002','Bút bi Thiên Long TL-027', 'Văn phòng phẩm', 'hộp'),
    ('VPP-KEP-003','Kẹp giấy 32mm',           'Văn phòng phẩm', 'hộp'),
    ('IT-MOU-001', 'Chuột không dây',          'Thiết bị IT',   'cái'),
    ('IT-KEY-002', 'Bàn phím USB',             'Thiết bị IT',   'cái'),
    ('IT-HDMI-003','Cáp HDMI 2m',              'Thiết bị IT',   'sợi'),
    ('VS-KHG-001', 'Khăn giấy hộp',            'Vệ sinh',       'hộp')
  ) AS v(sku, name, cat, unit)
  JOIN categories c ON c.category_name = v.cat;

INSERT INTO suppliers (supplier_name, contact_person, phone, email, address) VALUES
  ('Công ty TNHH Văn phòng phẩm Minh Phát', 'Lý Minh Phát', '0280000001', 'sales@minhphat.demo', 'Quận 1, TP.HCM'),
  ('Công ty CP Thiết bị số An Khang',       'Trịnh An Khang','0280000002', 'order@ankhang.demo',  'Thủ Đức, TP.HCM');

-- minimum stock levels (allowed directly: only quantity is posting-protected)
UPDATE stock_balances sb SET minimum_stock = v.min_qty
  FROM (VALUES ('VPP-A4-001', 50), ('VPP-BUT-002', 20), ('VPP-KEP-003', 10),
               ('IT-MOU-001', 10), ('IT-KEY-002', 10), ('IT-HDMI-003', 15), ('VS-KHG-001', 30)) v(sku, min_qty)
  JOIN products p ON p.sku = v.sku
 WHERE sb.product_id = p.product_id;

-- Opening stock through an adjustment: warehouse01 prepares, manager01 approves (BR-22)
SELECT set_config('app.current_user_id', (SELECT user_id::text FROM users WHERE username = 'warehouse01'), true);
INSERT INTO stock_adjustments (adjustment_type, created_by, reason)
VALUES ('OPENING', (SELECT user_id FROM users WHERE username = 'warehouse01'), 'Tồn đầu kỳ khi đưa hệ thống vào sử dụng');

INSERT INTO stock_adjustment_items (stock_adjustment_id, product_id, counted_quantity)
SELECT (SELECT max(stock_adjustment_id) FROM stock_adjustments), p.product_id, v.qty
  FROM (VALUES ('VPP-A4-001', 120), ('VPP-BUT-002', 15), ('VPP-KEP-003', 40),
               ('IT-MOU-001', 25), ('IT-KEY-002', 8), ('IT-HDMI-003', 30), ('VS-KHG-001', 60)) v(sku, qty)
  JOIN products p ON p.sku = v.sku;

SELECT set_config('app.current_user_id', (SELECT user_id::text FROM users WHERE username = 'manager01'), true);
UPDATE stock_adjustments
   SET status = 'CONFIRMED', confirmed_by = (SELECT user_id FROM users WHERE username = 'manager01')
 WHERE stock_adjustment_id = (SELECT max(stock_adjustment_id) FROM stock_adjustments);

COMMIT;

-- After seeding: VPP-BUT-002 (15 < 20) and IT-KEY-002 (8 < 10) appear in v_low_stock,
-- which is the starting point of the end-to-end demo (spec section 3.8).
