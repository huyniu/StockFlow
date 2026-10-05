<!-- Bàn giao bổ sung ngày 05/10/2026: V17 chống tạo đơn trùng; storefront bán công nghệ, giữ nghiệp vụ SKU. -->
# StockFlow — Handoff cho Antigravity

> Mục đích: đọc file này trước khi tư vấn, viết prompt, review hoặc đề xuất thay đổi cho project. Ưu tiên yêu cầu của người dùng nếu có mâu thuẫn với file này.

## 1. Bối cảnh và mục tiêu

Người dùng đang định hướng ứng tuyển vị trí **Java Backend Developer** (Intern/Fresher). Portfolio dự kiến có ba dự án:

1. Một dự án theo vấn đề thực tế/thời sự.
2. Một dự án theo hướng nghiên cứu kỹ thuật.
3. Một dự án thương mại có người dùng, nghiệp vụ và quy trình rõ ràng.

**StockFlow** là dự án thứ ba và là dự án thương mại chính: **website bán điện thoại, laptop, thiết bị âm thanh và phụ kiện công nghệ cho một cửa hàng/doanh nghiệp sở hữu nhiều kho**. Tên storefront là **StockFlow Tech**. Người dùng được chọn ngành hàng cho bài tập lớn và đã đồng ý hướng công nghệ. Bản hoàn thiện có storefront cho khách và dashboard cho admin/nhân viên. Không làm marketplace, nhiều người bán hoặc hệ thống tenant. Project vẫn thể hiện năng lực backend Java, thiết kế database, transaction, SQL, test và tài liệu dự án.

Tên dự án dùng trên CV:

> **StockFlow – Technology Storefront & Multi-Warehouse Management**

<!-- Bổ sung V17 và bộ kiểm chứng PostgreSQL thật; không xem lựa chọn mặc định của câu hỏi là câu trả lời. -->
**Hiện trạng ngày 05/10/2026:** trang chủ có lối tắt ngành hàng thật, thẻ ảnh gọn hơn, bán chạy từ đơn đã thanh toán và hướng dẫn `/#shop/help`. `POST /orders` nhận `Idempotency-Key` tùy chọn; V17 giữ UNIQUE khách/khóa trong cùng transaction với đặt đơn/reserve/ledger. CI có profile Testcontainers PostgreSQL 17. Đã kiểm chứng 580 test H2, 9 test PostgreSQL QA 18.3 và 241 kiểm tra Chrome; Docker/CI thật chưa chạy tại máy này. Xem [contract, phạm vi và quyết định còn chờ](docs/storefront-quality-and-reliability.md).

Đánh giá xác thực mua hàng (mỗi khách/model hay mỗi đơn, duyệt hay hiển thị ngay) và liên hệ/bảo hành/đổi trả (thật hay demo có nhãn) còn chờ chủ cửa hàng trả lời. Chưa tạo schema reviews hoặc bịa chính sách. Người dùng yêu cầu làm toàn bộ các cải thiện đã đề xuất; tiếp tục hai phần phụ thuộc sau khi có quyết định này. Không reset hoặc seed catalog của database đang dùng trong lượt cải thiện giao diện.

<!-- Bổ sung tìm kiếm khi gõ: cùng contract catalog công khai, không sửa nghiệp vụ backend. -->
Ô tìm kiếm storefront có gợi ý tối đa sáu model ACTIVE với ảnh/tên/giá từ, debounce 250 ms, chọn bằng chuột hoặc ↑/↓ + Enter. Escape/đổi trang/đổi tài khoản hủy gợi ý và phản hồi cũ. Gợi ý tìm toàn cửa hàng; Enter không chọn gợi ý hoặc nút tìm tất cả dùng bộ lọc kệ hiện có. Không thêm endpoint, schema hoặc sửa migration. Chi tiết và kiểm chứng ở [docs/search-suggestions.md](docs/search-suggestions.md).

Catalog giữ năm nhóm công nghệ ban đầu. Người dùng đã yêu cầu menu theo ảnh CellphoneS: **Điện thoại**, **Laptop**, **Âm thanh/Mic**, **Đồng hồ/Camera**, **Gia dụng/Làm đẹp**, cùng nhóm con và hãng. V9/V10 đã lưu tham chiếu và bổ sung form ADMIN; không tự thêm sản phẩm gia dụng hay model nổi bật. V11 có thông số nhập tay và nhóm màu; V12 mở rộng thành model → phiên bản → màu, **một thẻ chung cho model** theo quyết định người dùng. Dung lượng điện thoại, kích thước/kết nối đồng hồ hoặc RAM/SSD laptop có thể là phiên bản; mỗi tổ hợp có SKU/giá/ảnh/tồn riêng, giữ nguyên ID và lịch sử cũ. Serial/IMEI và bảo hành chưa có. Xem [Danh mục và hãng](docs/catalog-categories-and-brands.md), [Model/phiên bản/màu](docs/product-models-versions-and-colors.md) và [Trang bán lẻ](docs/retail-product-detail.md).

Lượt đổi ngành hàng đã kiểm chứng **219 test PASS**, test/package BUILD SUCCESS và **72 kiểm tra Chrome PASS** trên PostgreSQL QA mới. Sau đó người dùng yêu cầu xóa catalog cũ để tự thêm: ngày **01/10/2026** đã sao lưu và reset database `stockflow` tại `localhost:5432`. Tại thời điểm reset có **năm danh mục công nghệ, 0 sản phẩm/tồn kho/movements/đơn hàng**; giữ nguyên tài khoản, ba kho và phân công nhân viên. Người dùng đã thêm sản phẩm thủ công sau đó; không được coi snapshot reset là số liệu hiện tại. **21 yêu cầu API trực tiếp PASS** sau reset. Chi tiết bản sao lưu/phạm vi ở [docs/tech-store.md](docs/tech-store.md). Đây là reset vận hành một lần; startup runner không được tự xóa dữ liệu.

