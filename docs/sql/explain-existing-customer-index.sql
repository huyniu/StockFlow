-- Index theo customer phục vụ lịch sử khách hàng, không phải báo cáo toàn hệ thống.
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)
SELECT id,
       order_code,
       status,
       total_amount,
       created_at
FROM orders
WHERE customer_id = (SELECT id FROM users WHERE email = 'benchmark-42@example.invalid')
ORDER BY created_at DESC
LIMIT 20;
