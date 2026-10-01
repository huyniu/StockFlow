<!-- Đánh giá source và bàn giao giai đoạn API đầu tiên cho mục tiêu cửa hàng nhiều kho, ngày 30/09/2026. -->
# StockFlow: cửa hàng nhiều kho và lộ trình API

> Cập nhật giai đoạn 2: khách chọn kho/chi nhánh và DISPATCH lúc payment đã được chốt. Fulfillment PACKED/SHIPPED/DELIVERED/RETURNED và shipment tracking đã có API; xem [báo cáo kiểm chứng](fulfillment-verification.md). Các mục hiện trạng/kiểm thử giai đoạn 1 dưới đây giữ lại như lịch sử; không coi các khoảng trống tại mốc đó là trạng thái hiện tại.

## Mục tiêu sản phẩm

Một cửa hàng/doanh nghiệp sở hữu nhiều kho, bán trực tiếp cho khách. Bản hoàn thiện có storefront cho khách và dashboard cho admin/nhân viên; cùng dùng backend Java 17, Spring Boot 3.4.5, PostgreSQL và Flyway hiện tại. Không thêm seller, tenant hoặc marketplace.

Portfolio ưu tiên tính đúng đắn của database, transaction, quyền sở hữu/phạm vi kho và kiểm thử. Payment/shipping mô phỏng; giỏ ở frontend đủ cho MVP.

## Hiện trạng đã đọc trước khi sửa

Đã đọc source Java, test, migration PostgreSQL/H2 V1–V5, frontend tĩnh, Maven/configuration/CI/Docker và các tài liệu trong repository, gồm ANTIGRAVITY_HANDOFF.md.

- **Auth/user:** register CUSTOMER, login BCrypt/JWT, users/me; role được nạp từ DB. Trước lượt này login/filter chưa chặn INACTIVE, trong khi OrderService đã kiểm tra trạng thái actor.
- **Catalog/warehouse:** category/product public reads, lọc danh mục/trạng thái và phân trang; ADMIN tạo category/product/kho và sửa tên/giá/trạng thái sản phẩm. Kho lựa chọn đặt hàng trả DTO tối thiểu, chỉ kho ACTIVE.
- **Inventory:** stock-in cho ADMIN/nhân viên kho được giao; đọc tồn theo phạm vi; physical = available + reserved. Ledger có actor, tham chiếu, balance trước/sau; PostgreSQL trigger và H2 Java trigger chặn UPDATE/DELETE.
- **Order:** nhiều sản phẩm trong một transaction, atomic conditional reserve và thứ tự inventory ID chống khóa chéo; snapshot giá, giữ 15 phút, my/detail, payment mô phỏng idempotent, cancel/refund/restock và expiry. Lifecycle khóa dòng order và kiểm tra lại trạng thái.
- **Report:** revenue theo ngày/tháng/kho, top products, low stock và order summary; MANAGER/ADMIN, SQL tổng hợp và phân trang. Có tài liệu benchmark PostgreSQL riêng.
- **Portfolio/demo:** Swagger JWT, demo 3 kho/24 sản phẩm/72 inventory và receipts, workflow CI test/package, dashboard HTML/CSS/JS cùng Spring Boot. Dashboard kết hợp cả bốn role để trình diễn, chưa là hai giao diện hoàn thiện.

Đây là hiện trạng source và các contract được test; không có nghĩa mọi tính năng mục tiêu đã được triển khai hoặc đã chạy trên GitHub/public deployment.

## Khoảng trống của luồng bán hàng

Khách hiện đã có thể xem sản phẩm, thêm nhiều mặt hàng vào giỏ demo, chọn kho, đặt/giữ hàng và xem đơn của mình. Còn thiếu tìm kiếm storefront, ảnh/mô tả sản phẩm, contract public chỉ sản phẩm đang bán, thông tin nhận hàng và chính sách chọn kho ở checkout. Chi tiết đơn chưa trả shipment/tracking hoặc hành trình giao nhận. Không cần thêm bảng carts để giải quyết các điểm này.

Người vận hành trước lượt này chỉ tra đơn bằng ID. Sau CONFIRMED chưa có API PACKED, SHIPPED, DELIVERED, RETURNED, dù enum/entity/bảng shipments đã có. Đây là phần thiếu lớn nhất của luồng xử lý đơn. API tạo/sửa sản phẩm và nhập kho/báo cáo đã có; quản trị user/phân công nhân viên, kiểm kê và chuyển kho chưa có API riêng.

## Mâu thuẫn tài liệu đã sửa