Storefront và dashboard đã tách trong cùng ứng dụng tĩnh tại `/`. Khách vãng lai/CUSTOMER dùng cửa hàng; role vận hành vào dashboard sau đăng nhập. Lộ trình nằm tại [docs/storefront-roadmap.md](docs/storefront-roadmap.md), kiểm chứng mới nhất tại [docs/storefront-dashboard-verification.md](docs/storefront-dashboard-verification.md).

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
| Test | JUnit 5, Spring Boot Test, MockMvc, H2; profile Testcontainers PostgreSQL đã có, Docker/CI thật chưa xác minh tại máy này |
| Local infrastructure | Docker Compose |
| CI | GitHub Actions test/package trên Java 17, đã có workflow |
| Frontend | Storefront/dashboard riêng bằng HTML/CSS/JS thuần, chạy cùng Spring Boot; không cần Node.js |

Không dùng microservices, Kafka, payment gateway thật, carrier API thật hoặc frontend lớn trong MVP.

## 4. Bài toán sản phẩm

StockFlow Tech bán phụ kiện máy tính trực tiếp cho khách và giúp cửa hàng quản lý sản phẩm, tồn kho nhiều kho, đơn hàng, giao nhận và lịch sử biến động tồn kho. Giỏ hàng nằm ở frontend và giữ qua reload trong phiên tab; chưa có nhu cầu thêm bảng carts. Đã có mô tả văn bản và trang chi tiết công khai `/san-pham/{id}` cho khách; Admin nhập/sửa/xóa mô tả tối đa 5.000 ký tự. V16 đã bổ sung chỉnh sửa tên/số điện thoại liên hệ của chính tài khoản và trang `/#shop/account`. Sổ địa chỉ, đổi mật khẩu/email, đánh giá sau mua và tích hợp thanh toán vẫn là các lượt riêng; không coi mock giao diện hoặc nút thanh toán mô phỏng là đã đáp ứng.

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

Schema hiện tại do V1–V16 quản lý, có migration PostgreSQL và H2 riêng. V6 thêm ảnh bìa, V7 thêm mô tả tối đa 5.000 ký tự, V8 thêm tối đa 8 ảnh bổ sung có thứ tự. V9 thêm brands/brand_categories/products.brand_id; V10 thêm categories.parent_id và nhóm/hãng tham chiếu. V11 thêm product_specifications/product_variants; V12 thêm product_versions/product_version_specifications và version_id trên mapping màu. Không sửa migration đã áp dụng. Thông số là bảng tên–giá trị, chưa có lọc RAM/chip/màn hình chuyên biệt. Upload file ảnh và bảng giỏ hàng chưa có. V15 thêm snapshot người nhận/điện thoại/địa chỉ/ghi chú trên orders, giữ đơn cũ chưa có thông tin nhận hàng. V16 thêm users.phone tùy chọn để hỗ trợ hồ sơ và gợi ý checkout; không thay bản chụp đơn cũ. Fulfillment dùng shipment/payment hiện có.

