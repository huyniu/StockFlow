<!-- Tài liệu kiểm chứng SQL và index của Milestone 5, ghi bằng tiếng Việt UTF-8. -->
# StockFlow — kiểm chứng Reports & SQL Optimization

Ngày đo: **30/09/2026**. PostgreSQL **13.2**, Windows, cùng máy chạy dự án.
Đây là kết quả đo trên database kiểm chứng riêng, không phải cam kết latency của hệ thống production.

## Contract và cách tính

- Chỉ **MANAGER/ADMIN** đọc cả bốn endpoint tại `/api/v1/reports`. CUSTOMER/WAREHOUSE_STAFF nhận 403; chưa đăng nhập nhận 401.
- Revenue và Top Products chỉ tính đơn **CONFIRMED, PACKED, SHIPPED, DELIVERED**. Đơn PENDING/CANCELLED/EXPIRED/RETURNED không đóng góp doanh thu.
- Khoảng ngày dựa trên **orders.created_at theo UTC**, không phải payments.paid_at. fromDate/toDate là ISO `yyyy-MM-dd`, tùy chọn; ngày toDate được bao gồm bằng điều kiện `created_at < toDate + 1 ngày`.
- Revenue nhóm `groupBy=DAY` mặc định hoặc `MONTH`; trường `period` là ngày đầu tháng cho MONTH. Nhóm theo từng kho; đếm đơn trước khi JOIN order_items để không nhân tổng tiền.
- Top Products cộng **order_items.line_total** và quantity đã chụp lúc đặt; việc sửa giá catalog không đổi doanh thu quá khứ. Thứ tự là revenue DESC, quantity DESC, productId ASC.
- Low stock dùng `available_quantity <= threshold`, mặc định 10, có warehouseId. reserved_quantity không được coi là hàng có thể bán; physicalQuantity = available + reserved.
- Order summary nhóm mọi trạng thái đang có dữ liệu. totalAmount của trạng thái hủy/chờ là tổng giá trị đơn, không phải doanh thu.
- Revenue/Top Products/Low Stock trả `PageResponse`, hỗ trợ page/size (mặc định 20, tối đa 100). Top Products có limit ghi đè size. Thứ tự báo cáo cố định ở SQL, không nhận SQL sort tùy ý.
- Query và count phân trang chạy tại database; không nạp danh sách entity để tổng hợp ở Java.

## SQL đã đo

Các file SQL tái hiện truy vấn thật với tham số đã bind thành literal để EXPLAIN:
[top-products](sql/explain-top-products.sql), [revenue](sql/explain-revenue.sql).
SQL trong Java sử dụng **text block**, với từng mệnh đề xuống dòng. Bộ lọc tùy chọn chỉ được thêm khi có giá trị, tránh kiểu `(:param IS NULL OR column = :param)`.

### Top Products: một ngày UTC, trang đầu

```sql
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
```

### Revenue: một ngày UTC, toàn bộ kho

```sql
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
```

Điều kiện WHERE so sánh trực tiếp created_at. DATE_TRUNC chỉ dùng cho SELECT/GROUP BY.
Nếu có warehouseId, repository thêm `AND o.warehouse_id = :warehouseId` với tham số bind.
Query count của Top Products là COUNT(DISTINCT productId); count của Revenue đếm các nhóm ngày/kho, không đếm số đơn.

## Dữ liệu và phương pháp đo

Seed tại [seed-report-benchmark.sql](sql/seed-report-benchmark.sql) thêm:
**100.000 orders, 500.000 order_items, 200 products, 10 warehouses, 100 customers**, trải 365 ngày và 8 trạng thái.
Mỗi đơn có 5 sản phẩm khác nhau, total_amount khớp tổng line_total.

Database đã chạy integration test trước khi seed, nên số dòng thực tế lúc đo là:
**100.031 orders, 500.036 order_items, 254 products, 42 warehouses**.
Các fixture báo cáo tự rollback; một số fixture milestone trước còn tồn tại trong database kiểm chứng.
Khoảng 2025-05-15 có **137 đơn** đóng góp doanh thu và **685 dòng hàng** tương ứng.

Quy trình:

