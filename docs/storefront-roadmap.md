<!-- StockFlow Tech tập trung ngành hàng công nghệ; các giai đoạn và kết quả cũ được giữ như lịch sử. -->
# StockFlow Tech: cửa hàng công nghệ nhiều kho và lộ trình API

> Giai đoạn 1/2 đã nghiệm thu và commit. Giai đoạn 3 tách storefront/dashboard, thêm tìm kiếm và lựa chọn chi nhánh theo ngữ cảnh, giữ schema và nghiệp vụ đã có. Xem [kiểm chứng giai đoạn 3](storefront-dashboard-verification.md). Những đánh giá/test giai đoạn 1 dưới đây là lịch sử; các khoảng trống tại mốc đó không phải hiện trạng.

## Định vị hiện tại: cửa hàng điện thoại, laptop, âm thanh và phụ kiện

<!-- Cập nhật hiện trạng V17; các kết quả giai đoạn khởi đầu phía dưới được giữ làm lịch sử. -->
Trang chủ có các lối tắt danh mục đang tồn tại, bán chạy từ đơn đã thanh toán và hướng dẫn mua hàng. V17 bổ sung khóa chống tạo đơn trùng; CI thêm bộ kiểm chứng PostgreSQL thật. Không xóa danh mục/SKU đã được chủ cửa hàng nhập. Đánh giá xác thực mua hàng và nội dung liên hệ/bảo hành/đổi trả còn chờ quyết định nghiệp vụ. Xem [contract và kết quả kiểm chứng](storefront-quality-and-reliability.md).

### Phạm vi phụ kiện ban đầu (lịch sử)

Storefront mang tên **StockFlow Tech**, phục vụ một cửa hàng có nhiều kho. Năm nhóm đầu tiên là Bàn phím & Chuột, Tai nghe & Loa, Webcam & Micro, Hub/Cáp/Bộ sạc và Màn hình/Phụ kiện bàn làm việc. Mỗi cấu hình bán là một SKU riêng. Đổi định hướng không thay schema, không đổi giá/ảnh hay xóa lịch sử cũ trong startup runner. Xem [phạm vi công nghệ](tech-store.md).

Lượt này đổi nội dung giao diện, fixture và tài liệu. Chế độ nhập tay vẫn không nạp sản phẩm/tồn; fixture 24 sản phẩm công nghệ chỉ bật bằng DEMO_SEED_CATALOG=true. Mô tả/thuộc tính sản phẩm, checkout có người nhận, chỉnh sửa hồ sơ, đánh giá sau giao và thanh toán sandbox nằm ở các lượt tiếp theo.

Kiểm chứng lượt công nghệ: **219 test PASS**, test/package thành công và **72 kiểm tra Chrome PASS** trên PostgreSQL QA mới. Giữ schema V1–V6 và dữ liệu cũ cho tới khi chủ cửa hàng chọn phạm vi reset.

## Bổ sung sau giai đoạn 3: ảnh bìa và catalog nhập tay

- V6 bổ sung `products.image_url` tùy chọn, không sửa V1–V5. POST/PATCH chỉ ADMIN được lưu URL; bỏ qua/null PATCH giữ ảnh, chuỗi trống xóa ảnh.
- Form tạo/sửa có link và xem trước; storefront ưu tiên ảnh đã lưu, không cần chỉnh app.js khi thêm sản phẩm. HTTP/HTTPS và /assets/ được validate; không tải ảnh bên ngoài qua backend.
- Demo mặc định giữ tài khoản/kho/danh mục nhưng không tự tạo sản phẩm/tồn. `DEMO_SEED_CATALOG=true` vẫn bật fixture portfolio đầy đủ; đổi flag không tự xóa dữ liệu.
- Mô tả V7 và trang chi tiết `/san-pham/{id}` đã có; bấm thẻ mở trang, nút thêm giỏ vẫn độc lập. V8 thêm bộ ảnh có thứ tự qua form ADMIN, thumbnail và nút chuyển ảnh. Xem [hướng dẫn ảnh sản phẩm](product-images.md), [bộ ảnh](product-gallery.md) và [trang chi tiết](product-details.md). Upload file và thông tin người nhận còn trong lộ trình.
- Menu nhiều cột, hãng và giá lấy dữ liệu thật từ V9/V10. categoryId gồm nhóm con; brandId/minPrice/maxPrice/q/trạng thái lọc tại database trước phân trang. ADMIN chọn đường dẫn nhóm/hãng và tạo nhóm con. Giá tăng/giảm/mới nhất có ID làm thứ tự phụ; thông số laptop còn ở lượt sau. Xem [Danh mục và hãng](catalog-categories-and-brands.md).

## Hiện trạng sau giai đoạn 3