```text
roles
users
categories
brands
brand_categories
products
product_images
product_specifications
product_variants
product_versions
product_version_specifications
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

**product_specifications và product_variants (V11)**

ADMIN nhập tối đa 60 thông số tên–giá trị hiệu lực; POST bỏ qua/null lưu rỗng, PATCH bỏ qua/null giữ nguyên, [] xóa bảng. V12 có tối đa 20 phiên bản/model, 30 màu/phiên bản và 100 SKU/model. product_variants.product_id trỏ trang chung, sku_product_id trỏ SKU Product thực bán và version_id trỏ cấu hình; SKU gốc cũng có mapping chính nó. Giữ nguyên FK inventories/order_items và atomic reserve. Nhập kho/đặt hàng dùng sku_product_id, không dùng ID mapping hoặc phiên bản. grouped=true loại SKU con tại SQL trước phân trang; mặc định GET products vẫn trả SKU để API cũ/phiếu nhập tương thích. Cả trang INACTIVE hoặc một màu disabled đều bị chặn lúc đặt, kể cả gửi trực tiếp ID. Không merge SKU cũ hoặc di chuyển SKU giữa phiên bản. Contract/giới hạn hiện tại ở docs/product-models-versions-and-colors.md; tài liệu V11 là lịch sử.

**categories và brands**

categories.parent_id nullable, FK tự tham chiếu và CHECK chặn tự làm cha. API ADMIN chỉ nối danh mục mới vào cha đã có, tối đa ba cấp; không có API đổi cha gây vòng lặp. Hãng độc lập và dùng chung, không tạo Apple/Samsung thành danh mục con. Gợi ý brand_categories mở rộng xuống hậu duệ và lên tổ tiên; hãng thực sự dùng trong sản phẩm được gợi ý ở nhóm đó/các nhóm cha. Lọc nhóm/hãng tại database trước phân trang.

POST product cho phép brand_id null; PATCH bỏ qua/null giữ hãng, clear_brand=true xóa hãng. ProductResponse thêm category_id/brand_id/brand_name; CategoryResponse thêm parent_id. V9 chỉ gán Apple cho hàng từng thuộc iphone; không đoán hãng các hàng khác từ tên/mô tả.

**product_images**

```text
product_id REFERENCES products(id), position, image_url
PRIMARY KEY(product_id, position)
CHECK(0 <= position AND position < 8)
```

Ảnh bìa vẫn ở products.image_url. image_urls trong API là ảnh bổ sung theo thứ tự; POST bỏ qua/null lưu mảng rỗng, PATCH bỏ qua/null giữ ảnh và mảng rỗng xóa ảnh bổ sung. Mỗi link tối đa 2.048 ký tự, validate như ảnh bìa và không trùng trong danh sách. ADMIN sửa catalog; khóa dòng sản phẩm khi PATCH để các lượt đổi bộ ảnh không xen lẫn nhau. Collection tải theo lô, không fetch join làm sai phân trang. Xem docs/product-gallery.md.

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
POST  /api/v1/products/{id}/variants
PATCH /api/v1/products/{id}/variants/{variantId}

GET   /api/v1/categories
POST  /api/v1/categories
GET   /api/v1/brands
POST  /api/v1/brands

GET   /api/v1/warehouses
POST  /api/v1/warehouses
GET   /api/v1/warehouses/order-options
GET   /api/v1/warehouses/operating-options
GET   /api/v1/storefront/branches
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

- V9/V10 chuẩn bị 71 danh mục và 76 hãng; V13 hoàn thiện Tivi/Điện máy, Phụ kiện, Hàng cũ: database mới có 125 danh mục và 84 hãng. Profile demo mặc định chuẩn bị 4 tài khoản, 3 kho và phân công staff Hà Nội; chế độ nhập tay không tạo lại danh mục đã sửa/xóa. DEMO_SEED_CATALOG=true mới thêm 5 nhóm công nghệ (thành 130 danh mục trên database mới), 24 sản phẩm TECH-, 72 inventory và 72 GOODS_RECEIPT. Số lượng trên database đã sử dụng có thể khác vì giữ mọi danh mục tự nhập. Khởi động lại giữ dữ liệu đã có; không seed đơn giả.
- PostgreSQL có trigger cấm UPDATE/DELETE ledger; H2 có Java trigger tương ứng và test bất biến. Chưa có suite Testcontainers chạy migration PostgreSQL tự động trong CI.
- Scheduler hết hạn đã khóa/recheck order và chạy từng đơn trong transaction riêng; chưa có distributed scheduler lock.
- JWT mặc định một giờ. Tài khoản INACTIVE bị chặn cả đăng nhập và xác thực token đã phát; chưa có refresh token hoặc thu hồi từng token khi logout.
- Mục tiêu cũ chỉ làm dashboard trình diễn được thay bằng storefront và dashboard riêng. `.env` chỉ được Compose đọc; Spring Boot chạy trực tiếp dùng environment variables/IDE configuration.

Giai đoạn API đầu tiên cho mục tiêu mới bổ sung `GET /api/v1/orders`: lọc trạng thái/kho, phân trang 0-based, size 1–100, thứ tự created_at/id giảm dần. ADMIN/MANAGER xem mọi kho; staff chỉ thấy kho được phân công, kể cả tổng số đơn. Staff chọn kho ngoài phạm vi trả 403; không có phân công trả trang rỗng. CUSTOMER vẫn dùng `/orders/my` và kiểm tra chủ đơn ở API chi tiết.

Giai đoạn 2 có API pack/ship/deliver/return. Chỉ ADMIN/MANAGER và staff được phân công kho thao tác; CUSTOMER bị chặn. OrderResponse và /orders/my có shipment (tracking_code/status/shipped_at/delivered_at), null trước đóng gói. Ship nhận body tùy chọn với tracking_code; pack cấp sẵn SF-TRACK-UUID vì schema tracking_code NOT NULL. Mã không được đổi sau ship, có UNIQUE chống trùng giữa hai đơn.

Fulfillment dùng khóa dòng order chung với cancel/payment/expiry, kiểm tra chuyển trạng thái và idempotency tại trạng thái đích. Return nhận lại toàn bộ hàng theo inventoryId tăng dần, ghi movement mới và hoàn tiền mô phỏng cùng transaction; lỗi một mặt hàng phải rollback tất cả. Hủy sau SHIPPED bị chặn; đơn PACKED bị hủy giữ shipment PREPARING như lịch sử. Các ca nghiệp vụ/concurrency nằm tại [FulfillmentIntegrationTest](src/test/java/com/stockflow/order/FulfillmentIntegrationTest.java).

Giai đoạn 3 đã triển khai storefront/dashboard theo role và work queue có pack/ship/deliver/return. Chi nhánh công khai chỉ trả id/code/name của kho ACTIVE; lựa chọn vận hành đọc phân công hiện tại và giữ cả kho ngừng bán để xử lý đơn lịch sử. `/products` có `q` tìm tên/SKU trước phân trang và escape wildcard LIKE; storefront gửi `status=ACTIVE`. Không sửa schema/migration hoặc logic tồn.

Storefront có menu nhiều cột lấy cây danh mục/hãng thật từ API; ADMIN tạo nhóm con/hãng qua dashboard. GET /products kết hợp categoryId (cả hậu duệ), brandId, từ khóa/trạng thái/minPrice/maxPrice trước phân trang. Giá gồm cả đầu mút; giá tăng/giảm/mới nhất có ID làm thứ tự phụ. Sort hỗ trợ id/sku/name/unitPrice/status/createdAt và alias unit_price/created_at; input sai trả 400. Laptop/âm thanh có khoảng giá riêng; thông số chip/màn hình/nhu cầu còn trong lộ trình. Xem docs/catalog-categories-and-brands.md.

Khách chỉ có nút hủy PENDING; quản lý hủy CONFIRMED/PACKED trước SHIPPED. Staff không thấy ledger/reports/catalog admin; form nhập chỉ ADMIN/STAFF đúng contract. Demo login JWT thật đổi ngữ cảnh và xóa dữ liệu riêng/hủy request chậm. Giỏ ở bộ nhớ, giữ qua đăng nhập checkout của khách vãng lai nhưng xóa khi reload hoặc đổi tài khoản đã đăng nhập.

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

### Giai đoạn 3 — Hai nhóm giao diện đã triển khai

- Storefront ACTIVE/tìm kiếm/danh mục/phân trang, giỏ nhiều sản phẩm, chọn chi nhánh, đặt hàng, lịch sử/tracking, thanh toán và hủy PENDING.
- Dashboard theo role: work queue/fulfillment, inventory/stock-in, ledger, reports và catalog admin.
- Giữ 183 test cũ, thêm 11 test: 194 PASS. Chrome headless có 49 mốc PASS trên PostgreSQL QA riêng, gồm desktop/mobile, chuyển role, lỗi 403/409 và hoàn kho.
- UI không tự quyết định giá, tồn, quyền hay trạng thái. Không thêm bảng carts hoặc bước build/dependency frontend.

### Giai đoạn 4 — Ảnh bìa đã triển khai; contract nhận hàng còn lại

- products.image_url lưu ảnh bìa qua POST/PATCH dành cho ADMIN; response public và form quản trị có xem trước, thay/xóa ảnh. Null/bỏ qua PATCH giữ nguyên, chuỗi trống xóa ảnh. HTTP/HTTPS hoặc /assets/ được validate, backend không tải ảnh bên ngoài. V8 thêm gallery qua image_urls; form nhập mỗi dòng một link, đổi thứ tự/xóa ảnh, trang chi tiết có thumbnail và nút trước/sau. Chưa có upload file.
- products.description lưu mô tả văn bản tối đa 5.000 ký tự qua POST/PATCH ADMIN; null/bỏ qua PATCH giữ nguyên, chuỗi trống xóa mô tả. Bấm thẻ mở trang `/san-pham/{id}`, không dùng popup hoặc nút Xem chi tiết riêng ở storefront. Liên kết hỗ trợ mở tab mới, tải lại và Back/Forward; mô tả được escape để giữ an toàn. Chọn số lượng/chi nhánh rồi thêm vào giỏ hiện có. Controller trang chỉ forward giao diện, không mở quyền ghi catalog hoặc số liệu kho. Xem docs/product-details.md.
- 24 test mới đưa suite lên **218 PASS**; test/package BUILD SUCCESS. V6 chạy trên PostgreSQL QA 13.2; Chrome có **69 kiểm tra PASS** (49 hồi quy, 20 ảnh/form). Danh sách 24 file và giới hạn kiểm chứng ở docs/product-images.md.
- Khách tiếp tục chọn kho/chi nhánh phục vụ, một đơn thuộc một kho. Chốt thông tin người nhận/địa chỉ trước khi thêm schema checkout. Không làm tự allocation/split order trong MVP này.
- Nếu cần schema mới, thêm migration kế tiếp cho PostgreSQL/H2; giữ snapshot giá và reserve nhiều sản phẩm trong một transaction.
- Giữ giỏ ở frontend cho MVP; ownership đơn vẫn bắt buộc.

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

Storefront/dashboard được kiểm chứng cục bộ bằng Chrome headless và PostgreSQL. Đã có ảnh bìa URL, gallery có thứ tự, mô tả văn bản và chế độ catalog nhập tay; xem docs/product-images.md, docs/product-gallery.md và docs/product-details.md. Giao diện quản lý Catalog & Sản phẩm đã được tách thành 2 tab độc lập ("Sản phẩm" và "Danh mục") dạng menu xổ xuống (nav-group), kèm dialog/modal tạo mới sản phẩm, sửa sản phẩm, xem chi tiết tồn kho 3 chi nhánh và tạo mới danh mục (thay thế hoàn toàn inline forms cũ). Thao tác xóa sản phẩm tuân thủ quy tắc nghiệp vụ: chuyển trạng thái INACTIVE an toàn qua PATCH thay vì DELETE vật lý. Chưa có deployment công khai. Thông tin nhận hàng đã triển khai V15 theo quyết định ngày 04/10/2026. Upload file ảnh, bộ lọc thông số chuyên biệt, Testcontainers và các mục lộ trình chưa triển khai không được coi là đã hoàn thành.

Ngày 02/10/2026, storefront đổi chi tiết sang trang `/san-pham/{id}` và toàn thẻ là liên kết, giữ nút thêm giỏ riêng. **234 test PASS**, package thành công và **42 kiểm tra Chrome PASS** trên PostgreSQL QA riêng; có Back/Forward, Ctrl-click, reload URL và hồi quy fulfillment/hoàn kho. Không có schema/migration mới hoặc thay đổi dữ liệu sử dụng thực tế trong lượt chuyển trang. Xem danh sách 12 file và phạm vi kiểm chứng trong docs/product-details.md.

Ngày 02/10/2026, bổ sung thư viện ảnh V8: **260 test PASS**, package thành công sau test và **78 kiểm tra Chrome PASS** (36 bộ ảnh, 42 hồi quy). PostgreSQL QA chạy JAR V7 rồi nâng cấp V8, giữ nguyên 24 sản phẩm, 72 tồn kho, 72 movements và ảnh bìa. Bộ ảnh ban đầu rỗng; không tự tìm ảnh hoặc ghi dữ liệu vào database sử dụng thực tế. Khởi động lại ứng dụng để áp dụng V8 rồi Ctrl + F5. Hướng dẫn, contract, 22 file thay đổi và giới hạn kiểm chứng ở docs/product-gallery.md.

Mốc menu/giá ban đầu: **295 test PASS**, **74 kiểm tra Chrome PASS**, schema V8; đây là lịch sử ở docs/catalog-discovery.md.

Hãng/cây danh mục V9/V10: **353 test PASS**, JAR BUILD SUCCESS, **84 kiểm tra Chrome PASS** (42 mới + 42 hồi quy), 0 exception JavaScript trên PostgreSQL QA 13.2. Snapshot kiểm chứng nâng cấp giữ sản phẩm/ảnh/tồn/ledger/đơn/tài khoản. Database thật stockflow tại 5432 đã lên V10, có 81 danh mục/76 hãng, giữ 4 sản phẩm đã nhập và hai iPhone thuộc Điện thoại/Apple. Backup trước V10: target/db-backups/catalog-expansion-20261002_081730/stockflow.dump. File/giới hạn ở docs/catalog-categories-and-brands.md; khởi động lại Spring Boot và Ctrl+F5 để dùng bản mới.


<!-- Mốc V11 hiện tại; các con số V9/V10 phía trên là lịch sử nghiệm thu. -->
Ngày 02/10/2026, mốc V11 bổ sung thông số nhập tay và nhóm màu: **388 test PASS**, JAR BUILD SUCCESS, **86 kiểm tra Chrome PASS**. Đây là lịch sử triển khai chỉ gộp màu; phạm vi đó được mở rộng bằng V12 dưới đây. Backup và 28 file/giới hạn V11 ở docs/product-specifications-and-colors.md.

<!-- Quyết định mới đã chốt: một thẻ model như Apple Watch SE 3, chọn phiên bản rồi màu. -->
V12 thêm product_versions, bảng thông số riêng và version_id trên mapping màu. Nhóm màu V11 giữ nguyên ID/SKU/giá/ảnh/tồn/đơn trong phiên bản trung tính “Phiên bản hiện tại”; ADMIN đổi nhãn theo dữ liệu đúng. Không sửa migration V1–V11 hoặc tự merge SKU đã tạo độc lập. Cùng màu được phép ở phiên bản khác; UNIQUE và khóa ngoại ghép chặn trùng/sai model. API POST/PATCH versions chỉ ADMIN; các màu dùng version_id, contract variants V11 vẫn hoạt động khi bỏ trường này. Thông số riêng ghi đè nhãn chung, null giữ bảng và [] xóa ghi đè. Tạo/sửa cấu hình dùng cùng khóa model và transaction.

Storefront/admin danh sách vẫn grouped=true tại SQL trước phân trang; filter/sort giá dùng min_price của SKU ACTIVE, unit_price giữ giá SKU gốc cho client cũ. Trang chi tiết chọn phiên bản rồi chỉ thấy các màu tương ứng; đổi cấu hình giữ số lượng/chi nhánh, cập nhật SKU/giá/ảnh/thông số. Giỏ, order, inventory và ledger tiếp tục dùng Product ID của SKU; không gộp tồn các cấu hình. DISPATCH/fulfillment/return không thay đổi. Không mở API tồn/ledger cho khách.

**411 test PASS**, **81 kiểm tra Chrome PASS** (39 mới + 42 hồi quy), package BUILD SUCCESS, 0 exception JavaScript. PostgreSQL QA 13.2 nâng V11→V12 giữ 13 nhóm snapshot và 6 SKU thực tế; 12 request tranh 3 chiếc chỉ tạo 3 đơn, các cấu hình khác không bị lấy hàng. Khởi động lại Spring Boot để áp dụng V12 rồi Ctrl+F5. Hướng dẫn/API/file/giới hạn ở docs/product-models-versions-and-colors.md; backup target/db-backups/product-versions-20261002_173343/stockflow.dump. Không coi merge SKU cũ, landing page dòng máy, lọc thuộc tính, snapshot tên/cấu hình order item hoặc Testcontainers là đã hoàn thành.

<!-- Kết quả trang bán lẻ hiện tại tách khỏi các mốc triển khai V11/V12 phía trên. -->
Lượt hoàn thiện trang sản phẩm theo tham chiếu CellphoneS: **424 test PASS**, package BUILD SUCCESS và **114 kiểm tra Chrome PASS** trên PostgreSQL QA riêng, 0 exception JavaScript. Giữ một thẻ/model; phiên bản và màu có ảnh/giá riêng, URL SKU giữ lựa chọn qua reload/Back/Forward, breadcrumb nối nhóm cha và hãng. Thêm GET /api/v1/products/{id}/availability chỉ báo còn/hết tại kho ACTIVE, có no-store; không mở quantity/ledger hoặc thay atomic reserve. Đã bỏ đánh giá/lượt bán và nhãn bán chạy ghi cứng. Không có migration mới, mọi V1–V12 giữ hash. Hướng dẫn, 13 file thay đổi và giới hạn kiểm chứng ở docs/retail-product-detail.md. Khởi động lại Spring Boot rồi Ctrl+F5.

<!-- Mốc V13 theo ba ảnh tham khảo mới; không thêm hàng mẫu hay tự gán logo hãng. -->
V13 hoàn thiện nhánh Tivi/Điện máy, Phụ kiện và Hàng cũ, bổ sung tám hãng còn thiếu và gợi ý hãng theo loại hàng; hãng được dùng chung cho hàng mới/cũ. Thêm brands.logo_url nullable, POST /brands nhận logo tùy chọn, PATCH /brands/{id}/logo chỉ ADMIN. PATCH bắt buộc chuỗi logo_url; chuỗi trống xóa, null/bỏ trường trả 400. URL HTTP/HTTPS hoặc /assets/ được kiểm tra bằng ImageUrl hiện có; backend không tải ảnh bên ngoài. Không có upload file ở lượt này.

ADMIN vào Danh mục & Sản phẩm → Danh mục, phần Thương hiệu có tìm kiếm, Sửa logo và form thêm hãng với preview ảnh. Menu cửa hàng dùng logo đã lưu kèm tên hãng, giữ bộ lọc ID; ảnh lỗi hiện tên/chữ viết tắt. Kiểm chứng: **453 test PASS**, package BUILD SUCCESS, **121 kiểm tra Chrome PASS** (40 danh mục/logo + 39 phiên bản/màu + 42 mua hàng/fulfillment), không có exception JavaScript trong các bài kiểm chứng.

PostgreSQL thật stockflow tại localhost:5432 đã lên V13 ngày 03/10/2026, thêm 51 danh mục và 8 hãng thành 132 danh mục/84 hãng. Đối chiếu snapshot giữ 15 nhóm dữ liệu nghiệp vụ, toàn bộ ID/giá trị danh mục/hãng và liên kết cũ. Không sửa V1–V12 hoặc seed sản phẩm/tồn. Backup trước migration: target/db-backups/brand-logos-live-20261003_072254/stockflow.dump. Không dừng app 8080; người dùng khởi động lại Spring Boot để nạp API Java mới, rồi Ctrl+F5. Danh sách 19 file/hướng dẫn/giới hạn tại docs/catalog-completion-and-brand-logos.md.

<!-- CRUD danh mục được thêm riêng; không mở quyền ghi cho khách và không xóa dây chuyền dữ liệu nghiệp vụ. -->
Ngày 03/10/2026, bổ sung tìm/sửa/xóa danh mục: GET /categories nhận q tùy chọn, PATCH /categories/{id} sửa tên/slug và DELETE /categories/{id} xóa nhóm trống. PATCH/DELETE chỉ ADMIN; sửa giữ ID/cha/con, xóa chặn mọi SKU kể cả INACTIVE hoặc danh mục con và chỉ gỡ gợi ý brand_categories. Transaction/khóa dòng/UNIQUE/FK bảo vệ cập nhật, lỗi cạnh tranh trả 409. UI Admin có ô tìm tên/slug/ID/đường dẫn, nút Sửa/Xóa từng dòng và xác nhận xóa.

Chế độ app.demo.seed-catalog=false không còn tự khôi phục năm nhóm demo đã xóa/đổi slug. Database mới sau V13 có 125 danh mục; bật seed catalog mới thêm năm nhóm Tech cùng sản phẩm/tồn mẫu. Không thay đổi dữ liệu có sẵn hoặc migration V1–V13. **479 test PASS**, package BUILD SUCCESS, **66 kiểm tra Chrome PASS** (24 danh mục + 42 mua hàng/fulfillment), không có exception JavaScript trên PostgreSQL QA riêng. Database sử dụng thực tế/app 8080 được giữ nguyên; khởi động lại Spring Boot rồi Ctrl+F5 để dùng API/giao diện mới. Danh sách 15 file, hợp đồng API và giới hạn tại docs/category-management.md.

<!-- V14 bổ sung xóa/khôi phục cấu hình theo yêu cầu người dùng; không xóa SKU hoặc phá ledger bất biến. -->
Ngày 03/10/2026, quản trị sản phẩm có hai nút/tab riêng Phiên bản và Màu sắc. Phiên bản quản lý tên/thông số; Màu sắc chọn phiên bản và quản lý SKU/giá/ảnh/trạng thái. Mỗi phần có Xóa kèm xác nhận tên; bật “Hiện phiên bản và màu đã xóa” để khôi phục. Chuyển tab giữ dữ liệu chưa lưu, hỗ trợ bàn phím và mobile.

V14 PostgreSQL/H2 thêm archived mặc định false vào product_versions và product_variants. DELETE /products/{productId}/versions/{versionId} lưu trữ phiên bản, POST đường dẫn đó /restore khôi phục; DELETE /variants/{variantId} lưu trữ màu và PATCH status=ACTIVE khôi phục qua contract cũ. Mọi API ghi chỉ ADMIN, khóa cùng model trong transaction và kiểm tra đúng quan hệ model/cấu hình. Xóa phiên bản ẩn tất cả màu thuộc nó, không tự đổi trạng thái riêng của màu; khôi phục giữ màu đã xóa/ngừng bán riêng. Xóa màu giữ SKU gốc/model hoặc dừng SKU con, không đổi tồn hoặc ghi movement.

Response thêm archived; storefront ẩn lựa chọn đã xóa, OrderService chặn checkout trực tiếp, availability báo không bán và giá từ loại cấu hình đã xóa tại SQL. Đơn đã đặt vẫn thanh toán/hủy/giao/hoàn đúng SKU. Không sửa V1–V13, không tái sử dụng SKU/tên đang được dữ liệu lưu trữ giữ và không thêm cơ chế xóa vật lý/chuyển màu giữa phiên bản.

**496 test PASS**, package BUILD SUCCESS, **69 kiểm tra Chrome PASS** (27 cấu hình + 42 mua hàng/fulfillment) trên JAR cuối, không exception JavaScript. Nâng V13→V14 trên PostgreSQL QA giữ 18 nhóm snapshot và archived=false cho cấu hình cũ. Database thật/app 8080 giữ nguyên; khởi động lại Spring Boot rồi Ctrl+F5 để áp dụng V14/API/UI. Backup QA: target/db-backups/configuration-management-20261003_092923/stockflow.dump. Hướng dẫn, 20 file thay đổi và giới hạn tại docs/product-configuration-management.md.

<!-- Checkout V15 đã được người dùng chốt; snapshot người nhận không thay đổi khi hồ sơ hoặc vòng đời đơn đổi. -->
Ngày 04/10/2026, triển khai checkout V15: tên người nhận, điện thoại và địa chỉ bắt buộc, ghi chú tùy chọn; miễn phí giao hàng. DTO POST /orders thêm delivery bắt buộc; response tạo/chi tiết/my/vòng đời trả bản chụp. Muốn đổi thông tin phải hủy PENDING và đặt lại, không có API sửa địa chỉ. Giữ kho do khách chọn, giá do server tính, atomic reserve, transaction, thứ tự inventory ID và DISPATCH lúc payment.

V15 PostgreSQL/H2 thêm recipient_name/recipient_phone/delivery_address/delivery_note nullable trên orders; CHECK cho toàn null của đơn cũ hoặc bản chụp đầy đủ. Đơn cũ trả delivery=null, vẫn thanh toán/hủy/giao/hoàn; không bịa địa chỉ từ hồ sơ. Value object không có setter và mapping updatable=false; snapshot readonly ở tầng ứng dụng, không dùng trigger bất biến của ledger cho địa chỉ.

Giỏ có form người nhận, phí giao hàng miễn phí và hướng dẫn hủy PENDING nếu cần đổi. Login checkout giữ giỏ/người nhận; lỗi 400/409 giữ dữ liệu để chỉnh, đặt xong/đăng xuất/đổi tài khoản xóa dữ liệu chưa gửi. Chi tiết khách/dashboard hiển thị người nhận và ghi chú dưới dạng văn bản đã escape, tương thích sáng/tối/mobile. Khách chỉ xem đơn mình, staff chỉ xem kho phân công; summary vận hành không trả địa chỉ.

**526 test PASS** (498 baseline + 28 ca mới), test/package BUILD SUCCESS; **24 kiểm tra API PostgreSQL và 25 kiểm tra Chrome PASS**, 0 exception JavaScript. JAR V14→V15 trên PostgreSQL QA 13.2 giữ 19 bảng snapshot; hai CHECK thử bằng SQL thật đều chặn dữ liệu sai. 16 request tranh 5 chiếc chỉ tạo 5 đơn, tồn không âm và hủy hoàn đủ. Database sử dụng thực tế/app 8080 giữ nguyên; khởi động lại Spring Boot rồi Ctrl+F5 để áp dụng V15. 29 file, contract và giới hạn tại [docs/checkout-delivery.md](docs/checkout-delivery.md). Chưa có idempotency tạo đơn phía server, hồ sơ chỉnh sửa, đánh giá, refresh token hoặc Testcontainers; triển khai từng lượt riêng.

<!-- Mốc V16 thay hiện trạng hồ sơ còn thiếu tại mốc V15; không thay lịch sử nghiệm thu trước. -->
## Hồ sơ liên hệ V16 — ngày 05/10/2026

Đã thêm PATCH `/api/v1/users/me` chỉnh tên và số điện thoại tùy chọn của chính người đăng nhập. ID lấy từ JWT, không tiếp nhận thay email/hash/role/status. Các role ACTIVE đều sửa được liên hệ của mình; giao diện `/#shop/account` dành cho CUSTOMER, dashboard vận hành giữ nguyên. Bỏ qua/null giữ giá trị cũ, phone rỗng xóa số; UPDATE theo từng trường không ghi đè credential/quyền hoặc làm mất cập nhật khác trường đang chạy đồng thời.

