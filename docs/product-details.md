<!-- Nội dung chi tiết lấy từ catalog đã lưu; tài liệu phân biệt mô tả tự do và thuộc tính chưa triển khai. -->
# Chi tiết sản phẩm và mô tả

Khách bấm thẻ sản phẩm ở cửa hàng để chuyển sang trang riêng tại **`/san-pham/{id}`**, chẳng hạn `http://localhost:8080/san-pham/1`. Bấm ảnh, tên, giá hoặc khoảng trống trên thẻ đều mở trang; storefront không còn nút **Xem chi tiết** hoặc popup chi tiết. Nút **Thêm vào giỏ** trên thẻ vẫn là thao tác riêng để thêm nhanh.

Trang hiển thị ảnh lớn, bộ ảnh nhỏ để chọn góc chụp, tên, SKU, danh mục, giá bán và mô tả. Admin nhập ảnh bìa và tối đa 8 ảnh bổ sung, mỗi dòng một URL; xem [quản lý thư viện ảnh V8](product-gallery.md). Khách chọn chi nhánh/số lượng rồi thêm vào giỏ hiện có. Việc mở chi tiết, đổi ảnh hoặc thêm giỏ chưa tạo đơn hay giữ tồn kho.

Liên kết hỗ trợ mở tab mới và sao chép để chia sẻ. Mở trực tiếp hoặc tải lại URL được Spring Boot forward tới giao diện tĩnh; JavaScript đọc ID rồi gọi API catalog công khai. Điều hướng nội bộ dùng History API để giữ giỏ và hỗ trợ nút Quay lại/Tiến. Giỏ vẫn ở bộ nhớ theo thiết kế MVP: reload hoặc mở tab mới tạo phiên giỏ riêng, chưa lưu dài hạn.

## Nội dung do quản trị nhập

Form thêm/sửa sản phẩm có **Mô tả / Chi tiết sản phẩm** tối đa 5.000 ký tự. Có thể ghi thông tin giới thiệu, thông số, màu sắc, phụ kiện và bảo hành, mỗi dòng một ý. Đây là văn bản tự do do Admin cung cấp; hệ thống không tự sinh thông số, giá cũ, khuyến mãi hoặc đánh giá thật.

Sản phẩm cũ chưa có mô tả vẫn được đọc/đặt hàng theo API hiện có. Trang ghi rõ khi cửa hàng chưa bổ sung mô tả. Chỉ ADMIN được tạo/sửa catalog; quyền đọc công khai không cấp quyền ghi cho khách hoặc nhân viên.

## API và lưu dữ liệu

`POST /api/v1/products` nhận thêm trường `description` tùy chọn. `PATCH /api/v1/products/{id}` dùng quy tắc:

- Không gửi trường hoặc gửi `null`: giữ mô tả đã lưu.
- Gửi văn bản: bỏ khoảng trắng bao quanh, giữ xuống dòng bên trong và thay nội dung.
- Gửi chuỗi trống/toàn khoảng trắng: xóa mô tả, database lưu `null`.
- Vượt 5.000 ký tự: trả `400 Bad Request`; dữ liệu đang có được giữ nguyên.

`GET /api/v1/products` và `GET /api/v1/products/{id}` trả thêm `description`. Frontend escape nội dung và hiển thị xuống dòng bằng CSS; không thực thi HTML/script có trong mô tả.

Flyway **V7__add_product_description.sql** thêm cột `products.description TEXT` tùy chọn và CHECK độ dài. Có migration PostgreSQL/H2 tương ứng. V1–V6, giá, ảnh, tồn kho, đơn hàng và sổ cái không bị viết lại. Khởi động lại Spring Boot để áp dụng migration và entity/API mới, rồi nhấn **Ctrl + F5**.

## Kiểm chứng và phạm vi

Test tích hợp kiểm tra tạo/đọc/lưu thật, PATCH giữ/đổi/xóa, giới hạn độ dài và quyền ADMIN. Kiểm tra trình duyệt dùng PostgreSQL QA riêng cho form quản trị, nội dung tiếng Việt, chống thực thi HTML, giỏ nhiều sản phẩm và layout desktop/mobile.

Trang chi tiết có URL chia sẻ và gallery URL, nhưng chưa có upload file, bảng thuộc tính để lọc theo cấu hình hoặc HTML render từ server phục vụ SEO. Tồn khả dụng theo chi nhánh vẫn do backend xác nhận khi đặt hàng; giao diện khách không gọi API kho nội bộ để suy đoán số lượng. Sản phẩm INACTIVE hiển thị trạng thái ngừng bán và khóa nút thêm giỏ; ID không tồn tại có thông báo rõ và đường quay về cửa hàng. Dialog chi tiết Admin vẫn giữ để xem bộ ảnh và số liệu kho nội bộ theo quyền.

## Kết quả kiểm chứng ngày 01/10/2026