- Cửa hàng công khai/CUSTOMER: kệ ACTIVE dạng card, tìm tên/SKU tại database, danh mục/phân trang, chọn chi nhánh, giỏ nhiều sản phẩm và đặt/giữ hàng 15 phút.
- Khách theo dõi đơn của mình và shipment/tracking, thanh toán mô phỏng, hủy PENDING; giữ giỏ vãng lai qua đăng nhập checkout.
- Dashboard theo role có work queue theo kho, PACKED/SHIPPED/DELIVERED/RETURNED, tồn kho/nhập hàng, ledger, reports và catalog admin.
- Staff chọn kho theo phân công database, không thấy ledger/reports/admin. Quản lý không có form nhập hàng/catalog vì contract chỉ ADMIN/STAFF nhập, ADMIN quản trị catalog.
- Thêm read API `/storefront/branches`, `/warehouses/operating-options` và tham số `q`. Không sửa schema V1–V5, transaction/atomic reserve, DISPATCH lúc payment hoặc ledger.
- 194 test PASS (183 cũ + 11 mới), 49 mốc Chrome headless PASS trên PostgreSQL QA riêng; xem báo cáo giai đoạn 3.

## Mục tiêu sản phẩm

Một cửa hàng/doanh nghiệp bán phụ kiện máy tính và thiết bị công nghệ, sở hữu nhiều kho và bán trực tiếp cho khách. Bản hoàn thiện có storefront cho khách và dashboard cho admin/nhân viên; cùng dùng backend Java 17, Spring Boot 3.4.5, PostgreSQL và Flyway hiện tại. Không thêm seller, tenant hoặc marketplace.

Portfolio ưu tiên tính đúng đắn của database, transaction, quyền sở hữu/phạm vi kho và kiểm thử. Payment/shipping mô phỏng; giỏ ở frontend đủ cho MVP.

## Hiện trạng đã đọc ở giai đoạn 1 (lịch sử)

Đã đọc source Java, test, migration PostgreSQL/H2 V1–V5, frontend tĩnh, Maven/configuration/CI/Docker và các tài liệu trong repository, gồm ANTIGRAVITY_HANDOFF.md.

- **Auth/user:** register CUSTOMER, login BCrypt/JWT, users/me; role được nạp từ DB. Trước lượt này login/filter chưa chặn INACTIVE, trong khi OrderService đã kiểm tra trạng thái actor.
- **Catalog/warehouse:** category/product public reads, tìm tên/SKU, lọc danh mục/trạng thái/khoảng giá và sắp xếp trước phân trang; ADMIN tạo category/product/kho và sửa nội dung/bộ ảnh/giá/trạng thái sản phẩm. Menu storefront tự lấy danh mục đã lưu. Kho lựa chọn đặt hàng trả DTO tối thiểu, chỉ kho ACTIVE.
- **Inventory:** stock-in cho ADMIN/nhân viên kho được giao; đọc tồn theo phạm vi; physical = available + reserved. Ledger có actor, tham chiếu, balance trước/sau; PostgreSQL trigger và H2 Java trigger chặn UPDATE/DELETE.
- **Order:** nhiều sản phẩm trong một transaction, atomic conditional reserve và thứ tự inventory ID chống khóa chéo; snapshot giá, giữ 15 phút, my/detail, payment mô phỏng idempotent, cancel/refund/restock và expiry. Lifecycle khóa dòng order và kiểm tra lại trạng thái.
- **Report:** revenue theo ngày/tháng/kho, top products, low stock và order summary; MANAGER/ADMIN, SQL tổng hợp và phân trang. Có tài liệu benchmark PostgreSQL riêng.
- **Portfolio/demo:** Swagger JWT, demo 3 kho/24 sản phẩm/72 inventory và receipts, workflow CI test/package, dashboard HTML/CSS/JS cùng Spring Boot. Dashboard kết hợp cả bốn role để trình diễn, chưa là hai giao diện hoàn thiện.

Đây là hiện trạng source và các contract được test; không có nghĩa mọi tính năng mục tiêu đã được triển khai hoặc đã chạy trên GitHub/public deployment.

## Khoảng trống tại giai đoạn 1 (lịch sử)

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

Thông tin người nhận/địa chỉ checkout đã được chốt ngày 04/10/2026: bắt buộc tên, điện thoại, địa chỉ; ghi chú tùy chọn; miễn phí giao hàng và lưu cố định trên đơn. V15 triển khai contract này; xem [checkout](checkout-delivery.md). Các mốc giai đoạn 1–3 bên dưới được giữ làm lịch sử.

## Lộ trình còn lại và tiêu chí nghiệm thu