V16 PostgreSQL/H2 thêm users.phone nullable và CHECK định dạng. Không sửa V1–V15, không suy đoán số của user cũ. Checkout chỉ gợi ý tên/số vào ô trống khi mở giỏ, giữ người nhận nhập tay và mọi snapshot đơn cũ. Không lưu liên hệ vào sessionStorage; đổi actor/đăng xuất xóa form và loại response cũ. Sửa thêm xử lý đường dẫn không tồn tại: trả 404 thay vì 500.

**555 test PASS** (526 baseline + 29 ca mới), package BUILD SUCCESS. **40 kiểm chứng Chrome hồ sơ PASS**, **12 kiểm chứng API PostgreSQL và 7 CHECK SQL PASS** trên QA PostgreSQL 13.2 riêng; thêm **56 kiểm chứng hồi quy giỏ/đồng bộ đơn PASS** bằng API giả lập. Hồ sơ sáng/tối được kiểm tra 1440/390/375px, không tràn ngang; không exception JavaScript. Test Maven dùng H2, nâng V15→V16 bảo toàn user cũ được kiểm tra ở H2; PostgreSQL QA nạp V1–V16 và kiểm tra API/constraint thật. Chưa nâng database của người dùng hoặc khởi động lại app 8080. Khởi động lại Spring Boot và Ctrl+F5 để áp dụng V16/API/UI.

