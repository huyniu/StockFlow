<!-- Nghiệm thu giai đoạn 3: ghi đúng source, lệnh chạy, kết quả và giới hạn đã xác minh. -->
# StockFlow — Storefront & Dashboard

Ngày kiểm chứng: **01/10/2026**.

## Phạm vi đã hoàn thành

Ứng dụng vẫn chạy một Spring Boot tại **http://localhost:8080/**, dùng HTML5/CSS3/JavaScript thuần
trong `src/main/resources/static/`. Không cần Node.js/npm, CDN hoặc frontend server để chạy sản phẩm.

- **Cửa hàng:** StockFlow Shop với tìm kiếm tên/SKU, chi nhánh phục vụ, kệ sản phẩm ACTIVE dạng card,
  lọc danh mục/phân trang, giá VND và drawer giỏ nhiều sản phẩm có tăng/giảm/xóa số lượng.
- **Checkout:** khách chọn một chi nhánh, POST tạo đơn giữ hàng 15 phút. Khách vãng lai đăng nhập
  vẫn giữ giỏ/chi nhánh để kiểm tra lại rồi tự bấm đặt hàng; không tự tạo đơn ngay sau login.
- **Đơn của khách:** lịch sử riêng, mặt hàng/giá snapshot, timeline, hạn giữ hàng, vận đơn và thời điểm
  giao hàng. Thanh toán mô phỏng cho PENDING; khách chỉ tự hủy PENDING theo contract hiện có.
- **Cổng vận hành:** sidebar riêng, work queue theo kho/trạng thái có phân trang tại backend,
  tra ID/chi tiết, các bước pack/ship/deliver/receive-return và mã vận đơn tùy chọn.
  Hủy/nhận trả có hộp xác nhận ghi rõ tác động trước khi gửi POST.
- **Tồn kho:** ba số khả dụng/đang giữ/thực tế, lọc kho/sản phẩm và nhập hàng cho ADMIN/STAFF.
  Những thẻ tổng số lượng chỉ cộng trang đang xem, có nhãn phạm vi.
- **Kiểm toán/báo cáo:** ledger chỉ đọc với tồn trước → sau, tổng hợp trạng thái đơn,
  doanh thu ngày/tháng/kho, top products và low stock cho MANAGER/ADMIN.
- **Catalog admin:** bảng lọc cả ACTIVE/INACTIVE, tạo danh mục/sản phẩm và PATCH tên/giá/trạng thái.
- **Demo:** bốn nút login JWT thật tự đổi ngữ cảnh; giữ login/register thủ công.
  Khách không thấy form/menu nội bộ, staff không thấy ledger/reports/admin.
- **Phản hồi:** mã/thông điệp HTTP thật qua banner/toast, cả lỗi 409 ngay trong drawer native.
  Logout/đổi actor xóa dữ liệu riêng và hủy request cũ; response chậm được kiểm tra epoch.
  Token chỉ ở sessionStorage, không lưu mật khẩu; reload xác minh actor bằng users/me.
- **Bố cục:** desktop/mobile, navigation theo hash, focus bàn phím, dialog native và reduced motion.
  SVG nội bộ minh họa cửa hàng/danh mục; không giả ảnh thật của các sản phẩm chưa có ảnh.

## Những thay đổi backend cần thiết

1. `GET /api/v1/storefront/branches` công khai chỉ `id`/`code`/`name` của kho ACTIVE.
   Khách vãng lai chọn được chi nhánh, không nhận địa chỉ hay số tồn nội bộ.
2. `GET /api/v1/warehouses/operating-options` yêu cầu ADMIN/MANAGER/WAREHOUSE_STAFF.
   Staff chỉ nhận kho được phân công hiện tại; management nhận mọi kho. Giữ cả kho INACTIVE
   để xử lý các đơn đã tạo. CUSTOMER nhận 403, anonymous nhận 401.
