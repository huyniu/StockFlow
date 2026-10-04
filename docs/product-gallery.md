<!-- Bộ ảnh chỉ lưu URL do Admin nhập; tài liệu ghi rõ contract, migration và kiểm chứng thực tế. -->
# Thư viện ảnh sản phẩm

Trang `/san-pham/{id}` hiển thị **ảnh lớn, ảnh nhỏ phía dưới, nút trước/sau và số thứ tự ảnh**. Khách bấm thumbnail để đổi ảnh lớn; khi focus trong thư viện có thể dùng phím ← / →. Giao diện hoạt động trên máy tính và điện thoại.

Mỗi sản phẩm có ảnh bìa hiện tại và tối đa **8 ảnh bổ sung**, tức tối đa 9 ảnh để xem. Chỉ hiển thị các URL đã lưu; hệ thống không tự tìm góc chụp trên website khác. Nếu mới có một ảnh thì không tạo thumbnail hoặc nút chuyển giả.

## Thêm ảnh trực tiếp trên web

1. Dừng rồi chạy lại Spring Boot để áp dụng Flyway V8 và API mới; mở `http://localhost:8080/`, nhấn **Ctrl + F5**.
2. Đăng nhập ADMIN → **Danh mục & sản phẩm → Sản phẩm**.
3. Bấm **Thêm sản phẩm** hoặc **Sửa** ở sản phẩm đang có.
4. **Đường dẫn ảnh bìa** là ảnh chính. Trong **Ảnh bổ sung (tối đa 8 ảnh)**, dán mỗi dòng một link ảnh góc trước, góc sau hoặc chi tiết của **cùng sản phẩm**.
5. Xem preview rồi lưu. Đổi thứ tự dòng để đổi thứ tự ảnh. Xóa một dòng để bỏ ảnh đó; xóa toàn bộ nội dung để bỏ các ảnh bổ sung.
6. Mở thẻ sản phẩm ở cửa hàng để xem bộ ảnh. Không cần chỉnh `app.js` mỗi lần thêm ảnh.

Ví dụ các ảnh đã được đặt trong `src/main/resources/static/assets/products/`:

```text
/assets/products/iphone-front.jpg
/assets/products/iphone-back.jpg
/assets/products/iphone-camera.jpg
```

Đây là ví dụ đường dẫn, không phải file ảnh được tạo sẵn. Có thể dùng URL HTTP/HTTPS trực tiếp đến ảnh. Copy link tới **file ảnh**, tránh link tới cả trang sản phẩm. File upload chưa được triển khai.

Link lỗi hoặc website nguồn chặn ảnh có cảnh báo tại preview/thumbnail. Ảnh lớn có thể chuyển sang ảnh minh họa và ghi nhãn rõ ràng. Backend chỉ validate/lưu URL, không tải ảnh từ xa hoặc xác nhận quyền sử dụng ảnh.

## Contract API

Ảnh bìa vẫn là `image_url`. Trường mới `image_urls` chứa **ảnh bổ sung**, không bắt buộc lặp lại ảnh bìa:

```json
{
    "image_url": "/assets/products/iphone-front.jpg",
    "image_urls": [
        "/assets/products/iphone-back.jpg",
        "/assets/products/iphone-camera.jpg"
    ]
}
```

- `POST /api/v1/products`: bỏ qua hoặc gửi `null` cho `image_urls` thì tạo bộ ảnh bổ sung rỗng.
- `PATCH /api/v1/products/{id}`: bỏ qua hoặc gửi `null` thì giữ bộ ảnh đang có; gửi danh sách thì thay toàn bộ theo thứ tự.
- Gửi `"image_urls": []` thì xóa ảnh bổ sung, giữ ảnh bìa, mô tả, SKU và dữ liệu nghiệp vụ khác.
- `GET /api/v1/products` và `GET /api/v1/products/{id}` trả `image_urls`, kể cả mảng rỗng cho sản phẩm cũ.
- URL được bỏ khoảng trắng bao quanh; tối đa 2.048 ký tự mỗi link, tối đa 8 link. Chặn phần tử null/trống, link sai cấu trúc và link trùng nhau sau khi chuẩn hóa.
- Chấp nhận HTTP/HTTPS có host, không credentials, hoặc đường dẫn `/assets/` an toàn. Chặn javascript/data/file, đường dẫn vượt assets và URL không hợp lệ.
- Chỉ ADMIN được tạo/sửa catalog; CUSTOMER, STAFF, MANAGER vẫn bị `403` khi cố ghi. Đọc catalog giữ quyền công khai.

Frontend bỏ ảnh bổ sung trùng với ảnh bìa khi trình bày. Khi không có ảnh bìa, ảnh bổ sung đầu tiên được dùng trên kệ và làm ảnh đầu tiên ở trang chi tiết. Chỉ khi không có ảnh đã lưu mới dùng một ảnh minh họa dự phòng.

## Schema và transaction

Flyway `V8__create_product_images.sql` thêm bảng `product_images` trên PostgreSQL/H2:

```text
product_id BIGINT NOT NULL REFERENCES products(id)
position INTEGER NOT NULL
image_url VARCHAR(2048) NOT NULL
PRIMARY KEY (product_id, position)
CHECK (position >= 0 AND position < 8)
CHECK (CHAR_LENGTH(TRIM(image_url)) > 0)
```

