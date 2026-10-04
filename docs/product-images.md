<!-- Ảnh bìa cho StockFlow Tech và catalog nhập tay; dọn dữ liệu phải xác định đúng database. -->
# Ảnh sản phẩm và cửa hàng nhập catalog thủ công

## Thao tác quản trị

1. Đăng nhập ADMIN, mở **Danh mục & sản phẩm**.
2. Khi thêm sản phẩm, nhập SKU, tên, danh mục, giá, trạng thái và **Đường dẫn ảnh bìa** tùy chọn.
3. Dán URL HTTP/HTTPS hoặc đường dẫn ảnh đã đặt trong `src/main/resources/static/assets/`, ví dụ `/assets/products/keyboard.jpg`.
4. Kiểm tra ảnh xem trước rồi lưu. Storefront tự lấy `image_url` từ API, không cần sửa bảng ảnh trong app.js.
5. Khi cập nhật, chọn sản phẩm để xem ảnh hiện tại. Nhập link mới để thay ảnh, để trống để giữ nguyên, hoặc tích **Xóa ảnh bìa đã lưu**.
6. Sản phẩm mới chưa có hàng: vào **Tồn kho & nhập hàng**, chọn kho/sản phẩm và nhập số lượng trước khi đặt mua.

Form có thêm **Ảnh bổ sung (tối đa 8 ảnh)**, mỗi dòng một link. Khách chọn ảnh nhỏ hoặc dùng nút trước/sau trên trang sản phẩm. Xem [hướng dẫn bộ ảnh V8](product-gallery.md) để đổi thứ tự và xóa ảnh.

Không tải được ảnh từ URL thì preview cảnh báo; ảnh lớn có thể dùng ảnh minh họa với nhãn rõ ràng. Card ưu tiên ảnh bìa, rồi ảnh bổ sung đầu tiên, sau đó mới tới ảnh danh mục. Backend không kiểm tra nội dung ảnh bằng HTTP và không tải file từ xa. Chưa có upload file.

## Contract API

Migration PostgreSQL/H2 `V6__add_product_image_url.sql` bổ sung cột `image_url VARCHAR(2048)` cho products, nullable. Các migration đã áp dụng giữ nguyên.

- `POST /api/v1/products` nhận `image_url` tùy chọn; bỏ qua/null/trống lưu null.
- `PATCH /api/v1/products/{id}`: bỏ qua/null giữ ảnh cũ; URL thay ảnh; chuỗi trống xóa ảnh.
- `GET /api/v1/products` và `GET /api/v1/products/{id}` trả `image_url`.
- Chỉ ADMIN tạo/sửa catalog. Các role khác vẫn bị 403; đọc catalog tiếp tục công khai.
- Link tối đa 2048 ký tự, URL HTTP/HTTPS có host và không chứa credentials, hoặc đường dẫn /assets/ không traversal.
- Các scheme javascript/data/file, đường dẫn vượt assets và URL sai cấu trúc trả 400.

Ví dụ cập nhật riêng ảnh:

```json
{
  "image_url": "/assets/products/keyboard.jpg"
}
```

Ví dụ xóa ảnh:

```json
{
  "image_url": ""
}
```

## Bootstrap catalog

Profile `demo` mặc định chuẩn bị bốn tài khoản demo, ba kho, năm danh mục công nghệ và phân công staff Hà Nội; catalog/tồn được chủ cửa hàng nhập thủ công. Tài khoản hệ thống của migration phục vụ scheduler vẫn giữ nguyên. Catalog mới của [StockFlow Tech](tech-store.md) dùng SKU TECH- khi bật fixture.

Đặt `DEMO_SEED_CATALOG=true` trong environment của IntelliJ hoặc `.env` Docker Compose nếu cần fixture 24 sản phẩm, 72 tồn và 72 GOODS_RECEIPT. Trong suite fixture, property này được bật rõ ràng; suite nhập tay kiểm tra catalog trống không bị nạp lại.

