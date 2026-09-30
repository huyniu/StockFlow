-- Truy vấn chẩn đoán doanh số một sản phẩm để đo phạm vi hữu ích của idx_order_items_product.
-- API top-products toàn hệ thống không có productId; không coi đây là plan của API đó.
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)
SELECT oi.product_id,
       SUM(CAST(oi.quantity AS BIGINT)) AS total_quantity_sold,
       SUM(oi.line_total) AS total_revenue
FROM order_items oi
JOIN orders o ON o.id = oi.order_id
WHERE oi.product_id = (SELECT id FROM products WHERE sku = 'BENCH-P-42')
  AND o.status IN ('CONFIRMED', 'PACKED', 'SHIPPED', 'DELIVERED')
GROUP BY oi.product_id;