18 file của lượt hồ sơ, contract và giới hạn ở [docs/customer-profile.md](docs/customer-profile.md). Giỏ/đơn từ lượt trước giữ nguyên, tài liệu tại [docs/cart-and-order-sync.md](docs/cart-and-order-sync.md). Tiếp theo ưu tiên idempotency tạo đơn phía server; sổ địa chỉ, đổi email/mật khẩu, đánh giá sau mua, thanh toán sandbox, refresh token và Testcontainers vẫn là các lượt riêng.

<!-- Lựa chọn nhận diện mới độc lập với nghiệp vụ đã nghiệm thu. -->
## Nhận diện xanh dương — ngày 05/10/2026

Người dùng đã chọn xanh dương cho StockFlow: sáng `#2563EB`, hover `#1D4ED8`, nền nhấn `#EFF6FF`; tối `#60A5FA` trên navy. Đã đồng bộ storefront/dashboard, banner/logo/favicon, danh mục, nút mua và viền phiên bản/màu; các màu trạng thái kho/lỗi/cảnh báo và màu SKU/ảnh/logo hãng giữ nguyên. Không đổi API, database, JWT hoặc nghiệp vụ giỏ/đơn. Chi tiết token màu và kiểm chứng tại [docs/blue-theme.md](docs/blue-theme.md).