- Handoff cũ mô tả JWT/Swagger/CI như chưa có, và foundation fixes đã hoàn tất như còn chờ.
- Testcontainers là định hướng, trong khi suite/CI thực tế dùng H2 và migration/trigger riêng. Không gọi H2 là kiểm thử PostgreSQL.
- Seed demo thực tế là 24 sản phẩm, không phải 100 sản phẩm với hàng nghìn đơn giả. Dữ liệu benchmark SQL là bộ riêng.
- Mục tiêu dashboard demo mỏng được thay bằng storefront và dashboard riêng. Giữ demo hiện tại trong thời gian hoàn thiện API.
- Handoff cũ liệt kê subtotal nhưng schema orders không có cột này; đã đối chiếu lại schema V1–V5.

## Giai đoạn 1 đã triển khai

### Danh sách đơn cho vận hành

```http
GET /api/v1/orders?warehouseId=1&status=CONFIRMED&page=0&size=20
Authorization: Bearer <access_token>
```

- `warehouseId` và `status` tùy chọn; status phải là enum OrderStatus hiện có.
- `page` bắt đầu từ 0, mặc định 0; `size` từ 1 đến 100, mặc định 20. Sai tham số trả 400.
- Thứ tự cố định `created_at DESC, id DESC`, không nhận biểu thức SQL/sort tùy ý.
- ADMIN/MANAGER xem mọi kho; nhân viên chỉ thấy các kho được phân công, dù không truyền warehouseId. Kho tường minh ngoài phân công trả 403; không có phân công trả 200 cùng trang rỗng.
- CUSTOMER gọi danh sách vận hành nhận 403; tiếp tục dùng `/orders/my`. Không JWT nhận 401. API chi tiết vẫn kiểm tra chủ đơn và phân công kho.
- Content gồm id, order_code, customer_id, warehouse_id, warehouse_name, status, total_amount, reservation_expires_at, created_at, updated_at. Đọc items qua `/orders/{id}`.
- Response giữ PageResponse: content, page, size, total_elements, total_pages, last.

Query dùng NamedParameterJdbcTemplate và Text Block. JOIN warehouses lấy tên kho; EXISTS kiểm tra phân công áp dụng cùng điều kiện cho COUNT và truy vấn trang. Không JOIN collection order_items trong phân trang, không lọc quyền sau LIMIT ở Java. Các tham số được bind, không ghép dữ liệu client vào SQL.

### Chặn tài khoản không hoạt động

Login chỉ phát JWT cho ACTIVE. JWT filter nạp trạng thái DB mỗi request và không xác thực INACTIVE, kể cả token đã phát trước đó. Mật khẩu đúng của tài khoản bị khóa vẫn nhận 401 cùng thông điệp đăng nhập sai. Các API public vẫn public.

Đây là kiểm tra trạng thái tài khoản, chưa phải refresh token hoặc blacklist/thu hồi vĩnh viễn từng token. Nếu tài khoản được kích hoạt lại thì token còn hạn có thể dùng lại; chính sách revocation là giai đoạn riêng.

### Các phần được giữ nguyên

Không thay migration V1–V5 hoặc schema, không thêm bảng carts/sellers. Không sửa reserve, stock-in, movement, expiry, payment/cancel/refund hoặc tính toán báo cáo. Frontend demo và contract `/orders/my`, tạo đơn/chi tiết/thanh toán/hủy được giữ. OrderController chỉ được định dạng lại và thêm GET danh sách, cùng mô tả Swagger.

## Quyết định đã chốt sau nghiệm thu giai đoạn 1

1. **Chọn kho:** khách chọn warehouse_id/chi nhánh Hà Nội, Đà Nẵng hoặc TP.HCM; một đơn thuộc một kho. Không làm allocation hoặc split order tự động.
2. **Thời điểm xuất:** payment CONFIRMED giảm reserved và ghi DISPATCH. Pack/ship/deliver không đổi tồn; cancel trước ship restock/refund. Không cần chuyển đổi đơn cũ hoặc sửa ledger lịch sử.
3. **Giao/hoàn:** ADMIN/MANAGER hoặc staff kho đó xác nhận DELIVERED và nhận trả toàn bộ đơn DELIVERED; RETURN_RESTOCK và REFUNDED trong cùng transaction. Chưa có customer return-request hoặc partial return.

Thông tin người nhận/địa chỉ checkout còn cần được chốt trước phần schema phụ thuộc, nhưng không cản trở fulfillment giai đoạn này.

## Lộ trình còn lại và tiêu chí nghiệm thu

