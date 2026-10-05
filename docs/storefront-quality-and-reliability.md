<!-- Cập nhật ngày 05/10/2026: nêu đúng tính năng đã làm, phạm vi kiểm thử và quyết định còn chờ. -->
# StockFlow Tech: khám phá sản phẩm và độ tin cậy khi đặt hàng

StockFlow Tech là cửa hàng bán điện thoại, laptop, thiết bị âm thanh và phụ kiện của một doanh nghiệp có nhiều kho. Storefront và dashboard tiếp tục dùng HTML/CSS/JavaScript thuần. Không xóa sản phẩm, danh mục, đơn hoặc dữ liệu kho đang được chủ cửa hàng sử dụng.

## Giao diện đã hoàn thiện

- Header, tìm kiếm, nội dung banner, kệ hàng và footer thống nhất định vị cửa hàng công nghệ.
- Trang chủ có lối tắt tới danh mục điện thoại/laptop/âm thanh/phụ kiện đang tồn tại. Ưu tiên nhóm cấp đầu, hỗ trợ tên như “Âm thanh, Mic thu âm”, không hardcode ID.
- Mục bán chạy dùng API công khai riêng. Lỗi API có nút thử lại, không làm hỏng catalog. Chưa có doanh số thì hiển thị trạng thái trống, không gắn nhãn bán chạy cho sản phẩm bất kỳ.
- Ảnh sản phẩm đã lưu không có caption dư; ảnh dự phòng vẫn ghi “Ảnh minh họa”. Khi ảnh thật lỗi và dùng ảnh thay thế, caption được mở lại. Không sửa tên/ảnh trong database để làm đẹp thẻ.
- SKU có ellipsis và title đầy đủ; liên kết thẻ, chọn phiên bản/màu, lọc giá và giỏ hiện có được giữ.
- `/#shop/help` là trang hướng dẫn mua hàng riêng. Nội dung nêu đúng checkout, giữ 15 phút, thanh toán/vận chuyển mô phỏng, theo dõi vận đơn và giới hạn hủy của khách.
- Các khối mới dùng biến màu hiện có, hỗ trợ sáng/tối, hai cột trên điện thoại và `prefers-reduced-motion`.

## API bán chạy

`GET /api/v1/storefront/bestsellers?limit=4` mở công khai, mặc định `limit=8`, chỉ chấp nhận 1–12. Response là danh sách `ProductResponse`, không có dữ liệu đơn/khách, số tồn nội bộ hoặc doanh thu.

SQL tổng hợp số lượng các `order_items` thuộc đơn CONFIRMED/PACKED/SHIPPED/DELIVERED có payment PAID. SKU màu/phiên bản được gộp qua `product_variants` về model. Đơn PENDING/EXPIRED/CANCELLED/RETURNED và thanh toán REFUNDED không đóng góp. Xếp theo số lượng giảm dần, ID model tăng dần khi bằng nhau; đây là thứ hạng toàn thời gian, không phải số bán minh họa.

Chỉ hiển thị model ACTIVE còn ít nhất một cấu hình bán được hoặc sản phẩm không có biến thể. Giá, ảnh và cấu hình lấy từ catalog hiện tại; giá đơn lịch sử vẫn giữ nguyên. API báo cáo quản trị và quy tắc phân quyền của nó không đổi.

## Chống tạo đơn trùng — Flyway V17

`POST /api/v1/orders` tiếp tục yêu cầu CUSTOMER và DTO nhận hàng hiện có. Header tùy chọn `Idempotency-Key` gồm 1–128 ký tự chữ/số hoặc `. _ : -`.

- Cùng khách + khóa + nội dung: trả cùng đơn với trạng thái hiện tại, HTTP 201; không reserve hoặc ghi movement thêm.
- Cùng khách + khóa nhưng nội dung khác: HTTP 409.
- Khách khác có thể dùng cùng chuỗi khóa; không đọc được đơn của nhau.
- Không có header: giữ contract cũ, mỗi POST hợp lệ tạo một đơn.
- Thiếu tồn hoặc lỗi transaction: rollback cả khóa, đơn, reserve và movements; có thể thử lại cùng khóa sau khi điều chỉnh điều kiện kho.