Chuyển flag sang false không xóa dữ liệu cũ. Không xóa catalog bằng migration hoặc startup runner: việc reset thủ công phải có phạm vi database/bảng rõ ràng, có sao lưu và giữ tài khoản/kho cần thiết.

## Kiểm chứng

- `ProductImageIntegrationTest` kiểm tra lưu/đọc ảnh, tương thích request cũ, PATCH giữ/thay/xóa, URL sai/quá dài và quyền ADMIN.
- `ManualCatalogSeedIntegrationTest` kiểm tra tài khoản/kho sẵn sàng, restart không nạp sản phẩm/tồn mẫu và giữ sản phẩm/ảnh đã nhập.
- Chạy Maven với `-Dmaven.repo.local=C:/Users/Admin/.m2/repository` trên máy Windows này.

```powershell
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' test
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' package
```

Kiểm chứng ngày **01/10/2026**:

- Toàn suite **218 PASS**, 0 failures/errors/skipped; 194 test cũ và 24 test mới (21 ảnh, 3 bootstrap nhập tay).
- `test` và `package` đều BUILD SUCCESS; JAR chứa đúng static resources và migration V6 từ source hiện tại.
- PostgreSQL cục bộ **13.2**: V6 đã áp dụng thành công trên database QA riêng. Không thay schema của database đang được ứng dụng cổng 8080 sử dụng trong lượt kiểm chứng này.
- Chrome headless: **49** kiểm tra hồi quy storefront/dashboard và **20** kiểm tra form/ảnh, tổng **69 PASS**, 0 JavaScript exception và 0 API 5xx.
- Kiểm tra desktop/mobile 390px, lưu ảnh qua form vào PostgreSQL, reload, sửa riêng ảnh/giá, xóa ảnh, URL sai, ảnh 404, chọn sản phẩm khác và đổi tài khoản.
- QA dùng database/cổng/profile trình duyệt riêng. Chưa kiểm chứng Docker Compose, PostgreSQL 17 hoặc GitHub Actions trong lượt này. Dữ liệu cũ chưa bị xóa khi chưa chốt phạm vi reset.

## File của lượt thay đổi này

Danh sách dưới đây chỉ gồm thay đổi ảnh/catalog nhập tay; không gộp các file còn chưa commit của giao diện giai đoạn trước.

- `src/main/java/com/stockflow/catalog/api/ProductController.java`
- `src/main/java/com/stockflow/catalog/domain/Product.java`
- `src/main/java/com/stockflow/catalog/dto/CreateProductRequest.java`
- `src/main/java/com/stockflow/catalog/dto/UpdateProductRequest.java`
- `src/main/java/com/stockflow/catalog/dto/ProductResponse.java`
- `src/main/java/com/stockflow/catalog/service/ProductService.java`
- `src/main/java/com/stockflow/catalog/validation/ImageUrl.java` (mới)
- `src/main/java/com/stockflow/catalog/validation/ImageUrlValidator.java` (mới)
- `src/main/resources/db/migration/V6__add_product_image_url.sql` (mới)
- `src/test/resources/db/migration/V6__add_product_image_url.sql` (mới)
- `src/main/resources/static/index.html`
- `src/main/resources/static/app.js`
- `src/main/resources/static/styles.css`
- `src/main/java/com/stockflow/demo/DemoDataSeeder.java`
- `src/main/resources/application-demo.yml`
- `src/test/java/com/stockflow/demo/DemoDataIntegrationTest.java`
- `src/test/java/com/stockflow/demo/ManualCatalogSeedIntegrationTest.java` (mới)
- `src/test/java/com/stockflow/catalog/api/ProductImageIntegrationTest.java` (mới)
- `compose.yaml`
- `.env.example`
- `README.md`
- `ANTIGRAVITY_HANDOFF.md`
- `docs/storefront-roadmap.md`
- `docs/product-images.md` (mới)
