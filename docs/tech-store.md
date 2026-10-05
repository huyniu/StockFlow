<!-- Phạm vi StockFlow Tech đã được người dùng đồng ý; kiểm chứng và reset dữ liệu được ghi rõ riêng. -->
# StockFlow Tech — Cửa hàng phụ kiện máy tính và thiết bị công nghệ

## Mục tiêu đã chốt

StockFlow phục vụ **một cửa hàng sở hữu nhiều kho**, có storefront cho khách và dashboard cho ADMIN/MANAGER/nhân viên kho. Người dùng được chọn ngành hàng trong bài tập lớn và đã đồng ý chuyển sang phụ kiện máy tính, thiết bị công nghệ.

Tên hệ thống vẫn là StockFlow, tên cửa hàng là **StockFlow Tech**. Kho Hà Nội, Đà Nẵng và TP.HCM tiếp tục phục vụ đơn hàng; một đơn thuộc một kho do khách chọn.

## Catalog ban đầu

- **Bàn phím & Chuột** — slug `ban-phim-chuot`.
- **Tai nghe & Loa** — slug `tai-nghe-loa`.
- **Webcam & Micro** — slug `webcam-micro`.
- **Hub, Cáp & Bộ sạc** — slug `hub-cap-sac`.
- **Màn hình & Phụ kiện bàn làm việc** — slug `man-hinh-ban-lam-viec`.

Mỗi cấu hình được bán là một SKU riêng, có giá và tồn kho riêng. MVP chưa cần bảng biến thể, serial/IMEI hay quy trình bảo hành. Quản trị viên có thể bổ sung danh mục/sản phẩm qua API và dashboard hiện có.

## Cách khởi động

Profile `demo` mặc định chuẩn bị bốn tài khoản demo, ba kho, catalog mẫu và tồn đầu kỳ **20–50 sản phẩm/SKU tại mỗi kho demo đang hoạt động**. Tồn được nạp qua `InventoryService.stockIn()`, tạo ledger `GOODS_RECEIPT` với actor admin và balance trước/sau.

`DEMO_SEED_CATALOG=true` là mặc định: database mới có **24 sản phẩm TECH-**, **72 tồn kho**, **72 GOODS_RECEIPT**. Sản phẩm ACTIVE hiện có cũng được nạp ở ba kho demo nếu chưa có tồn đầu kỳ. Dòng inventory đã tồn tại nhưng tồn vật lý bằng 0 và chưa có movement được nạp một lần; hàng đã bán hết có ledger không tự được bổ sung khi restart. Đặt `DEMO_SEED_CATALOG=false` để giữ chế độ nhập tay. Giá, cấu hình và ảnh Unsplash là dữ liệu minh họa.

Startup runner nhận diện SKU/slug đã có và giữ nguyên giá, ảnh, tồn kho, đơn hàng, danh mục cũ và ledger. Đổi ngành hàng hay đổi flag không xóa dữ liệu đã nhập. Khi dùng database cũ, danh mục và sản phẩm cũ vẫn xuất hiện cho tới khi chủ cửa hàng chủ động xử lý/reset.

## Reset catalog cục bộ đã thực hiện

Ngày **01/10/2026**, người dùng yêu cầu xóa toàn bộ sản phẩm cũ để tự thêm. Đã reset database **`stockflow` tại `localhost:5432`**, đang phục vụ ứng dụng ở cổng 8080:

- Sao lưu bằng `pg_dump` trước khi xóa: `target/db-backups/stockflow-catalog-reset-20261001-111016/stockflow.dump` (45.048 byte). `pg_restore --list` đọc thành công và có dữ liệu của đủ 11 bảng được kiểm tra. Bản sao lưu chỉ nằm trên máy, trong thư mục build được Git bỏ qua.
- Xóa 24 sản phẩm, 72 dòng tồn kho, 72 movements và bốn danh mục demo cũ. Các bảng đơn hàng, mặt hàng trong đơn, thanh toán và vận đơn cũng được làm trống cùng transaction; trước reset các bảng này đã có 0 bản ghi.
- Sau reset có **năm danh mục công nghệ**, **0 sản phẩm**, **0 tồn kho**, **0 movements**, **0 đơn hàng**. Quản trị viên tự tạo sản phẩm/ảnh rồi nhập kho qua dashboard để bắt đầu dữ liệu mới.
- Giữ nguyên **5 tài khoản** (bốn tài khoản demo và tài khoản hệ thống), vai trò, **3 kho** và **1 phân công nhân viên kho Hà Nội**. Trigger bảo vệ sổ cái vẫn bật; không sửa migration hay lịch sử Flyway.
- Kiểm tra trực tiếp ứng dụng đang chạy: **21 yêu cầu API thành công**, gồm catalog trống, năm danh mục đúng tiếng Việt, đăng nhập cả bốn vai trò, ba lựa chọn kho, đơn hàng/tồn kho/sổ cái/low-stock trống. Khi xem tồn kho bằng STAFF, truy vấn chỉ định kho Hà Nội được phân công.

Đây là thao tác vận hành một lần theo yêu cầu rõ ràng, không phải migration hoặc logic xóa dữ liệu lúc khởi động. Giữ `DEMO_SEED_CATALOG=false` khi nhập sản phẩm thủ công để không nạp thêm fixture. Lần reset không khởi động lại phiên Spring Boot trong IntelliJ: database đang chạy vẫn giữ Flyway V1–V5; V6 ảnh sản phẩm sẽ được áp dụng khi chạy phiên bản source/JAR mới đã build.

