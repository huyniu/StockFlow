-- Dữ liệu tổng hợp chỉ dành cho database kiểm chứng riêng, không chạy trên database ứng dụng.
SET TIME ZONE 'UTC';

-- Tạo danh mục và kho có mã riêng để không đụng fixture integration test.
INSERT INTO categories (name, slug)
SELECT 'Danh mục benchmark ' || n,
       'benchmark-category-' || n
FROM generate_series(1, 10) AS n;

INSERT INTO warehouses (code, name, address, status)
SELECT 'BENCH-W-' || n,
       'Kho benchmark ' || n,
       'Dữ liệu kiểm chứng hiệu năng',
       'ACTIVE'
FROM generate_series(1, 10) AS n;

INSERT INTO users (email, password_hash, full_name, role_id, status)
SELECT 'benchmark-' || n || '@example.invalid',
       'BENCHMARK_ACCOUNT_NO_LOGIN',
       'Khách benchmark ' || n,
       r.id,
       'INACTIVE'
FROM generate_series(1, 100) AS n
CROSS JOIN roles r
WHERE r.name = 'CUSTOMER';

INSERT INTO products (category_id, sku, name, unit_price, status)
SELECT c.id,
       'BENCH-P-' || n,
       'Sản phẩm benchmark ' || n,
       (n % 25) + 1,
       'ACTIVE'
FROM generate_series(1, 200) AS n
JOIN categories c ON c.slug = 'benchmark-category-' || ((n - 1) % 10 + 1);

-- Phân bố 100.000 đơn qua 365 ngày, 10 kho, 100 khách và 8 trạng thái.
INSERT INTO orders (order_code, customer_id, warehouse_id, status, total_amount, created_at, updated_at)
SELECT 'BENCH-O-' || n,
       u.id,
       w.id,
       (ARRAY['PENDING', 'CONFIRMED', 'PACKED', 'SHIPPED',
              'DELIVERED', 'CANCELLED', 'EXPIRED', 'RETURNED'])[(n - 1) % 8 + 1],
       0,
       TIMESTAMPTZ '2025-01-01 00:00:00+00'
           + ((n - 1) % 365) * INTERVAL '1 day'
           + ((n * 13) % 86400) * INTERVAL '1 second',
       TIMESTAMPTZ '2025-01-01 00:00:00+00'
FROM generate_series(1, 100000) AS n
JOIN users u ON u.email = 'benchmark-' || ((n - 1) % 100 + 1) || '@example.invalid'
JOIN warehouses w ON w.code = 'BENCH-W-' || ((n - 1) % 10 + 1);

-- Mỗi đơn 5 sản phẩm khác nhau; giá và line_total tương ứng snapshot catalog khi seed.
INSERT INTO order_items (order_id, product_id, quantity, unit_price, line_total)
SELECT o.id,
       p.id,
       line.n,
       p.unit_price,
       p.unit_price * line.n
FROM generate_series(1, 100000) AS order_number
CROSS JOIN generate_series(1, 5) AS line(n)
JOIN orders o ON o.order_code = 'BENCH-O-' || order_number
JOIN products p ON p.sku = 'BENCH-P-' || ((order_number * 7 + line.n * 31) % 200 + 1);

-- Khớp tổng tiền đơn với các dòng hàng để benchmark không chứa tổng tiền giả.
UPDATE orders o
SET total_amount = totals.amount
FROM (
    SELECT oi.order_id,
           SUM(oi.line_total) AS amount
    FROM order_items oi
    JOIN orders source_order ON source_order.id = oi.order_id
    WHERE source_order.order_code LIKE 'BENCH-O-%'
    GROUP BY oi.order_id
) totals
WHERE o.id = totals.order_id;

-- Cập nhật thống kê và visibility map trước đo, không ép planner dùng index.
VACUUM ANALYZE;