`@ElementCollection` và `@OrderColumn` giữ thứ tự. Bộ ảnh được thay trong transaction PATCH có khóa dòng sản phẩm để các lượt sửa đồng thời không xen lẫn dòng ảnh. Đọc collection theo lô `@BatchSize`, giữ phân trang tại database; không fetch join collection vào truy vấn phân trang.

Không sửa V1–V7, không backfill ảnh minh họa thành ảnh thật, không đổi SKU/giá/tồn kho/đơn/ledger. Sản phẩm đang có được đọc với `image_urls: []` cho đến khi Admin nhập ảnh bổ sung. Giỏ, JWT, thanh toán, atomic reserve và fulfillment giữ luồng hiện có.

## Kiểm chứng ngày 02/10/2026

- **26 ca tích hợp mới** trong `ProductGalleryIntegrationTest`: tạo/đọc/lưu thứ tự thật, reorder/thay/xóa, PATCH thiếu/null, request cũ, giới hạn 8 ảnh, link trùng/sai/quá dài, phần tử null/trống, kiểu JSON sai, quyền và phân trang.
- Toàn suite **260 test PASS**, 0 failures/errors/skipped. Suite tự động chạy H2 như hiện tại.
- Build JAR **BUILD SUCCESS** sau full test; JAR chứa migration V8 và static resources mới.
- **36 kiểm tra Chrome PASS** cho form/preview, chuyển ảnh, phím mũi tên, mobile, reload, thứ tự/xóa, không có ảnh bìa, ảnh lỗi, ảnh trùng bìa, xóa bản nháp khi đổi actor và không gọi API nội bộ từ khách.
- **42 kiểm tra Chrome hồi quy PASS**, gồm thẻ/link trang riêng, giỏ, Back/Forward, checkout/giữ hàng/thanh toán, STAFF pack/ship/deliver/return, tồn kho hoàn đúng và ledger không trùng.
- Tổng **78 kiểm tra trình duyệt**, 145 phản hồi API giao diện, 0 exception JavaScript. Có một `404` chủ động của bài hồi quy ID không tồn tại; các API khác thành công. Ảnh 404 chủ động có cảnh báo đúng.
- PostgreSQL QA **13.2**: chạy JAR V7 rồi nâng cấp V8 trên cùng database. Snapshot giữ nguyên **24 sản phẩm, 72 tồn kho, 72 movements**, kể cả ảnh bìa, mô tả, giá, SKU và thời điểm tồn kho; ảnh bổ sung khởi tạo rỗng.
- QA dùng database/cổng/profile Chrome riêng. Không thêm ảnh kiểm thử vào database `stockflow` của người dùng. Đã xem layout máy tính và điện thoại; không tràn ngang.
- Chưa kiểm chứng Docker Compose/PostgreSQL 17, GitHub CI hoặc mọi CDN ảnh bên ngoài. Chưa có Testcontainers, upload file, video hoặc phóng to toàn màn hình.

Lệnh đã chạy:

```powershell
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' '-Dtest=ProductGalleryIntegrationTest' test
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' test
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' '-DskipTests' package
```

Lệnh package bỏ lần chạy test lặp lại vì suite trước đó đã PASS. Có thể chạy `package` không kèm `-DskipTests` nếu muốn kiểm thử lại cùng build.

Log và script kiểm chứng nằm trong `target/` được Git bỏ qua: `product-gallery-test.log`, `product-gallery-package.log`, `web-qa/product-gallery-browser-result.json`, `web-qa/product-gallery-regression-result.json` và snapshot nâng cấp.

## 22 file thay đổi trong lượt bộ ảnh

Danh sách chỉ tính từ trạng thái trước lượt này, không gộp các thay đổi chưa commit của tính năng cũ.

Backend:

- `src/main/java/com/stockflow/catalog/domain/Product.java`
- `src/main/java/com/stockflow/catalog/dto/CreateProductRequest.java`
- `src/main/java/com/stockflow/catalog/dto/UpdateProductRequest.java`
- `src/main/java/com/stockflow/catalog/dto/ProductResponse.java`
- `src/main/java/com/stockflow/catalog/service/ProductService.java`
- `src/main/java/com/stockflow/catalog/repository/ProductRepository.java`
- `src/main/java/com/stockflow/catalog/api/ProductController.java`
- `src/main/java/com/stockflow/catalog/validation/ImageUrl.java`

Giao diện:

- `src/main/resources/static/index.html`
- `src/main/resources/static/app.js`
- `src/main/resources/static/styles.css`

Migration và test mới:

- `src/main/resources/db/migration/V8__create_product_images.sql`
- `src/test/resources/db/migration/V8__create_product_images.sql`
- `src/test/java/com/stockflow/catalog/api/ProductGalleryIntegrationTest.java`

Tài liệu:

- `README.md`
- `ANTIGRAVITY_HANDOFF.md`
- `HUONG_PHAT_TRIEN.md`
- `docs/product-details.md`
- `docs/product-images.md`
- `docs/storefront-roadmap.md`
- `docs/tech-store.md`
- `docs/product-gallery.md` (mới)
