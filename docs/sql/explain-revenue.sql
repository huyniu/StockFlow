-- Doanh thu theo ngày UTC trên toàn bộ kho; đây là trường hợp index theo kho không có cột dẫn phù hợp.
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)
SELECT CAST(DATE_TRUNC('day', o.created_at AT TIME ZONE 'UTC') AS DATE) AS period,
       o.warehouse_id,
       w.name AS warehouse_name,
       COUNT(*) AS total_orders,
       SUM(o.total_amount) AS total_revenue
FROM orders o
JOIN warehouses w ON w.id = o.warehouse_id
WHERE o.status IN ('CONFIRMED', 'PACKED', 'SHIPPED', 'DELIVERED')
  AND o.created_at >= TIMESTAMPTZ '2025-05-15 00:00:00+00'
  AND o.created_at < TIMESTAMPTZ '2025-05-16 00:00:00+00'
GROUP BY period,
         o.warehouse_id,
         w.name
ORDER BY period ASC,
         o.warehouse_id ASC
LIMIT 20
OFFSET 0;