`order_creation_requests` có UNIQUE `(customer_id, request_key)`, SHA-256 của DTO đã chuẩn hóa và FK tới đơn. Mặt hàng được sắp theo product ID trước khi hash; định dạng điện thoại/khoảng trắng dùng chuẩn hóa sẵn có trong DTO. Bản ghi khóa không lưu tên, điện thoại hoặc địa chỉ.

`INSERT ... ON CONFLICT DO NOTHING` và `SELECT ... FOR UPDATE` khóa đúng lần đặt. Việc chiếm khóa, tạo đơn, atomic reserve, ghi ledger và gắn order ID nằm trong cùng transaction. Không khóa tất cả đơn của khách hoặc sử dụng khóa cục bộ chỉ có tác dụng trên một instance. Các quy tắc DISPATCH lúc thanh toán, fulfillment, hủy/hoàn và thứ tự inventory ID vẫn do `OrderService` hiện có thực hiện.

Frontend giữ metadata chủ phiên/khóa/hash trong sessionStorage trên HTTPS hoặc localhost. Thông tin người nhận không được lưu. Sau lỗi mạng, gửi lại cùng nội dung sẽ dùng lại khóa, kể cả sau F5 nếu khách nhập lại thông tin như cũ. Đặt thành công hoặc đổi tài khoản xóa metadata; thay nội dung tạo lần đặt mới. Với HTTP không có Web Crypto, khóa chỉ được giữ trong bộ nhớ đến khi tải lại trang. Hash không phải mã hóa thông tin liên hệ. Khi chưa rõ lần đặt trước thành công hay chưa, hãy giữ nguyên nội dung để thử lại hoặc xem “Đơn hàng của tôi” trước khi tạo lần mua khác.

V17 có bản PostgreSQL và H2 riêng. Không sửa V1–V16 đã áp dụng. Khởi động lại Spring Boot để Flyway nạp V17 và đăng ký API mới.

## PostgreSQL và Testcontainers