1. **Fulfillment API theo kho đã triển khai:** pack, ship/tracking, deliver, receive return/refund. Giữ row lock, kiểm tra transition, role/phạm vi kho, idempotency và rollback toàn bộ return. Có test concurrent pack/return, ship/cancel và trùng tracking giữa hai đơn; xem báo cáo giai đoạn 2.
2. **Catalog/checkout storefront:** tìm kiếm và public visibility, chi tiết sản phẩm, snapshot người nhận/địa chỉ khi đã chốt, allocation toàn giỏ nếu được chọn. Thêm migration mới khi cần; giữ transaction/atomic update và snapshot giá. Customer không thể xem hoặc thao tác đơn khác.
3. **Storefront/dashboard riêng:** giỏ frontend, checkout, lịch sử đơn/vận đơn cho khách; catalog, nhập kho, danh sách xử lý đơn theo kho và báo cáo cho nhân viên. Mọi hành động gọi API đã nghiệm thu; UI không quyết định số tồn hay quyền.
4. **Củng cố kiểm thử/vận hành:** Testcontainers PostgreSQL cho migration/trigger/concurrency, rồi auth refresh/revocation và request tracing/metrics. Cache/distributed scheduler lock có yêu cầu rõ ràng và test trước khi thêm. Chuyển kho/kiểm kê để sau MVP bán hàng.

## Kiểm thử và build giai đoạn 1

Ngày kiểm chứng: 30/09/2026. Các lệnh Windows dùng đúng Maven cache yêu cầu:

```powershell
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' '-Dtest=OrderQueryIntegrationTest,AuthIntegrationTest' test
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' test
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' package
```

- Test tập trung: **31 PASS** (22 OrderQueryIntegrationTest, 9 AuthIntegrationTest).
- Toàn bộ `test`: **116 PASS**, 0 failures/errors/skipped, gồm 27 ca mới so với milestone demo 89 test.
- `package`: **BUILD SUCCESS**, chạy lại 116 test, tạo `target/stockflow-0.0.1-SNAPSHOT.jar`.
- PostgreSQL cục bộ **13.2**, database QA riêng và JAR chạy cổng 8096: **37 khẳng định PASS qua 31 request HTTP**. Kiểm tra login 4 role, đặt ba đơn ở hai kho, giữ 15 phút, thanh toán idempotent, management đọc mọi kho, scope staff trước COUNT/LIMIT, lọc/paging, 400/401/403, ownership khách, UTF-8/timestamp và INACTIVE với token cũ. Đọc danh sách không thay đổi tồn/version/timestamp/ledger.
- Instance/database QA đã dừng và xóa sau kiểm chứng; không đổi database StockFlow hoặc instance cổng 8080 đang dùng. Kết quả/log cục bộ nằm trong `target/storefront-phase1-*.log` và `target/storefront-phase1-pg-result.json` (không commit).

Test mới phủ management đọc nhiều kho, staff một/nhiều/không có phân công, thu hồi phân công với JWT cũ, 403 ngoài phạm vi, ownership khách, 401 anonymous, lọc trạng thái/kho, tổng số dòng trước phân trang, tie-break ID, tham số sai, trang rỗng và đọc không làm thay đổi inventory/version/payment/ledger. Auth test kiểm tra login INACTIVE và JWT cũ bị chặn cho cả bốn role.

## File thay đổi

- `src/main/java/com/stockflow/order/api/OrderController.java`
- `src/main/java/com/stockflow/order/dto/OrderListResponse.java` (mới)
- `src/main/java/com/stockflow/order/repository/OrderQueryRepository.java` (mới)
- `src/main/java/com/stockflow/order/service/OrderQueryService.java` (mới)
- `src/main/java/com/stockflow/auth/service/AuthService.java`
- `src/main/java/com/stockflow/auth/security/JwtAuthenticationFilter.java`
- `src/main/java/com/stockflow/user/domain/UserStatus.java`
- `src/test/java/com/stockflow/order/OrderQueryIntegrationTest.java` (mới)
- `src/test/java/com/stockflow/auth/api/AuthIntegrationTest.java`
- `README.md`
- `ANTIGRAVITY_HANDOFF.md`
- `docs/storefront-roadmap.md` (mới)

## Giới hạn và việc tiếp theo

Suite tự động vẫn dùng H2, chưa chạy trong GitHub Actions ở lượt này, chưa có Testcontainers. PostgreSQL được kiểm chứng cục bộ trên 13.2; chưa chạy lại Docker Compose/PostgreSQL 17 trong lượt này. Flyway hiện cảnh báo phiên bản H2 2.3 mới hơn mức hỗ trợ đã kiểm chứng; suite vẫn PASS, chưa đổi dependency ngoài phạm vi. Giao diện không sửa nên không có kiểm chứng trình duyệt mới. Không có public deployment. Luồng fulfillment, checkout/địa chỉ và thay đổi thời điểm DISPATCH còn chờ giai đoạn sau; không coi chúng là đã hoàn thành.

Sau giai đoạn 2, lượt tiếp theo nên hoàn thiện **catalog storefront và contract thông tin nhận hàng**. Giữ khách chọn kho/DISPATCH lúc payment; chốt địa chỉ/snapshot trước migration mới. Sau API mới tách storefront/dashboard. Không thêm chuyển kho, kiểm kê, cache hoặc refresh token vào lượt checkout.