3. `GET /api/v1/products?q=...` tìm tên/SKU không phân biệt hoa/thường tại database,
   kết hợp category/status trước phân trang. Escape `%`, `_`, `\` cho LIKE theo nghĩa đen.
   Storefront luôn gửi `status=ACTIVE`; contract đọc catalog tổng quát hiện có được giữ.

API `/warehouses` và `/warehouses/order-options` vẫn giữ quyền cũ.
Không sửa migration/schema V1–V5, datasource, các service order/inventory, atomic reserve,
khóa dòng order hoặc database trigger ledger. DISPATCH vẫn tại CONFIRMED; fulfillment không xuất lần hai.

## Test và JAR cuối cùng

Đã chạy đúng Maven cache của máy Windows:

```powershell
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' test
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' package
```

- **test:** 194 tests, 0 failures, 0 errors, 0 skipped; **BUILD SUCCESS**.
- **package:** chạy lại toàn bộ 194 tests với cùng kết quả; **BUILD SUCCESS**.
- Giữ đủ **183 ca cũ**, gồm các test transaction/concurrency/fulfillment.
- Thêm **11 ca** trong StorefrontApiIntegrationTest: DTO chi nhánh ACTIVE tối thiểu,
  JWT ở endpoint cũ/mới, manager/admin mọi kho, staff đúng phân công và thu hồi với token cũ,
  staff không phân công, CUSTOMER bị chặn, tìm tên/SKU/phân trang/filter và escape wildcard.
- WebDemoIntegrationTest vẫn giữ 9 lượt HTTP kiểm chứng welcome/index/assets public và quyền API;
  bổ sung khẳng định storefront mặc định cùng dashboard bị ẩn trong HTML ban đầu.
- `node --check` xác nhận cú pháp JavaScript; HTML/CSS/JS được định dạng với dòng/thụt lề rõ ràng.
- JAR: `target/stockflow-0.0.1-SNAPSHOT.jar`.

Log cục bộ: `target/storefront-phase3-test.log`, `target/storefront-phase3-package.log`,
và `target/storefront-phase3-targeted.log` (20 ca tập trung PASS).

## Kiểm chứng Chrome và PostgreSQL

Công cụ computer-use không khởi động được runtime. Thay thế bằng **Chrome headless thật**,
profile QA riêng trong target, viewport **1440 × 1000** và **390 × 844**.
Chạy JAR trên cổng **8096**, PostgreSQL **13.2**, database QA
`stockflow_fulfillment_verify_20260930_2220` đã tách riêng từ lượt trước.
Không dùng database ứng dụng StockFlow hay instance cổng 8080 cho các thao tác kiểm thử.

JavaScript production được Chrome thực thi, các nút/form chạy handler thật và gọi API thật.
Đối chiếu thêm inventory/movement qua API backend sau các thao tác.

**49 mốc PASS, 107 response API:** 98 × 200, 6 × 201, 1 × 401, 1 × 403, 1 × 409.
Không có response 5xx hoặc JavaScript exception chưa xử lý. Các tình huống:

1. Guest chỉ tải dữ liệu công khai; không gọi inventory/reports/orders/warehouse nội bộ.
2. Tìm kiếm SKU toàn catalog, chip/select danh mục đồng bộ và phân trang đúng metadata.
3. Giỏ hai mặt hàng, tăng số lượng, đồng bộ chi nhánh header/drawer và giữ giỏ qua login checkout.
4. Tạo đơn giữ đúng 15 phút; giá snapshot; payment xuất đúng tồn của hai mặt hàng.
5. Staff tự vào work queue chỉ kho Hà Nội, không có menu ledger/reports/admin.
6. CONFIRMED → PACKED → SHIPPED → DELIVERED → RETURNED; tracking tùy chọn hiện đúng.
   Pack/ship không trừ lần hai; return hoàn đủ ba số tồn của hai mặt hàng,
   có đúng hai RETURN_RESTOCK với snapshot trước/sau chuẩn.
7. Nhập hàng từ form staff cộng đúng tồn; sửa dropdown sang kho ngoài phân công vẫn bị 403 backend.
8. Manager đọc dữ liệu report/ledger thật; đổi về khách xóa các bảng/dữ liệu riêng.
9. Khách xem tracking/RETURNED, vượt tồn nhận 409 trong drawer và giữ giỏ; hủy PENDING nhả reserved.
10. Admin tạo/cập nhật giá decimal chính xác, tên chứa HTML được escape, INACTIVE không lên kệ khách,
    thêm danh mục và lọc work queue theo trạng thái.
11. Response tồn kho được trì hoãn có chủ đích không ghi dữ liệu admin sang phiên khách.
12. Mobile không tràn màn hình; chi nhánh có hàng riêng, đủ bốn nút demo và drawer nằm trong viewport.
    Ảnh desktop/mobile đã được xem để đối chiếu bố cục; banner dashboard nằm cạnh sidebar.
13. Reload xác minh users/me; JWT lỗi/role giả trong storage không mở dashboard và vẫn tải shop.
    Register với mật khẩu 6 ký tự theo DTO, lịch sử khách mới rỗng; logout xóa token và dữ liệu riêng.

Kết quả/harness/ảnh QA ở `target/web-qa/storefront-phase3-browser.mjs`,
`target/storefront-phase3-browser-result.json`, `target/storefront-phase3-browser.log`
và `target/storefront-phase3-*.png`, được Git bỏ qua.
Node/ws và formatter chỉ là công cụ QA cục bộ; không thêm vào dependency hoặc build của ứng dụng.

JVM 8096 và Chrome profile QA đã dừng. Database QA vẫn được giữ vì bộ duyệt tự động chặn DROP DATABASE
khi chưa có xác nhận riêng cho thao tác xóa vĩnh viễn; câu hỏi xác nhận đã gửi, chưa có phản hồi.

## Danh sách file thay đổi

**Giao diện (3):**

- `src/main/resources/static/index.html`
- `src/main/resources/static/app.js`
- `src/main/resources/static/styles.css`

**Backend (7):**

- `src/main/java/com/stockflow/catalog/api/ProductController.java`
- `src/main/java/com/stockflow/catalog/service/ProductService.java`
- `src/main/java/com/stockflow/common/config/SecurityConfig.java`
- `src/main/java/com/stockflow/warehouse/api/StorefrontController.java` — mới
- `src/main/java/com/stockflow/warehouse/api/WarehouseController.java`
- `src/main/java/com/stockflow/warehouse/repository/WarehouseRepository.java`
- `src/main/java/com/stockflow/warehouse/service/WarehouseService.java`

**Kiểm thử (2):**

- `src/test/java/com/stockflow/common/api/WebDemoIntegrationTest.java`
- `src/test/java/com/stockflow/warehouse/StorefrontApiIntegrationTest.java` — mới

**Tài liệu (4):**

- `README.md`
- `ANTIGRAVITY_HANDOFF.md`
- `docs/storefront-roadmap.md`
- `docs/storefront-dashboard-verification.md` — mới

Tổng **16 file** trong phạm vi source/test/tài liệu; công cụ/log/ảnh QA trong target không tính là source thay đổi.
Mọi file mới/sửa giữ comment/JavaDoc tiếng Việt có dấu, UTF-8. SQL mới dùng Java Text Block nhiều dòng.

## Giới hạn và công việc tiếp theo

- Suite Maven/CI vẫn dùng H2, chưa có Testcontainers PostgreSQL; PostgreSQL nêu trên là QA cục bộ bổ sung.
- Chưa chạy GitHub Actions, Docker Compose/PostgreSQL 17, Firefox/Safari hoặc điện thoại vật lý ở lượt này.
  Viewport mobile là Chrome emulation, chưa có public deployment.
- Giỏ ở bộ nhớ, reload làm trống; token logout phía client chưa phải cơ chế thu hồi JWT phía server.
- Storefront lọc ACTIVE; API catalog tổng quát cũ vẫn cho đọc/lọc trạng thái và chi tiết theo ID.
  Nếu đổi chính sách public visibility cần chốt contract rồi thêm test backend.
- Chưa có ảnh/mô tả sản phẩm, snapshot người nhận/địa chỉ, partial returns hoặc customer return-request.
- Lượt tiếp theo nên chốt tên/số điện thoại/địa chỉ nhận hàng rồi thêm snapshot vào order bằng migration mới,
  DTO validation và test transaction/ownership. Sau đó bổ sung ảnh/mô tả và trang chi tiết sản phẩm.
  Giữ quy tắc một đơn/một kho, chọn chi nhánh và DISPATCH lúc payment.