## Phạm vi lượt thay đổi

- Đổi nhận diện storefront, tiêu đề, ô tìm kiếm, hero và footer sang StockFlow Tech.
- Thay minh họa túi mua sắm/điện thoại bằng góc làm việc có màn hình, bàn phím, chuột và tai nghe.
- Thêm ảnh dự phòng cho năm nhóm công nghệ; ảnh đã lưu qua ADMIN vẫn có ưu tiên cao nhất.
- Đổi danh mục bootstrap và fixture tùy chọn sang hàng công nghệ.
- Giữ schema V1–V6 và API hiện có, thứ tự khóa, atomic reserve, JWT/ownership, phạm vi kho, movement và fulfillment.
- Cập nhật Swagger, README, handoff, lộ trình và hướng dẫn ảnh.

Việc dọn dữ liệu bán hàng cũ là thao tác riêng có sao lưu và mục tiêu database/bảng rõ ràng. Không có migration hoặc runner tự xóa dữ liệu.

## Chức năng bài tập lớn còn lại

1. Thuộc tính cấu trúc và bộ lọc phù hợp; mô tả văn bản V7, trang chi tiết có URL `/san-pham/{id}` và bộ ảnh URL V8 đã có. Xem [Chi tiết sản phẩm](product-details.md) và [quản lý bộ ảnh](product-gallery.md); upload file còn chưa triển khai.
2. Thông tin nhận hàng tại checkout, lưu snapshot tên/số điện thoại/địa chỉ trong đơn.
3. Chỉnh sửa hồ sơ cá nhân.
4. Đánh giá sau mua: xác minh khách đã nhận hàng; điểm sao, bình luận và nhãn bán chạy lấy từ dữ liệu thật.
5. Tích hợp thanh toán sandbox theo yêu cầu nghiệm thu; hiện vẫn là mô phỏng.

Sao 4,9, 120+ đã bán và nhãn bán chạy trên card hiện là minh họa. Chúng chưa phải hệ thống đánh giá/thống kê public đã hoàn chỉnh. Giá gạch ngang/phần trăm giảm minh họa đã được gỡ; giá hiển thị lấy từ sản phẩm. Wishlist, voucher, chat, loyalty và gợi ý là mở rộng sau luồng chính.

## Kiểm chứng

Các lệnh trên Windows dùng Maven cache được yêu cầu:

```powershell
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' test
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' package
```

Test cập nhật kiểm tra năm danh mục/24 SKU, ảnh fixture, ledger nhập đầu kỳ, staff đúng kho và restart không ghi đè dữ liệu. Test mới xác nhận catalog, tồn và ledger cũ được giữ khi chưa reset có chủ đích.

Kết quả ngày **01/10/2026**:

- Test tập trung: **21 PASS** (demo, bootstrap nhập tay, giao diện public).
- Toàn suite **219 PASS**, 0 failures/errors/skipped; giữ 218 ca hiện có và thêm một ca bảo toàn dữ liệu cũ.
- `test` và `package` đều BUILD SUCCESS; tạo `target/stockflow-0.0.1-SNAPSHOT.jar`.
- PostgreSQL **13.2**, database QA mới: áp dụng V1–V6 thành công. Chế độ nhập tay có năm danh mục/ba kho, không sinh sản phẩm, tồn, movement hoặc đơn.
- Khi bật fixture công nghệ, Chrome headless có **72 kiểm tra PASS**: 49 hồi quy storefront/dashboard/fulfillment, 20 ảnh/form và 3 nhận diện/catalog/ảnh CDN.
- Không có JavaScript exception chưa xử lý hoặc API 5xx trong các luồng đã chạy. Desktop và mobile 390px không tràn ngang; tám card đầu tải ảnh CDN đã lưu thành công.
- Môi trường QA sử dụng cổng và profile Chrome riêng. Khi đổi ngành hàng, database chính được giữ nguyên; sau đó người dùng đã yêu cầu reset catalog và thao tác cục bộ đã hoàn tất như ghi ở trên.
- Docker Compose/PostgreSQL 17, GitHub Actions và các chức năng bài tập lớn còn lại chưa được kiểm chứng/triển khai trong lượt này.

## File thay đổi của lượt này

Danh sách này chỉ gồm thay đổi định hướng công nghệ, giữ riêng với các thay đổi giao diện/ảnh chưa commit trước đó:

- `src/main/java/com/stockflow/demo/DemoDataSeeder.java`
- `src/main/java/com/stockflow/common/config/OpenApiConfig.java`
- `src/main/resources/application-demo.yml`
- `src/main/resources/static/index.html`
- `src/main/resources/static/app.js`
- `src/main/resources/static/styles.css`
- `src/test/java/com/stockflow/demo/DemoDataIntegrationTest.java`
- `src/test/java/com/stockflow/demo/ManualCatalogSeedIntegrationTest.java`
- `src/test/java/com/stockflow/common/api/WebDemoIntegrationTest.java`
- `README.md`
- `ANTIGRAVITY_HANDOFF.md`
- `docs/storefront-roadmap.md`
- `docs/product-images.md`
- `docs/tech-store.md` (mới)