1. **Fulfillment API theo kho đã triển khai:** pack, ship/tracking, deliver, receive return/refund. Giữ row lock, kiểm tra transition, role/phạm vi kho, idempotency và rollback toàn bộ return. Có test concurrent pack/return, ship/cancel và trùng tracking giữa hai đơn; xem báo cáo giai đoạn 2.
2. **Storefront/dashboard đã triển khai:** giỏ, chọn chi nhánh, lịch sử/vận đơn; work queue/fulfillment, catalog, nhập kho, ledger và reports. Khách chọn một kho, DISPATCH lúc payment; không làm allocation/split order.
3. **Nhận hàng và catalog:** V15 đã có người nhận/điện thoại/địa chỉ/ghi chú cố định trên đơn, form checkout và kiểm thử; xem [Checkout](checkout-delivery.md). Ảnh bìa, gallery URL, mô tả văn bản và trang chi tiết sản phẩm cũng đã có; xem [Chi tiết sản phẩm](product-details.md) và [bộ ảnh](product-gallery.md). Upload file và lọc thông số chuyên biệt còn lại. Giữ transaction/atomic reserve/snapshot giá; không thêm carts chỉ để làm CRUD.
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

Tại giai đoạn 1, suite dùng H2, chưa có Testcontainers/GitHub Actions/public deployment kiểm chứng mới và chưa sửa giao diện. Đây là giới hạn lịch sử. Sau giai đoạn 3 đã kiểm chứng Chrome desktop/mobile với PostgreSQL 13.2; fulfillment và giao diện đã hoạt động. CI mới, Docker Compose/PostgreSQL 17, Testcontainers và contract địa chỉ vẫn chưa được xác minh/triển khai ở lượt này.

<!-- Kết quả hiện tại tách khỏi các mốc lịch sử để lần bàn giao sau không dùng tổng test cũ. -->
<!-- Mốc V12 thay phạm vi chỉ gộp màu bằng model có phiên bản/màu, giữ số liệu cũ làm lịch sử. -->
Các mốc ảnh bìa/trang riêng/bộ ảnh/menu giá có 218/260/295 test PASS. V9/V10 có 353 test/84 kiểm tra Chrome; V11 có 388 test/86 kiểm tra Chrome; V12 có 411 test/81 kiểm tra Chrome. Đây là lịch sử. Lượt hoàn thiện trang bán lẻ hiện có **424 test PASS**, package BUILD SUCCESS và **114 kiểm tra Chrome PASS** trên PostgreSQL QA 13.2. Một thẻ model chứa phiên bản và màu; chọn SKU cập nhật URL, ảnh nhỏ và breadcrumb đầy đủ. API công khai chỉ báo còn/hết hàng theo chi nhánh, không tiết lộ quantity hoặc ledger. Đã bỏ đánh giá/lượt bán và nhãn bán chạy ghi cứng. Xem [Trang sản phẩm bán lẻ](retail-product-detail.md), [Danh mục và hãng](catalog-categories-and-brands.md) và [Model, phiên bản, màu](product-models-versions-and-colors.md).

<!-- Checkout V15 nối tiếp các mốc lịch sử, không thay thời điểm DISPATCH hoặc quyền theo kho. -->
Checkout **đã triển khai V15**: DTO bắt buộc người nhận/điện thoại/địa chỉ, ghi chú tùy chọn, snapshot trên order, form giỏ và hiển thị ở chi tiết cho khách/nhân viên. Đơn cũ vẫn hỗ trợ `delivery=null`. Giữ miễn phí giao hàng, khách chọn kho, DISPATCH lúc payment và rollback reserve nhiều mặt hàng. Xem [contract, file thay đổi và kiểm chứng](checkout-delivery.md).

<!-- Hai cải thiện frontend đã triển khai ngày 04/10/2026, giữ nguyên API và schema hiện có. -->
Giỏ và chi nhánh đã được giữ qua tải lại trong phiên tab, theo đúng danh tính được backend xác thực; giá/cấu hình đọc lại từ API. Màn hình đơn khách đã tự cập nhật trạng thái và vận đơn, dừng khi tab ẩn/rời màn hình, có retry/backoff và chống response cũ ghi đè thao tác tay. Xem [quy tắc, giới hạn và kiểm chứng](cart-and-order-sync.md).

<!-- Hồ sơ V16 nối tiếp giỏ/đồng bộ đơn; không mở rộng sang sổ địa chỉ hoặc đổi credential. -->
Hồ sơ khách hàng **đã triển khai V16**: trang `/#shop/account`, GET/PATCH `/users/me`, tên và điện thoại tùy chọn; thông tin định danh/quyền không nhận từ form. Checkout chỉ gợi ý vào ô trống, không thay người nhận nhập tay hoặc bản chụp của đơn cũ. Xem [contract và kiểm chứng hồ sơ](customer-profile.md).

Lượt tiếp theo đề xuất chống tạo đơn trùng bằng khóa idempotency phía server. Đánh giá sau mua, thanh toán sandbox và Testcontainers PostgreSQL là các lượt riêng. Không coi việc khóa nút đặt hàng là đã hoàn thành idempotency tạo đơn. Đổi mật khẩu/email, sổ địa chỉ, upload file, giỏ lâu dài/đa thiết bị, merge SKU cũ và lọc RAM/chip/màn hình chuyên biệt vẫn là mở rộng sau.
