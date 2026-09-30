-- Truy vấn tương đương API top-products cho một ngày UTC, trang đầu 20 dòng.
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)
SELECT p.id AS product_id,
       p.sku AS product_sku,
       p.name AS product_name,
       c.name AS category_name,
       SUM(CAST(oi.quantity AS BIGINT)) AS total_quantity_sold,
       SUM(oi.line_total) AS total_revenue
FROM order_items oi
JOIN orders o ON o.id = oi.order_id
JOIN products p ON p.id = oi.product_id
JOIN categories c ON c.id = p.category_id
WHERE o.status IN ('CONFIRMED', 'PACKED', 'SHIPPED', 'DELIVERED')
  AND o.created_at >= TIMESTAMPTZ '2025-05-15 00:00:00+00'
  AND o.created_at < TIMESTAMPTZ '2025-05-16 00:00:00+00'
GROUP BY p.id,
         p.sku,
         p.name,
         c.name
ORDER BY total_revenue DESC,
         total_quantity_sold DESC,
         p.id ASC
LIMIT 20
OFFSET 0;