1. Chạy Flyway V1–V4 và toàn bộ integration test trên database kiểm chứng riêng.
2. Seed dữ liệu benchmark, chạy VACUUM ANALYZE.
3. Chạy `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)` **5 lần** mỗi truy vấn trước thêm index.
4. Áp dụng index V5, ANALYZE orders và chạy lại cùng 5 lần, cùng câu SQL và dữ liệu.
5. Giữ mặc định planner; **không SET enable_seqscan = off**, không ép join hay index.
6. Tạo database nghiệm thu mới để kiểm chứng Flyway V1–V5 và toàn bộ test; dọn database kiểm chứng sau khi hoàn tất.

Các mẫu có cache đã được làm nóng bởi seed/VACUUM và lần đo trước.
Median là mẫu giữa sau khi sắp xếp 5 Execution Time. Cost là ước lượng của planner, **không phải milliseconds**.
Số liệu sau đây đo câu SELECT dữ liệu, chưa bao gồm query count, JWT, serialize JSON, network hoặc toàn bộ latency HTTP.

## Kết quả trước và sau V5

**Top Products**

- Root cost trước: **6985.86..6985.91**; sau: **3219.05..3219.10**.
- Execution Time trước (ms): **26.492, 23.826, 25.810, 25.229, 25.507**.
- Execution Time sau (ms): **4.586, 3.588, 5.029, 4.471, 4.184**.
- Median: **25.507 → 4.471 ms**, khoảng **5,70 lần nhanh hơn** trên mẫu này.
- Shared hit blocks ở mẫu thứ 5: **3809 → 1113**.
- Trước: parallel Seq Scan orders; JOIN order_items dùng Index Scan `uq_order_product`.
- Sau: Index Only Scan orders qua `idx_orders_report_created`, **137 rows, Heap Fetches = 0**;
  JOIN order_items tiếp tục dùng `uq_order_product` (**137 loops × 5 rows**).
- Seq Scan trên bảng products/categories nhỏ vẫn là lựa chọn của planner và không bị ép thay đổi.

**Revenue**

- Root cost trước: **1980.35..1981.05**; sau: **15.46..16.16**.
- Execution Time trước (ms): **0.953, 0.911, 0.971, 0.950, 0.950**.
- Execution Time sau (ms): **0.446, 0.289, 0.278, 0.267, 0.279**.
- Median: **0.950 → 0.279 ms**, khoảng **3,41 lần nhanh hơn** trên mẫu này.
- Shared hit blocks ở mẫu thứ 5: **655 → 15**.
- Trước: Seq Scan warehouses nhỏ, rồi **42 Index Scan loops** trên `idx_orders_warehouse_status_created`.
- Sau: **một Index Only Scan** qua `idx_orders_report_created`, **137 rows, Heap Fetches = 0**,
  rồi JOIN với warehouses. Không còn phải tìm qua index riêng cho từng kho.

### Các nút EXPLAIN thực tế đáng chú ý

Đây là các trường trích từ output FORMAT JSON; các trường khác và nút cha được lược bỏ.

Top Products trước V5:

```json
{
  "Node Type": "Seq Scan",
  "Parallel Aware": true,
  "Relation Name": "orders",
  "Total Cost": 3844.84,
  "Actual Rows": 69,
  "Actual Loops": 2,
  "Rows Removed by Filter": 49947
}
```

Actual Rows trên node song song là số trung bình mỗi loop; kết quả toàn bộ là 137 đơn phù hợp.

Top Products và Revenue sau V5 đều có:

```json
{
  "Node Type": "Index Only Scan",
  "Relation Name": "orders",
  "Index Name": "idx_orders_report_created",
  "Actual Rows": 137,
  "Actual Loops": 1,
  "Heap Fetches": 0
}
```

Output JSON đầy đủ của lần đo được lưu trong `target/report-before-*.json` và `target/report-after-*.json`.
Các file target là artifact cục bộ, không cần commit; SQL và số liệu ở tài liệu này đủ để tái hiện phép đo.

## Index cũ dùng đúng phạm vi

Không thể khẳng định cả ba index cũ đều phải xuất hiện trong cùng một truy vấn tổng hợp.
Planner chọn đường đọc theo điều kiện lọc và thống kê; bỏ warehouseId/customerId/productId có thể làm index tương ứng kém phù hợp.

- **idx_orders_warehouse_status_created**:
  [Revenue có warehouseId, tháng 05/2025](sql/explain-existing-warehouse-index.sql) trước V5 dùng
  **Bitmap Index Scan → Bitmap Heap Scan**, **480 rows**.
  Root cost **746.81..747.51**, Execution Time mẫu thứ 5 **1.374 ms**.
  Revenue toàn kho trước V5 cũng dùng index này thông qua JOIN warehouse.
  Sau V5 planner chọn index mới cho mẫu đã đo; index cũ vẫn phục vụ work queue theo kho/trạng thái.

