# StockFlow — Handoff cho Antigravity

> Mục đích: đọc file này trước khi tư vấn, viết prompt, review hoặc đề xuất thay đổi cho project. Ưu tiên yêu cầu của người dùng nếu có mâu thuẫn với file này.

## 1. Bối cảnh và mục tiêu

Người dùng đang định hướng ứng tuyển vị trí **Java Backend Developer** (Intern/Fresher). Portfolio dự kiến có ba dự án:

1. Một dự án theo vấn đề thực tế/thời sự.
2. Một dự án theo hướng nghiên cứu kỹ thuật.
3. Một dự án thương mại có người dùng, nghiệp vụ và quy trình rõ ràng.

**StockFlow** là dự án thứ ba và là dự án thương mại chính. Mục tiêu không phải làm một ứng dụng CRUD đơn thuần, mà là thể hiện năng lực backend Java, thiết kế database, transaction, SQL, test và tài liệu dự án.

Tên dự án dùng trên CV:

> **StockFlow – Multi-Warehouse Order and Inventory Management System**

## 2. Mô hình phối hợp

- **Người dùng:** quyết định ưu tiên, chạy/quan sát dự án trong IntelliJ và duyệt thay đổi.
- **Antigravity / Codex trong IntelliJ:** đọc source tại chỗ, viết/sửa code, chạy Maven/test/debug khi được yêu cầu.
- **Trợ lý thiết kế:** chốt phạm vi, business rules, kiến trúc, database/API contract, viết prompt rõ ràng và review kết quả.

Khi nhận một yêu cầu code, Antigravity nên:

1. Đọc các file liên quan trước.
2. Nêu ngắn gọn kế hoạch thay đổi và những assumption quan trọng.
3. Chỉ sửa đúng phạm vi được yêu cầu.
4. Chạy build/test phù hợp.
5. Báo cáo file đã sửa, lý do, validation đã chạy, và phần chưa xác minh.

## 3. Stack đã chốt

| Thành phần | Lựa chọn |
| --- | --- |
| Ngôn ngữ | Java 17 LTS (máy hiện có Java 17) |
| Framework | Spring Boot 3.4.5 |
| Build | Maven + Maven Wrapper |
| Database | PostgreSQL |
| ORM | Spring Data JPA / Hibernate |
| Migration | Flyway |
| Authentication | Spring Security + JWT (chưa triển khai) |
| API docs | Swagger / OpenAPI (sẽ thêm sau authentication foundation) |
| Test | JUnit 5, Mockito, Testcontainers |
| Local infrastructure | Docker Compose |
| CI | GitHub Actions (giai đoạn hoàn thiện) |
| Frontend | Làm sau backend; chỉ cần giao diện mỏng cho demo |

Không dùng microservices, Kafka, payment gateway thật, carrier API thật hoặc frontend lớn trong MVP.

## 4. Bài toán sản phẩm

StockFlow giúp một retailer nhỏ quản lý sản phẩm, tồn kho ở nhiều kho, đơn hàng và lịch sử biến động tồn kho.

### Vai trò

| Role | Quyền chính |
| --- | --- |
| `CUSTOMER` | Xem sản phẩm; tạo, xem và hủy đơn của chính mình. |
| `WAREHOUSE_STAFF` | Nhập kho; xem tồn kho; đóng gói và giao đơn của kho được phân công. |
| `MANAGER` | Xem đơn hàng, tồn kho, lịch sử kho và báo cáo. |
| `ADMIN` | Quản lý user, role, catalog, warehouse và stock adjustment. |

### Luồng đơn hàng

```text
Tạo đơn (PENDING, giữ hàng 15 phút)
  → xác nhận thanh toán mô phỏng (CONFIRMED)
  → đóng gói (PACKED)
  → giao hàng (SHIPPED)
  → hoàn tất (DELIVERED)

PENDING → CANCELLED hoặc EXPIRED: giải phóng hàng đã giữ
CONFIRMED → CANCELLED: chỉ trước khi SHIPPED
DELIVERED → RETURNED: tạo movement nhập lại hàng
```

## 5. Quy tắc nghiệp vụ bắt buộc

1. Không được bán vượt tồn kho.
2. Tạo đơn với nhiều sản phẩm phải là một transaction: kiểm tra tồn, reserve hàng, tạo order và order items cùng thành công hoặc cùng rollback.
3. Mỗi stock change phải tạo một bản ghi **immutable** trong `inventory_movements`; không được sửa/xóa lịch sử để “chữa dữ liệu”.
4. Đơn `PENDING` hết thời hạn 15 phút phải tự chuyển `EXPIRED` và release stock.
5. Hủy đơn phải release đúng số lượng đã reserve; return phải tạo inbound movement mới.
6. Customer chỉ truy cập order của họ. Warehouse staff chỉ thao tác kho được phân công.
7. Payment chỉ là simulation trong MVP nhưng phải idempotent: một order không được tạo nhiều payment `PAID`.