**555 test PASS**, package BUILD SUCCESS; sau chỉnh nền danh mục tối chạy thêm 14 test tài nguyên web PASS. **85 kiểm chứng Chrome PASS** bằng API giả lập và server tài nguyên riêng, desktop 1440px/mobile 375px, sáng/tối; không exception JavaScript. Giữ logic theme.js, kích thước/bố cục và màu trạng thái; chỉ các cặp màu nút chính được đo tương phản, không coi là audit khả năng tiếp cận toàn website. 5 file tĩnh và 3 file tài liệu; không thêm dependency/runtime mới.

<!-- Khu trưng bày mới chỉ lấy dữ liệu từ trang catalog hiện có, không sinh ưu đãi hoặc thay nghiệp vụ. -->
## Khu trưng bày công nghệ — ngày 05/10/2026

Thay banner SVG bằng nền navy và tối đa ba model có ảnh/giá/liên kết từ trang ACTIVE/grouped đang xem. Danh mục desktop cuộn riêng trong khung 420px; tablet/mobile đưa banner lên trước dải danh mục. Giữ mở menu tại ô ngoài, điều hướng `/san-pham/{id}`, giỏ/checkout và các API. Ảnh được chứa trọn trong khung; có trạng thái tải/rỗng/lỗi, hiệu ứng chuột nhẹ và hỗ trợ giảm chuyển động. Không tạo khuyến mãi, rating, lượt bán hoặc nhãn bán chạy giả.