- **idx_orders_customer_created**:
  [Lịch sử một customer, LIMIT 20](sql/explain-existing-customer-index.sql) dùng **Index Scan**,
  **20 rows**, Root cost trước V5 **6.92..76.29**, Execution Time mẫu thứ 5 **0.176 ms**.
  Báo cáo doanh thu toàn hệ thống không có customerId, nên không gán công dụng này cho Revenue/Top Products.

- **idx_order_items_product**:
  [Tổng hợp một productId](sql/explain-existing-product-index.sql) dùng
  **Bitmap Index Scan → Bitmap Heap Scan**, **2500 rows**.
  Root cost trước V5 **4521.05..8085.78**, Execution Time mẫu thứ 5 **16.223 ms**.
  Đây là truy vấn chẩn đoán theo sản phẩm, **không phải** endpoint Top Products toàn hệ thống.
  Với Top Products lọc ngày, index `uq_order_product(order_id, product_id)` hiệu quả hơn vì đã chọn được orders trước.

Seq Scan trên bảng nhỏ hoặc báo cáo phủ phần lớn dữ liệu không tự động có nghĩa là truy vấn cần sửa.
Không thêm index cho low-stock/order-summary khi chưa có số đo chứng minh.

## Index mới và trade-off

Migration [PostgreSQL V5](../src/main/resources/db/migration/V5__add_report_date_index.sql):

```sql
CREATE INDEX idx_orders_report_created
    ON orders (created_at)
    INCLUDE (id, warehouse_id, total_amount)
    WHERE status IN ('CONFIRMED', 'PACKED', 'SHIPPED', 'DELIVERED');
```

- Cột dẫn created_at phục vụ báo cáo lọc ngày không cần warehouseId.
- Predicate khớp tập trạng thái cố định trong repository, giảm số đơn cần lưu trong index.
- INCLUDE giúp đọc id/warehouseId/totalAmount mà không phải đọc heap khi visibility map cho phép.
- Kích thước index đo được: **2.506.752 bytes**, khoảng **2,39 MiB**.
- Đổi lại có thêm chi phí lưu trữ và bảo trì index khi cập nhật đơn, đặc biệt khi chuyển vào/ra tập trạng thái doanh thu.
- Index Only Scan không luôn có Heap Fetches = 0 trên bảng đang ghi nhiều; kết quả benchmark đã VACUUM không đại diện mọi workload.
- [H2 V5](../src/test/resources/db/migration/V5__add_report_date_index.sql) dùng index thông thường `(created_at, status)`
  do H2 không hỗ trợ cùng partial covering index. H2 xác minh chức năng; số đo hiệu năng chỉ lấy trên PostgreSQL.

## Tái hiện

Chỉ thực hiện trên **database benchmark riêng**.

1. Tạo database trống và áp dụng tuần tự V1–V4 bằng Flyway hoặc psql.
2. Chạy seed. Không áp dụng V5 trước khi lấy baseline.
3. Chạy mỗi file explain 5 lần. Ví dụ từ thư mục gốc dự án, với thông tin kết nối của môi trường:

```powershell
psql -h localhost -p 5432 -U postgres -d stockflow_report_benchmark -v ON_ERROR_STOP=1 -f docs/sql/seed-report-benchmark.sql
psql -h localhost -p 5432 -U postgres -d stockflow_report_benchmark -v ON_ERROR_STOP=1 -f docs/sql/explain-top-products.sql
psql -h localhost -p 5432 -U postgres -d stockflow_report_benchmark -v ON_ERROR_STOP=1 -f docs/sql/explain-revenue.sql
```

4. Áp dụng V5 vào database benchmark, ANALYZE orders, chạy lại các file explain.
5. Giữ cả cost, Execution Time, BUFFERS và tên node/index; kết quả có thể khác theo phần cứng, phiên bản và thống kê.
6. Database benchmark áp dụng SQL bằng psql không được dùng làm database ứng dụng có lịch sử Flyway production.

Trong nghiệm thu, bộ test được chạy trên cả H2 và PostgreSQL với profile test tắt scheduler.
Test báo cáo dùng JWT thật, fixture đa kho/đa trạng thái, giá snapshot, ngày biên UTC, phân trang, đầu vào sai và ma trận role.