## 6. Quy tắc transaction và concurrency

Không dùng cách `SELECT inventory → kiểm tra ở Java → UPDATE` dễ tạo race condition.

Cách ưu tiên khi reserve stock là **atomic conditional update**:

```sql
UPDATE inventories
SET available_quantity = available_quantity - :quantity,
    reserved_quantity = reserved_quantity + :quantity,
    version = version + 1,
    updated_at = now()
WHERE id = :inventoryId
  AND available_quantity >= :quantity;
```

- Affected row count `0` nghĩa là không đủ stock tại thời điểm update; request phải thất bại với lỗi nghiệp vụ rõ ràng.
- Với order nhiều sản phẩm, xử lý inventory theo thứ tự tăng dần của `inventory_id` trong cùng transaction để giảm nguy cơ deadlock.
- `@Version` vẫn hữu ích cho entity update nói chung, nhưng atomic conditional update là cơ chế chính của reserve inventory.
- Chỉ cân nhắc pessimistic locking sau này nếu phải chọn/allocate stock động giữa nhiều warehouse trong một critical section.

## 7. Data model dự kiến

```text
roles
users
categories
products
warehouses
inventories
inventory_movements
orders
order_items
payments
shipments
```

### Các bảng/cột quan trọng

**inventories**

```text
id, product_id, warehouse_id,
available_quantity, reserved_quantity, version, updated_at
UNIQUE(product_id, warehouse_id)
```

**inventory_movements**

```text
id, inventory_id, performed_by, type, quantity,
balance_before, balance_after,
reference_type, reference_id, note, created_at
```

`balance_before` và `balance_after` là số tồn vật lý trước/sau movement. Chúng giúp audit lịch sử kho mà không cần tính lại toàn bộ chain.

**orders**

```text
id, order_code, customer_id, warehouse_id, status,
subtotal, total_amount, reservation_expires_at, created_at
```

### Initial indexes

| Bảng | Index | Lý do |
| --- | --- | --- |
| `inventories` | unique `(product_id, warehouse_id)` | Lookup/reserve stock. |
| `orders` | `(customer_id, created_at DESC)` | Lịch sử đơn của customer. |
| `orders` | `(warehouse_id, status, created_at DESC)` | Work queue cho kho. |
| `orders` | `(status, reservation_expires_at)` | Scheduler quét đơn expired. |
| `order_items` | `(product_id)` | Báo cáo sản phẩm bán chạy. |
| `inventory_movements` | `(inventory_id, created_at DESC)` | Lịch sử kho. |
| `products` | `(category_id, status)` | Filter catalog. |

Không thêm index report theo cảm tính. Khi đã có seed data, dùng `EXPLAIN ANALYZE` đo query trước/sau index và ghi lại kết quả trong README.

## 8. API roadmap

### Authentication

```text
POST /api/v1/auth/register
POST /api/v1/auth/login
GET  /api/v1/users/me
```

### Catalog và warehouse

```text
GET   /api/v1/products
POST  /api/v1/products
PATCH /api/v1/products/{id}

GET   /api/v1/warehouses
POST  /api/v1/warehouses
```

### Inventory

```text
GET  /api/v1/inventories
POST /api/v1/inventories/stock-in
GET  /api/v1/inventories/movements
```

### Order

```text
POST /api/v1/orders
GET  /api/v1/orders/my
GET  /api/v1/orders/{id}
POST /api/v1/orders/{id}/cancel
POST /api/v1/orders/{id}/payment-simulations/confirm
```

### Reports (giai đoạn sau)

```text
GET /api/v1/reports/revenue
GET /api/v1/reports/top-products
GET /api/v1/reports/low-stock
GET /api/v1/reports/order-summary
```

## 9. Trạng thái hiện tại

Project hiện được người dùng mở và chạy trong IntelliJ tại:

```text
D:\IdeaProjects\Stockflow
```

Foundation đã có:

- `pom.xml` với Spring Web, Data JPA, Security, Validation, Flyway, PostgreSQL, Lombok, test dependencies.
- `compose.yaml` chạy PostgreSQL local.
- `application.yml` cấu hình datasource bằng environment variables có default local.
- `V1__create_users_and_roles.sql` tạo `roles`, `users`, seed bốn role.
- `StockflowApplication`.
- `HealthController` tại `GET /api/v1/health`.
- `README.md`, `.env.example`, `.gitignore`.