- Toàn suite **231 test PASS**, 0 failures/errors/skipped; gồm **12 ca mới** cho mô tả. Maven dùng `-Dmaven.repo.local=C:/Users/Admin/.m2/repository`.
- `package` thành công sau khi test; JAR: `target/stockflow-0.0.1-SNAPSHOT.jar`.
- **28 kiểm tra Chrome PASS**, 47 phản hồi API thành công, không có exception JavaScript. Đã xem ảnh layout desktop/mobile và form quản trị.
- PostgreSQL QA 13.2 được chạy bằng JAR V6 rồi nâng cấp sang V7: giữ nguyên **24 sản phẩm, 72 inventories và 72 movements**, kể cả giá/ảnh/SKU và thời điểm tồn kho. Các mô tả ban đầu là null.
- CHECK của PostgreSQL chặn UPDATE trực tiếp với mô tả 5.001 ký tự; khối kiểm chứng rollback thay đổi bị chặn.
- Dữ liệu kiểm chứng được ghi vào database QA riêng. Ứng dụng chính cần khởi động lại để dùng entity/API V7; thao tác này không tự thêm mô tả hoặc viết lại sản phẩm đã nhập.
- Chưa chạy Docker Compose/PostgreSQL 17 hoặc CI GitHub trong lượt này; kiểm chứng PostgreSQL hiện là QA cục bộ, chưa phải Testcontainers.

## File của tính năng mô tả V7

Backend và schema:

- `src/main/java/com/stockflow/catalog/domain/Product.java`
- `src/main/java/com/stockflow/catalog/dto/CreateProductRequest.java`
- `src/main/java/com/stockflow/catalog/dto/UpdateProductRequest.java`
- `src/main/java/com/stockflow/catalog/dto/ProductResponse.java`
- `src/main/java/com/stockflow/catalog/service/ProductService.java`
- `src/main/java/com/stockflow/catalog/api/ProductController.java`
- `src/main/resources/db/migration/V7__add_product_description.sql`
- `src/test/resources/db/migration/V7__add_product_description.sql`
- `src/test/java/com/stockflow/catalog/api/ProductDescriptionIntegrationTest.java`

Giao diện:

- `src/main/resources/static/index.html`
- `src/main/resources/static/app.js`
- `src/main/resources/static/styles.css`

Tài liệu:

- `README.md`
- `ANTIGRAVITY_HANDOFF.md`
- `HUONG_PHAT_TRIEN.md`
- `docs/storefront-roadmap.md`
- `docs/tech-store.md`
- `docs/product-details.md`

Log, snapshot và script QA nằm trong `target/` được Git bỏ qua; không phải source cần commit.

## Trang sản phẩm riêng — kiểm chứng ngày 02/10/2026

Thay đổi giao diện từ hộp thoại sang trang `/san-pham/{id}`. Bổ sung MVC controller forward tới `index.html` và mở GET của trang trong SecurityConfig; các API catalog/đơn/kho giữ nguyên quyền và contract. Không thêm hoặc sửa migration cho lượt đổi trang này.

- Toàn suite **234 test PASS**, 0 failures/errors/skipped, gồm 3 URL chi tiết bổ sung trong test HTTP thật. Lệnh: `.\mvnw.cmd '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' test`.
- `package` thành công với cùng Maven repository và `-DskipTests` sau khi suite đã PASS. JAR: `target/stockflow-0.0.1-SNAPSHOT.jar`.
- **42 kiểm tra Chrome PASS**, 72 phản hồi API, 0 exception JavaScript. Có một `404` chủ động khi thử ID không tồn tại; các response còn lại thành công.
- Kiểm tra bấm giá/khoảng trống trên thẻ, thêm nhanh, Ctrl-click mở tab mới, Back/Forward, reload URL, mô tả chống thực thi HTML, sản phẩm INACTIVE, số lượng sai, đồng bộ chi nhánh và request chi tiết chậm khi rời trang.
- Kiểm tra mua từ trang chi tiết → giữ 2 hàng → thanh toán → staff đóng gói/giao/hoàn tất/nhận trả. Tồn kho về đúng 8 hàng, reserved về 0 và chỉ một movement RETURN_RESTOCK cho đơn.
- Đã xem ảnh Chrome desktop/mobile; trang một cột trên điện thoại, form không tràn ngang. Admin vẫn xem/sửa mô tả trong dialog quản trị hiện có; Manager vẫn mở báo cáo được.
- Kiểm chứng dùng PostgreSQL 13.2 và Chrome QA riêng, không ghi dữ liệu vào database `stockflow` của người dùng. Chưa chạy Docker Compose/PostgreSQL 17 hoặc GitHub CI trong lượt này.

Để dùng controller trang mới ở phiên IntelliJ đang chạy, dừng rồi chạy lại Spring Boot và nhấn **Ctrl + F5**. Không có thao tác reset hoặc nạp thêm catalog vào database sử dụng thực tế.

### File thay đổi cho lượt chuyển trang

- `src/main/java/com/stockflow/catalog/api/ProductPageController.java` — mới, forward URL chi tiết.
- `src/main/java/com/stockflow/common/config/SecurityConfig.java` — mở GET trang, giữ JWT cho API.
- `src/main/resources/static/index.html` — khu vực trang chi tiết, breadcrumb và đường về cửa hàng.
- `src/main/resources/static/app.js` — liên kết thẻ, History API, tải/hiển thị chi tiết và giữ giỏ trong điều hướng.
- `src/main/resources/static/styles.css` — trang hai cột, mobile và vùng liên kết toàn thẻ.
- `src/test/java/com/stockflow/common/api/WebDemoIntegrationTest.java` — URL trang công khai và nội dung giao diện.
- `README.md`
- `ANTIGRAVITY_HANDOFF.md`
- `HUONG_PHAT_TRIEN.md`
- `docs/storefront-roadmap.md`
- `docs/tech-store.md`
- `docs/product-details.md`
