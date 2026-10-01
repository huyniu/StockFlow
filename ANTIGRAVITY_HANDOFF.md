<!-- Bàn giao cập nhật ngày 30/09/2026: quyết định kho/DISPATCH đã chốt và API fulfillment giai đoạn 2. -->
# StockFlow — Handoff cho Antigravity

> Mục đích: đọc file này trước khi tư vấn, viết prompt, review hoặc đề xuất thay đổi cho project. Ưu tiên yêu cầu của người dùng nếu có mâu thuẫn với file này.

## 1. Bối cảnh và mục tiêu

Người dùng đang định hướng ứng tuyển vị trí **Java Backend Developer** (Intern/Fresher). Portfolio dự kiến có ba dự án:

1. Một dự án theo vấn đề thực tế/thời sự.
2. Một dự án theo hướng nghiên cứu kỹ thuật.
3. Một dự án thương mại có người dùng, nghiệp vụ và quy trình rõ ràng.

**StockFlow** là dự án thứ ba và là dự án thương mại chính: **website bán hàng cho một cửa hàng/doanh nghiệp sở hữu nhiều kho**. Bản hoàn thiện có storefront cho khách và dashboard cho admin/nhân viên. Không làm marketplace, nhiều người bán hoặc hệ thống tenant. Project vẫn thể hiện năng lực backend Java, thiết kế database, transaction, SQL, test và tài liệu dự án.

Tên dự án dùng trên CV:

> **StockFlow – Retail Storefront & Multi-Warehouse Management**

Dashboard demo hiện có vẫn được giữ để trình diễn. Ưu tiên hoàn thiện API trước khi tách hai nhóm giao diện. Đánh giá hiện trạng và lộ trình mới nằm tại [docs/storefront-roadmap.md](docs/storefront-roadmap.md).

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
| Authentication | Spring Security + JWT + BCrypt, đã triển khai |
| API docs | SpringDoc 2.8.5, Swagger / OpenAPI, đã triển khai |
| Test | JUnit 5, Spring Boot Test, MockMvc, H2; Testcontainers PostgreSQL chưa triển khai |
| Local infrastructure | Docker Compose |
| CI | GitHub Actions test/package trên Java 17, đã có workflow |
| Frontend | Demo HTML/CSS/JS hiện có; storefront và dashboard riêng triển khai sau API |

Không dùng microservices, Kafka, payment gateway thật, carrier API thật hoặc frontend lớn trong MVP.

## 4. Bài toán sản phẩm

StockFlow bán hàng trực tiếp cho khách và giúp một retailer quản lý sản phẩm, tồn kho nhiều kho, đơn hàng, giao nhận và lịch sử biến động tồn kho. Giỏ hàng có thể nằm ở frontend trong phiên bản đầu; chưa có nhu cầu thêm bảng carts.

### Vai trò

Bảng dưới mô tả quyền mục tiêu. Đóng gói/giao nhận/nhận trả hàng đã có API cho ADMIN/MANAGER và staff đúng kho. Quản trị user/role và điều chỉnh tồn chưa có API; không được mô tả như đã triển khai. API thực tế xem README và mục 9.

| Role | Quyền chính |
| --- | --- |
| `CUSTOMER` | Xem sản phẩm; tạo, xem và hủy đơn của chính mình. |
| `WAREHOUSE_STAFF` | Nhập kho; xem tồn kho; đóng gói và giao đơn của kho được phân công. |
| `MANAGER` | Xem đơn hàng, tồn kho, lịch sử kho và báo cáo. |
| `ADMIN` | Quản lý user, role, catalog, warehouse và stock adjustment. |

### Luồng đơn hàng

Luồng dưới đây đã có API fulfillment. Người dùng đã chốt: khách chọn kho/chi nhánh phục vụ, một đơn thuộc một kho; DISPATCH giảm reserved/tồn vật lý ngay khi thanh toán. PACKED/SHIPPED/DELIVERED chỉ thay đổi trạng thái đơn/vận đơn, không trừ kho lần hai.

```text
Tạo đơn (PENDING, giữ hàng 15 phút)
  → xác nhận thanh toán mô phỏng (CONFIRMED)
  → đóng gói (PACKED)
  → giao hàng (SHIPPED)
  → hoàn tất (DELIVERED)

PENDING → CANCELLED hoặc EXPIRED: giải phóng hàng đã giữ
CONFIRMED hoặc PACKED → CANCELLED: chỉ trước SHIPPED; RETURN_RESTOCK và REFUNDED
DELIVERED → RETURNED: nhận trả toàn bộ hàng; RETURN_RESTOCK và hoàn tiền mô phỏng
```

## 5. Quy tắc nghiệp vụ bắt buộc