Lệnh `test` tiếp tục chạy H2 nhanh. Profile `postgres-tests` dùng Maven Failsafe và `StockflowPostgresIT`, nạp trực tiếp `src/main/resources/db/migration` để tránh các bản H2 trùng tên trên test classpath. Testcontainers được ghim ở 1.21.4 qua BOM Spring Boot; bản này sửa tương thích Docker Engine mới theo [release note chính thức](https://github.com/testcontainers/testcontainers-java/releases/tag/1.21.4). Image `postgres:17-alpine` đồng bộ Compose. Không âm thầm skip khi Docker thiếu.

```powershell
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' test
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' '-Ppostgres-tests' verify
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' '-DskipTests' package
```

CI chạy H2, `docker info`, rồi profile PostgreSQL trước khi đóng gói/upload JAR. Đây là cấu hình đã bổ sung, chưa có kết quả từ GitHub Actions của commit này.

Chế độ chẩn đoán trên máy chưa có Docker nhận `-Dstockflow.pg-test.url=jdbc:postgresql://127.0.0.1:<port>/stockflow_qa`, chỉ cho database QA riêng trên loopback với user `stockflow_qa`. Chế độ này vẫn chạy cùng các ca PostgreSQL/Flyway thật nhưng không xác minh vòng đời container. Không trỏ tới database cửa hàng hoặc dùng tên `stockflow`.

Tham khảo [module PostgreSQL](https://java.testcontainers.org/modules/databases/postgres/) và [quản lý vòng đời container](https://java.testcontainers.org/test_framework_integration/manual_lifecycle_control/) của Testcontainers.

## Kết quả kiểm chứng

- Suite H2: **580 tests PASS**, không lỗi hoặc skip; có thêm 16 test idempotency và 9 test bán chạy.
- PostgreSQL QA 18.3 riêng trên port 55435: **9 tests PASS**, nạp V1–V17 production. Bao gồm trigger UPDATE/DELETE, CHECK/UNIQUE, 12 lượt đồng thời cùng khóa, 8 lượt cạnh tranh năm đơn vị tồn, rollback nhiều mặt hàng, đảo thứ tự SKU và HTTP/JWT/bán chạy.
- Chrome thật, API giả lập: **241 kiểm tra PASS** gồm 85 hồi quy giỏ/đơn/quyền, 56 lọc giá, 55 bố cục/bộ lọc và 45 khám phá/idempotency/hướng dẫn/cây danh mục thực tế. Sáng/tối, desktop 1440px, tablet 768px, mobile 390/375px đã kiểm tra. Các thao tác không ghi database đang dùng.
- `package -DskipTests` thành công sau hai suite đã PASS. JAR chứa V17 và bốn tài nguyên tĩnh khớp hash nguồn. Khởi chạy JAR riêng trên 8096 với PostgreSQL QA đã xác nhận GET `/` và `/api/v1/storefront/bestsellers` trả 200, Swagger có header retry tùy chọn.
- Chưa chạy Docker/Testcontainers PostgreSQL 17 tại máy này vì chưa có Docker ở vị trí thông thường. PostgreSQL QA 18.3 không thay thế việc xác minh image/engine Docker ở CI.
- Chưa xác minh trên Safari/iOS hoặc Android thật. Ảnh CDN và font có dự phòng, phụ thuộc mạng khi tải nguồn bên ngoài.

## Hai quyết định nghiệp vụ còn chờ

Chưa tạo bảng reviews hoặc công bố chính sách bán hàng khi chưa nhận lựa chọn của chủ cửa hàng:

1. Đề xuất: khách có đơn DELIVERED đánh giá 1–5 sao và bình luận; một đánh giá/customer/model, được sửa, hiển thị ngay, giữ lại nếu đơn sau đó RETURNED. Chốt trước khi triển khai schema và kiểm tra quyền mua hàng.
2. Liên hệ/bảo hành/đổi trả: cần thông tin và điều kiện thật, hoặc chấp nhận nội dung có nhãn “Cửa hàng demo / Chính sách minh họa”. Không tự tạo hotline, địa chỉ, thời hạn bảo hành hoặc cam kết đổi trả.

## File thuộc lượt thay đổi này

<!-- Danh sách tách rõ file mới/sửa; những thay đổi bộ lọc giá có sẵn trong working tree tiếp tục được giữ. -->
- Sửa `src/main/resources/static/index.html`, `app.js`, `styles.css`: định vị, khám phá/bán chạy, thẻ ảnh, trang hướng dẫn và khóa checkout.
- Sửa `src/main/java/com/stockflow/common/config/SecurityConfig.java`: chỉ mở GET bán chạy.
- Sửa `src/main/java/com/stockflow/order/api/OrderController.java`: nhận header tùy chọn.
- Thêm `src/main/java/com/stockflow/order/service/OrderPlacementService.java` và `order/repository/OrderCreationRequestRepository.java`.
- Thêm `src/main/java/com/stockflow/catalog/api/StorefrontDiscoveryController.java`, `catalog/service/StorefrontDiscoveryService.java`, `catalog/repository/StorefrontDiscoveryRepository.java`.
- Thêm `src/main/resources/db/migration/V17__create_order_creation_requests.sql` và `src/test/resources/db/migration/V17__create_order_creation_requests.sql`.
- Thêm `src/test/java/com/stockflow/order/OrderIdempotencyIntegrationTest.java`, `catalog/StorefrontDiscoveryIntegrationTest.java`, `persistence/StockflowPostgresIT.java`.
- Thêm `src/test/resources/application-postgres-test.yml`.
- Sửa `src/test/java/com/stockflow/common/api/WebDemoIntegrationTest.java`: nội dung nhận diện mới, vẫn kiểm tra HTTP/public resources/quyền cũ.
- Sửa `pom.xml` và `.github/workflows/ci.yml`: Testcontainers, Failsafe/profile PostgreSQL, CI.
- Sửa `README.md`, `ANTIGRAVITY_HANDOFF.md`, `HUONG_PHAT_TRIEN.md`, `docs/storefront-roadmap.md`; thêm tài liệu này.

Tổng cộng 24 file được thêm/sửa trong lượt này. `assets/theme.css` và `docs/price-filter.md` đã có thay đổi từ lượt bộ lọc trước và được giữ nguyên; toàn bộ working tree hiện có 26 file thay đổi. Chưa commit hoặc chạy migration lên database cửa hàng đang dùng.