**555 test PASS**, thêm lượt **14 test tài nguyên web PASS**, package cuối BUILD SUCCESS. **85 kiểm chứng Chrome hồi quy và 52 kiểm chứng banner PASS** ở hai theme, 1440/1024/768/390/375px; không exception JavaScript. Hai nhóm chạy riêng và có kiểm tra chung. Chrome dùng API giả lập; chỉ đọc catalog/danh mục/hãng công khai của app đang chạy để chụp ảnh thật, không ghi vào database của người dùng. Maven dùng H2 hiện có; không thêm dependency, không sửa Java/migration/test trong lượt này. 7 file thay đổi và giới hạn tại [docs/storefront-showcase.md](docs/storefront-showcase.md).

<!-- Bộ lọc giá giữ contract minPrice/maxPrice, các đầu mút thập phân và phạm vi catalog hiện có. -->
## Thanh trượt lọc giá — ngày 05/10/2026

Đã thêm thanh kéo hai đầu, nhãn VND, nút Đặt lại và đồng bộ ô nhập/chip chọn nhanh. Kéo chỉ xem trước; thả tay gửi một GET catalog. Giá nhập tay giữ hai chữ số thập phân, kiểm tra âm/vượt giới hạn/đảo hai đầu. Thang mặc định 0–50 triệu, đầu trên là “Không giới hạn”; nhập giá lớn tự mở rộng thang. Lọc mới về trang đầu, giữ danh mục/hãng/từ khóa/sắp xếp và URL; lỗi đọc cho phép thử lại cùng mức giá. Giữ nguyên API, giỏ, JWT, fulfillment và database.