1. Không được bán vượt tồn kho.
2. Tạo đơn với nhiều sản phẩm phải là một transaction: kiểm tra tồn, reserve hàng, tạo order và order items cùng thành công hoặc cùng rollback.
3. Mỗi stock change phải tạo một bản ghi **immutable** trong `inventory_movements`; không được sửa/xóa lịch sử để “chữa dữ liệu”.
4. Đơn `PENDING` hết thời hạn 15 phút phải tự chuyển `EXPIRED` và release stock.
5. Hủy đơn phải release đúng số lượng đã reserve; return phải tạo inbound movement mới.
6. Customer chỉ truy cập order của họ. Warehouse staff chỉ thao tác kho được phân công.
7. Payment và shipping chỉ mô phỏng trong MVP nhưng phải idempotent: một order không được tạo nhiều payment `PAID`, vận đơn hoặc movement xuất/hoàn kho trùng.
8. Không sửa migration Flyway đã áp dụng; chỉ thêm migration mới khi schema thực sự cần thay đổi.
9. Không xóa/viết lại phần đang hoạt động nếu không có lý do cụ thể. Giữ phạm vi mỗi giai đoạn đủ nhỏ để kiểm thử và build xong.
10. Khi quyết định có nhiều cách hiểu và ảnh hưởng lớn đến schema/vòng đời đơn, hỏi người dùng trước khi triển khai phần phụ thuộc. Phần độc lập vẫn có thể tiếp tục.

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
- Vòng đời thanh toán/hủy/hết hạn hiện khóa dòng order bằng PESSIMISTIC_WRITE và kiểm tra lại trạng thái để chống xử lý lặp; giữ cơ chế này khi mở rộng fulfillment.
- Phần reserve inventory vẫn dùng atomic conditional update, không thay bằng kiểm tra số tồn tại Java.

## 7. Data model nền tảng đã có

Schema hiện tại do V1–V5 quản lý, có bản migration PostgreSQL và H2 riêng. Chưa có ảnh/mô tả sản phẩm, địa chỉ giao hàng chụp tại thời điểm đặt, hoặc bảng giỏ hàng. Giai đoạn 2 dùng bảng shipment/payment hiện có, không sửa/thêm migration.

```text
roles
users
categories
products
warehouses
warehouse_staff_assignments
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
total_amount, reservation_expires_at, created_at, updated_at
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
| `orders` | `created_at` với điều kiện trạng thái doanh thu (V5) | Báo cáo theo khoảng ngày, đã có đo PostgreSQL. |

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
GET   /api/v1/products/{id}
POST  /api/v1/products
PATCH /api/v1/products/{id}

GET   /api/v1/categories
POST  /api/v1/categories

GET   /api/v1/warehouses
POST  /api/v1/warehouses
GET   /api/v1/warehouses/order-options
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
GET  /api/v1/orders
GET  /api/v1/orders/my
GET  /api/v1/orders/{id}
POST /api/v1/orders/{id}/cancel
POST /api/v1/orders/{id}/payment-simulations/confirm
POST /api/v1/orders/{id}/pack
POST /api/v1/orders/{id}/ship
POST /api/v1/orders/{id}/deliver
POST /api/v1/orders/{id}/return
```

### Reports (đã có)

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

Các milestone backend nền tảng 0–6 đã có source và integration test: auth/JWT, catalog, warehouse, inventory/ledger, order/reserve/payment/cancel/expiry, reports/SQL, Swagger, seed demo và CI. Giao diện demo chạy cùng Spring Boot tại `/`.

- Demo seed thực tế: 3 kho, 4 danh mục, 24 sản phẩm, 72 inventory và 72 movement GOODS_RECEIPT; không seed đơn giả để tạo doanh thu.
- PostgreSQL có trigger cấm UPDATE/DELETE ledger; H2 có Java trigger tương ứng và test bất biến. Chưa có suite Testcontainers chạy migration PostgreSQL tự động trong CI.
- Scheduler hết hạn đã khóa/recheck order và chạy từng đơn trong transaction riêng; chưa có distributed scheduler lock.
- JWT mặc định một giờ. Tài khoản INACTIVE bị chặn cả đăng nhập và xác thực token đã phát; chưa có refresh token hoặc thu hồi từng token khi logout.
- Mục tiêu cũ chỉ làm dashboard trình diễn được thay bằng storefront và dashboard riêng. `.env` chỉ được Compose đọc; Spring Boot chạy trực tiếp dùng environment variables/IDE configuration.

Giai đoạn API đầu tiên cho mục tiêu mới bổ sung `GET /api/v1/orders`: lọc trạng thái/kho, phân trang 0-based, size 1–100, thứ tự created_at/id giảm dần. ADMIN/MANAGER xem mọi kho; staff chỉ thấy kho được phân công, kể cả tổng số đơn. Staff chọn kho ngoài phạm vi trả 403; không có phân công trả trang rỗng. CUSTOMER vẫn dùng `/orders/my` và kiểm tra chủ đơn ở API chi tiết.

Giai đoạn 2 có API pack/ship/deliver/return. Chỉ ADMIN/MANAGER và staff được phân công kho thao tác; CUSTOMER bị chặn. OrderResponse và /orders/my có shipment (tracking_code/status/shipped_at/delivered_at), null trước đóng gói. Ship nhận body tùy chọn với tracking_code; pack cấp sẵn SF-TRACK-UUID vì schema tracking_code NOT NULL. Mã không được đổi sau ship, có UNIQUE chống trùng giữa hai đơn.