### Các foundation fixes đang chờ thực hiện

1. Thêm Flyway database module dành cho PostgreSQL, tương thích dependency management của Spring Boot 3.4.5.
2. Thêm `SecurityFilterChain` tạm thời:
   - `GET /api/v1/health` được `permitAll()`.
   - Các endpoint khác `authenticated()`.
   - Chưa triển khai JWT ở bước này.
3. Sửa README: Spring Boot không tự đọc `.env`; phải export environment variables qua IDE/run config hoặc dùng cơ chế config được hỗ trợ.
4. Thêm Maven Wrapper (`mvnw`, `mvnw.cmd`, `.mvn/wrapper`) nếu IntelliJ/môi trường hỗ trợ generate.
5. Chạy build/test sau thay đổi và báo cáo rõ kết quả.

## 10. Thứ tự triển khai

### Milestone 0 — Hoàn thiện foundation

Thực hiện năm foundation fixes ở mục 9. Không thêm feature nghiệp vụ mới.

**Definition of done:** application khởi động, Flyway migration chạy trên PostgreSQL, health endpoint trả `200`, các endpoint chưa public trả `401`, Maven Wrapper hoạt động, build/test pass.

### Milestone 1 — Authentication và authorization

- User entity/repository/service.
- Register/login với password hash BCrypt.
- JWT access token.
- Role-based authorization bốn role.
- Standard API error response và request validation.
- Test login, token invalid/expired, permission denied.

**Definition of done:** protected endpoint nhận JWT hợp lệ; sai role trả `403`; không token trả `401`.

### Milestone 2 — Catalog và warehouse

- Categories, products, warehouses.
- CRUD có validation và pagination/filter hợp lý.
- Admin tạo/sửa catalog; manager/staff read theo quyền.
- Flyway migrations mới, không sửa migration đã chạy trong môi trường khác.

### Milestone 3 — Inventory và immutable movements

- Inventory per product + warehouse.
- Stock-in với movement, balance before/after và actor.
- Query stock, low-stock, movement history.
- Test constraint `(product_id, warehouse_id)` và audit movement.

### Milestone 4 — Order, reserve và concurrency

- Create order với nhiều line items.
- Atomic conditional update để reserve.
- Cancel, expire scheduler, confirm payment simulation.
- Shipment/return theo scope MVP.
- Integration test với concurrent requests chứng minh không oversell.

### Milestone 5 — Reports và SQL optimization

- Revenue theo ngày/tháng/kho.
- Top products.
- Low stock.
- Order summary theo status.
- Query dùng `JOIN`, `GROUP BY`, pagination; đo `EXPLAIN ANALYZE` và thêm index có chứng cứ.

### Milestone 6 — Portfolio finish

- Swagger/OpenAPI và Postman collection.
- Seed: khoảng 3 warehouses, 100 products, 1.000–5.000 orders.
- Docker Compose, test automation, GitHub Actions.
- README có ERD, architecture, setup, API flow, trade-off concurrency/index.
- Sau backend, làm frontend mỏng: dashboard, products/inventory, create order, order detail/movement history.

## 11. Tiêu chuẩn chất lượng

- Package/module rõ ràng: `auth`, `user`, `catalog`, `inventory`, `order`, `report`, `common`.
- DTO cho request/response; không expose JPA entity trực tiếp ra API.
- Validation bằng Bean Validation; lỗi trả format nhất quán.
- Không hard-code secret; dùng environment variables. Không commit `.env` thật.
- Chỉ dùng Flyway cho schema evolution; `spring.jpa.hibernate.ddl-auto=validate`.
- Viết test có giá trị cho business rule và integration test cho transaction/concurrency; không viết test chỉ để tăng coverage.
- Không tự biến MVP thành microservices hay thêm feature không được yêu cầu.

## 12. Yêu cầu để đưa vào CV

Project hoàn thành cần có:

- Docker Compose chạy được application + PostgreSQL.
- Swagger và/hoặc Postman collection demo API.
- Migration tạo database từ đầu.
- Test cho authorization, stock-in, reserve, confirm, cancel, expiration, return và concurrency.
- Ít nhất một query report được tối ưu có minh chứng `EXPLAIN ANALYZE`.
- README rõ: business problem, ERD, architecture, setup, API flow, trade-offs.
- Bản deploy công khai hoặc video demo.

CV description dự kiến:

> Developed StockFlow, a Spring Boot and PostgreSQL multi-warehouse order and inventory management API. Implemented role-based access, transactional stock reservation to prevent overselling, immutable inventory audit trails, sales reporting, Dockerized local setup, and automated integration tests.

