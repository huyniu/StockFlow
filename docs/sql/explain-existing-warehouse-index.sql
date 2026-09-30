-- Bộ lọc kho cụ thể cho phép planner dùng index hiện có theo warehouse/status/created_at.
-- Đây vẫn là truy vấn revenue của API, chỉ thêm tham số warehouseId.
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)
SELECT CAST(DATE_TRUNC('day', o.created_at AT TIME ZONE 'UTC') AS DATE) AS period,
       o.warehouse_id,
       w.name AS warehouse_name,
       COUNT(*) AS total_orders,
       SUM(o.total_amount) AS total_revenue
FROM orders o
JOIN warehouses w ON w.id = o.warehouse_id
WHERE o.status IN ('CONFIRMED', 'PACKED', 'SHIPPED', 'DELIVERED')
  AND o.warehouse_id = (SELECT id FROM warehouses WHERE code = 'BENCH-W-1')
  AND o.created_at >= TIMESTAMPTZ '2025-05-01 00:00:00+00'
  AND o.created_at < TIMESTAMPTZ '2025-06-01 00:00:00+00'
GROUP BY period,
         o.warehouse_id,
         w.name
ORDER BY period ASC,
         o.warehouse_id ASC
LIMIT 20
OFFSET 0;