Fulfillment dùng khóa dòng order chung với cancel/payment/expiry, kiểm tra chuyển trạng thái và idempotency tại trạng thái đích. Return nhận lại toàn bộ hàng theo inventoryId tăng dần, ghi movement mới và hoàn tiền mô phỏng cùng transaction; lỗi một mặt hàng phải rollback tất cả. Hủy sau SHIPPED bị chặn; đơn PACKED bị hủy giữ shipment PREPARING như lịch sử. Chi tiết kiểm chứng: [docs/fulfillment-verification.md](docs/fulfillment-verification.md).

## 10. Lộ trình theo mục tiêu cửa hàng mới

### Giai đoạn 1 — Danh sách đơn vận hành và trạng thái tài khoản

- Thêm API danh sách đơn theo role/phạm vi kho/trạng thái và phân trang tại database.
- Chặn INACTIVE khi login hoặc khi dùng JWT đã phát; giữ các contract auth hiện có.
- Giữ toàn bộ schema V1–V5, tạo/giữ/thanh toán/hủy/hết hạn và giao diện demo.
- Test quyền, metadata phân trang, thu hồi phân công kho, tài khoản bị khóa và regression toàn bộ suite.

### Giai đoạn 2 — Fulfillment theo kho đã triển khai

- Giữ DISPATCH lúc xác nhận payment theo quyết định người dùng; không xuất lần hai khi ship.
- API CONFIRMED → PACKED → SHIPPED → DELIVERED → RETURNED; tracking_code duy nhất, timestamp và response shipment.
- Dùng khóa dòng order, kiểm tra role/phạm vi kho, state transition và idempotency. Return phải có movement mới, không sửa movement cũ.
- ADMIN/MANAGER hoặc staff kho đó xác nhận giao/nhận trả; MVP nhận trả toàn bộ, chưa có partial return hoặc customer return-request.

### Giai đoạn 3 — API mua hàng và checkout

- Tìm kiếm catalog, contract public chỉ sản phẩm đang bán; bổ sung ảnh/mô tả khi có yêu cầu cụ thể.
- Khách tiếp tục chọn kho/chi nhánh phục vụ, một đơn thuộc một kho. Chốt thông tin người nhận/địa chỉ trước khi thêm schema checkout. Không làm tự allocation/split order trong MVP này.
- Nếu cần schema mới, thêm migration kế tiếp cho PostgreSQL/H2; giữ snapshot giá và reserve nhiều sản phẩm trong một transaction.
- Giữ giỏ ở frontend cho MVP; ownership đơn vẫn bắt buộc.

### Giai đoạn 4 — Hai nhóm giao diện

- Storefront: danh mục/chi tiết, giỏ, checkout, theo dõi đơn và vận đơn.
- Dashboard: catalog, stock-in, danh sách xử lý đơn theo kho, fulfillment và reports.
- Tái sử dụng API; UI không tự suy luận quyền hay cập nhật số tồn/trạng thái đơn.

### Giai đoạn 5 — Củng cố portfolio và vận hành

- Ưu tiên Testcontainers PostgreSQL để CI kiểm tra migration/trigger thật và concurrency trên DB đích.
- Refresh token/revocation, correlation ID/Actuator; distributed task lock hoặc cache chỉ khi có nhu cầu và test tương ứng.
- Chuyển kho và kiểm kê là mở rộng WMS sau MVP bán hàng; không chen vào luồng checkout hiện tại.
- Deploy/video demo sau khi luồng mua hàng và fulfillment đã được nghiệm thu.

## 11. Tiêu chuẩn chất lượng

- Package/module rõ ràng: `auth`, `user`, `catalog`, `inventory`, `order`, `report`, `common`.
- DTO cho request/response; không expose JPA entity trực tiếp ra API.
- Validation bằng Bean Validation; lỗi trả format nhất quán.
- Không hard-code secret; dùng environment variables. Không commit `.env` thật.
- Chỉ dùng Flyway cho schema evolution; `spring.jpa.hibernate.ddl-auto=validate`.
- Viết test có giá trị cho business rule và integration test cho transaction/concurrency; không viết test chỉ để tăng coverage.
- Không tự biến MVP thành microservices hay thêm feature không được yêu cầu.
- Mọi file mới/sửa có JavaDoc/comment tiếng Việt có dấu, mã hóa UTF-8. SQL mới dùng Java Text Block hoặc file SQL thụt lề nhiều dòng; không viết các câu SQL dài trên một dòng.
- Máy Windows này bắt buộc chạy Maven với `-Dmaven.repo.local=C:/Users/Admin/.m2/repository`; báo cáo test/package, file thay đổi và điểm chưa xác minh.

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

> Developed StockFlow for a retailer operating multiple warehouses, using Spring Boot and PostgreSQL. Implemented scoped authorization, transactional stock reservation to prevent overselling, immutable inventory audit trails, shipment tracking and order fulfillment, sales reporting, a runnable demo, and automated integration tests.

Storefront riêng chưa hoàn thiện; chỉ bổ sung giao diện đó vào CV sau khi triển khai và kiểm thử. Không coi lộ trình là thành tích đã hoàn thành.
