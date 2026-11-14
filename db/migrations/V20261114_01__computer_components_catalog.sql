-- Add a realistic computer-parts catalog without rewriting existing products or stock history.
-- Safe to apply more than once.
BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conrelid = 'public.goods_receipts'::regclass
           AND conname = 'ck_gr_document_reference'
    ) THEN
        ALTER TABLE public.goods_receipts
            ADD CONSTRAINT ck_gr_document_reference
            CHECK (status <> 'CONFIRMED' OR btrim(coalesce(document_reference, '')) <> '')
            NOT VALID;
    END IF;
END $$;

INSERT INTO categories (category_name, description)
SELECT 'Linh kiện máy tính', 'Các linh kiện PC: CPU, bo mạch chủ, RAM, lưu trữ, GPU, nguồn và tản nhiệt'
WHERE NOT EXISTS (
    SELECT 1 FROM categories WHERE lower(category_name) = lower('Linh kiện máy tính')
);

INSERT INTO products (sku, product_name, category_id, unit)
SELECT c.sku, c.product_name, cat.category_id, c.unit
  FROM (VALUES
      ('PC-CPU-AM5-R5', 'CPU AMD Ryzen 5 AM5', 'cái'),
      ('PC-MB-AM5-B650', 'Bo mạch chủ AM5 B650 mATX', 'cái'),
      ('PC-RAM-DDR5-16', 'RAM DDR5 16GB', 'thanh'),
      ('PC-SSD-NVME-1T', 'SSD NVMe 1TB', 'cái'),
      ('PC-GPU-RTX4060', 'Card đồ họa RTX 4060 8GB', 'cái'),
      ('PC-PSU-650-BR', 'Nguồn máy tính 650W 80+ Bronze', 'cái'),
      ('PC-CASE-MATX', 'Vỏ máy tính mATX Airflow', 'cái'),
      ('PC-COOL-TOWER120', 'Tản nhiệt khí CPU Tower 120mm', 'cái')
  ) AS c(sku, product_name, unit)
  CROSS JOIN LATERAL (
      SELECT category_id FROM categories
       WHERE lower(category_name) = lower('Linh kiện máy tính')
       ORDER BY category_id LIMIT 1
  ) cat
 WHERE NOT EXISTS (SELECT 1 FROM products p WHERE p.sku = c.sku);

UPDATE stock_balances sb
   SET minimum_stock = c.minimum_stock
  FROM (VALUES
      ('PC-CPU-AM5-R5', 2::numeric),
      ('PC-MB-AM5-B650', 2::numeric),
      ('PC-RAM-DDR5-16', 4::numeric),
      ('PC-SSD-NVME-1T', 3::numeric),
      ('PC-GPU-RTX4060', 1::numeric),
      ('PC-PSU-650-BR', 2::numeric),
      ('PC-CASE-MATX', 2::numeric),
      ('PC-COOL-TOWER120', 2::numeric)
  ) AS c(sku, minimum_stock)
  JOIN products p ON p.sku = c.sku
 WHERE sb.product_id = p.product_id
   AND sb.minimum_stock = 0;

-- Archive the fictional starter catalog instead of deleting products with ledger/document references.
UPDATE products
   SET is_active = false
 WHERE sku IN ('VPP-A4-001', 'VPP-BUT-002', 'VPP-KEP-003',
               'IT-MOU-001', 'IT-KEY-002', 'IT-HDMI-003', 'VS-KHG-001')
   AND is_active;

COMMIT;