**555 test Maven PASS**, package BUILD SUCCESS. **85 kiểm chứng Chrome hồi quy và 56 kiểm chứng lọc giá PASS** ở sáng/tối, 1440/1024/768/390/375px, chuột/cảm ứng/bàn phím; không ngoại lệ JavaScript. Hai nhóm có kiểm tra chung; Chrome dùng API giả lập, Maven dùng H2 hiện có. Không ghi database hay khởi động lại app đang chạy. Bốn file tĩnh và ba file tài liệu; hướng dẫn, giới hạn và danh sách ở [docs/price-filter.md](docs/price-filter.md).

<!-- Bố cục tham khảo chỉ dùng những điều kiện lọc có dữ liệu và API hiện hành. -->
### Bố cục bộ lọc theo ảnh tham khảo — ngày 05/10/2026

Đưa sắp xếp và số kết quả trang/tổng cạnh tiêu đề; hãng có nút chọn nhanh đồng bộ dropdown, theo danh mục hiện tại. Dải hãng dài cuộn ngang trong khung. Thanh giá và năm mốc chọn nhanh nằm trong panel gọn; mở “Nhập giá chính xác” khi cần, lỗi giá tự mở phần này để sửa. Mobile chia mốc giá thành hai hàng rõ ràng. Giữ màu xanh dương và toàn bộ contract lọc/giỏ/đơn. Các nhãn giảm giá, freeship, bảo hành và xếp hạng bán chạy trong ảnh tham khảo chưa có dữ liệu tương ứng nên không đưa vào bộ lọc.

**555 test PASS**, thêm **14 test tài nguyên web PASS**, package BUILD SUCCESS. Ba nhóm Chrome riêng PASS: **85 hồi quy, 56 lọc giá, 55 bố cục/hãng/phần giá mở rộng**; kiểm tra cả hai theme và 1440/1024/768/390/375px, không ngoại lệ JavaScript. Chrome dùng API giả lập; ảnh cuối dùng snapshot catalog công khai từ lượt banner. Không sửa Java/migration/DTO/test JUnit hoặc database. Bốn file tĩnh và ba file tài liệu, log riêng trong `target/catalog-reference-qa/`; chi tiết tại [docs/price-filter.md](docs/price-filter.md).
