<!-- Checkout V15 theo quyết định người dùng ngày 04/10/2026; không đổi nghiệp vụ tồn kho hoặc dữ liệu sử dụng thực tế. -->
# Checkout có thông tin nhận hàng

## Phạm vi và quy tắc đã chốt

- Đơn mới bắt buộc **tên người nhận, số điện thoại và địa chỉ giao hàng**; ghi chú tùy chọn.
- **Miễn phí giao hàng** trong MVP. Backend tính `total_amount` từ giá mặt hàng, không nhận giá/phí/chủ đơn do client tự khai báo.
- Thông tin được chụp cố định trên từng đơn, không lấy lại từ hồ sơ khách khi thanh toán hoặc giao hàng.
- Muốn đổi thông tin, khách hủy đơn **PENDING** rồi đặt lại. Chưa có API sửa địa chỉ, thu phí giao hàng hoặc giao một phần.
- Giữ khách chọn chi nhánh, một đơn một kho, reserve 15 phút, atomic conditional update và thứ tự khóa inventory ID.
- DISPATCH lúc thanh toán; pack/ship/deliver không trừ kho lần hai. Hủy/nhận trả vẫn nhả/hoàn kho cùng ledger trong transaction.

## Contract API

`POST /api/v1/orders` chỉ cho CUSTOMER. Actor lấy từ JWT và được kiểm tra bằng dữ liệu tài khoản trong DB.

```json
{
  "warehouse_id": 1,
  "items": [
    { "product_id": 1, "quantity": 2 }
  ],
  "delivery": {
    "recipient_name": "Nguyễn Văn An",
    "recipient_phone": "+84 90-123-4567",
    "address": "12 Phố Mới, phường Cầu Giấy, Hà Nội",
    "note": "Gọi trước khi giao"
  }
}
```

ID trong ví dụ là minh họa; dùng ID từ database của bạn. **Client cũ phải bổ sung `delivery` khi tạo đơn mới.** Bỏ qua hoặc null trả 400; không tự tạo địa chỉ giả để tương thích payload cũ.

- `recipient_name`: không trống, tối đa 150 ký tự.
- `recipient_phone`: 8–15 chữ số, cho dấu `+` ở đầu. Server bỏ khoảng trắng ASCII, dấu ngoặc và dấu gạch nối trước validation/lưu, ví dụ `+84 90-123-4567` thành `+84901234567`.
- `address`: không trống, tối đa 500 ký tự; giữ xuống dòng bên trong.
- `note`: tùy chọn, tối đa 1.000 ký tự; bỏ qua/null/rỗng/toàn khoảng trắng lưu null.

Tên, địa chỉ và ghi chú được bỏ khoảng trắng đầu/cuối. Validation áp dụng cả controller và `OrderService`, kể cả gọi service trực tiếp. Sai dữ liệu trả 400; không đủ hàng trả 409 và rollback toàn bộ order, items, reservation và movements.

Response tạo đơn, chi tiết, `/orders/my` và các hành động vòng đời thêm `delivery` với bốn trường trên. Chủ đơn và ADMIN/MANAGER đọc được; STAFF chỉ đọc đơn thuộc kho được phân công. Anonymous bị 401, khách khác/staff ngoài kho bị 403. Danh sách vận hành `/orders` giữ summary tối thiểu, không thêm địa chỉ vào mọi dòng phân trang.

## Schema và đơn lịch sử

V15 thêm `orders.recipient_name VARCHAR(150)`, `recipient_phone VARCHAR(16)`, `delivery_address VARCHAR(500)`, `delivery_note VARCHAR(1000)` trong migration PostgreSQL/H2 riêng. CHECK cho phép toàn null đối với đơn lịch sử, hoặc bản chụp đầy đủ với điện thoại hợp lệ. Không sửa V1–V14, giá, tồn kho, ledger, payment hoặc shipment đã áp dụng.

Các đơn trước V15 trả **`delivery: null`**, vẫn đọc, thanh toán, hủy, giao và nhận trả theo quy tắc cũ. Giao diện báo “Đơn lịch sử chưa có thông tin nhận hàng.” Không suy đoán địa chỉ lịch sử từ tên tài khoản.

`DeliveryDetails` là value object JPA không có setter; cột `updatable=false` và không có endpoint sửa bản chụp. Đây là quy tắc bất biến ở tầng ứng dụng; không phải database trigger chống mọi câu SQL quản trị như ledger. CHECK vẫn cho phép insert SQL legacy toàn null để hỗ trợ dữ liệu cũ; API tạo đơn mới luôn bắt buộc người nhận.

## Thao tác giao diện

Mở giỏ hàng, chọn chi nhánh, nhập người nhận/điện thoại/địa chỉ và ghi chú, kiểm tra phí giao hàng miễn phí rồi bấm **Tiến hành đặt hàng**.

Khách vãng lai đăng nhập từ checkout vẫn giữ giỏ và thông tin đã nhập; cần xem lại và bấm đặt đơn. Lỗi 400/409 giữ dữ liệu để sửa. Đặt thành công, đăng xuất hoặc đổi tài khoản xóa thông tin trên form và dữ liệu riêng của phiên trước. Thông tin đơn đã lưu vẫn hiển thị tại chi tiết cho khách/nhân viên đúng quyền.

Địa chỉ và ghi chú được escape khi render, giữ xuống dòng dưới dạng văn bản; không chèn HTML do khách nhập vào DOM. Form và chi tiết tương thích nền sáng/tối, tên/điện thoại xuống một cột trên màn hình nhỏ.

Giỏ/người nhận chưa đặt chỉ nằm trong bộ nhớ trang; tải lại trang sẽ mất phần chưa gửi. Khóa nút hạn chế nhấp lặp trong một lượt gửi, **chưa phải idempotency tạo đơn phía server**. Không tự retry POST khi lỗi mạng vì request trước có thể đã commit.

## Kiểm chứng ngày 04/10/2026

```powershell
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' test
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' package
```

- Baseline trước checkout: **498 PASS**. Sau thay đổi: **526 PASS**, 0 failures/errors/skipped; `test` và `package` BUILD SUCCESS, JAR Java 17 tạo thành công.
- **28 ca mới**: 26 ca checkout và 2 ca nâng migration. Phủ chuẩn hóa UTF-8/điện thoại, giới hạn, null/thiếu/trống/quá dài, ghi chú tùy chọn, server tính giá, quyền sở hữu/phạm vi kho, bản chụp không đổi sau cập nhật hồ sơ/vòng đời, rollback nhiều mặt hàng và đơn legacy.
- Test catalog, fulfillment, demo và danh sách đơn dùng fixture người nhận hợp lệ; giữ nguyên kiểm tra cũ, gồm concurrency nhiều thread. Ca mặt hàng rỗng/null vẫn dùng người nhận hợp lệ để validation checkout không che lỗi mặt hàng.
- PostgreSQL **13.2**, database QA riêng: chạy JAR V14 tạo đơn PENDING và DELIVERED có payment/shipment/ledger, nâng JAR V15 và đối chiếu **19 bảng giữ nguyên dữ liệu**. Migration không bịa người nhận cho đơn cũ.
- **24 kiểm tra API PostgreSQL PASS**: đơn legacy, validation/rollback, quyền, người nhận qua fulfillment/hoàn kho, payment lặp và 16 request tranh 5 sản phẩm. Đúng 5 đơn thành công, available=0/reserved=5; hủy trả đúng 5 sản phẩm.
- Hai kiểm tra SQL trực tiếp PASS: CHECK PostgreSQL chặn bản chụp thiếu trường và số điện thoại sai định dạng; các thử cập nhật không làm thay đổi đơn QA.
- **25 kiểm tra Chrome PASS**, 0 exception JavaScript: form người nhận, login giữ checkout, 400/409 giữ giỏ, tạo đơn/thanh toán, XSS dưới dạng văn bản, admin/staff đọc chi tiết, legacy, xóa dữ liệu khi đổi phiên, nền sáng/tối và viewport 390px.
- Lỗi khóa JAR trên Windows khi instance QA dùng tệp trong `target` đã được khắc phục: chạy QA từ bản sao riêng, đóng gói lại thành công. JAR cuối được dùng cho các kiểm tra API/Chrome.

Log/ảnh/script cục bộ trong `target/checkout-qa/`, không commit. Database `stockflow` và instance người dùng cổng 8080 không bị thay đổi trong lượt này.

Sau kiểm chứng đã dừng đúng instance QA cổng 8124/Chrome profile riêng và xóa database QA vừa tạo. Health của app người dùng cổng 8080 vẫn trả 200. Toàn bộ 28 file migration V1–V14 giữ nguyên SHA-256; ba resource frontend và migration V15 trong JAR cuối khớp source.

## File thay đổi trong lượt checkout

- `src/main/java/com/stockflow/order/api/OrderController.java`
- `src/main/java/com/stockflow/order/domain/Order.java`
- `src/main/java/com/stockflow/order/domain/DeliveryDetails.java` (mới)
- `src/main/java/com/stockflow/order/dto/CreateOrderRequest.java`
- `src/main/java/com/stockflow/order/dto/DeliveryDetailsRequest.java` (mới)
- `src/main/java/com/stockflow/order/dto/DeliveryDetailsResponse.java` (mới)
- `src/main/java/com/stockflow/order/dto/OrderResponse.java`
- `src/main/java/com/stockflow/order/service/OrderService.java` (kèm sửa lỗi gõ cú pháp đã có trước lượt)
- `src/main/resources/db/migration/V15__add_order_delivery_details.sql` (mới)
- `src/test/resources/db/migration/V15__add_order_delivery_details.sql` (mới)
- `src/main/resources/static/index.html`
- `src/main/resources/static/app.js`
- `src/main/resources/static/styles.css`
- `src/test/java/com/stockflow/order/CheckoutIntegrationTest.java` (mới)
- `src/test/java/com/stockflow/order/CheckoutMigrationIntegrationTest.java` (mới)
- `src/test/java/com/stockflow/order/support/CheckoutTestData.java` (mới)
- `src/test/java/com/stockflow/order/OrderIntegrationTest.java`
- `src/test/java/com/stockflow/order/FulfillmentIntegrationTest.java`
- `src/test/java/com/stockflow/order/OrderQueryIntegrationTest.java`
- `src/test/java/com/stockflow/catalog/api/ProductAvailabilityIntegrationTest.java`
- `src/test/java/com/stockflow/catalog/api/ProductConfigurationArchiveIntegrationTest.java`
- `src/test/java/com/stockflow/catalog/api/ProductOptionsIntegrationTest.java`
- `src/test/java/com/stockflow/catalog/api/ProductVersionsIntegrationTest.java`
- `src/test/java/com/stockflow/demo/DemoDataIntegrationTest.java`
- `README.md`
- `ANTIGRAVITY_HANDOFF.md`
- `HUONG_PHAT_TRIEN.md`
- `docs/storefront-roadmap.md`
- `docs/checkout-delivery.md` (mới)

## Áp dụng và công việc tiếp theo

Khởi động lại Spring Boot trong IntelliJ để Flyway tự áp dụng V15 và nạp API mới, rồi **Ctrl + F5** trang web. Chưa áp dụng V15 trực tiếp lên database sử dụng thực tế trong lượt kiểm thử này.

Chưa chạy lại GitHub Actions, Docker Compose/PostgreSQL 17 hoặc Testcontainers. PostgreSQL QA cục bộ không thay thế suite Testcontainers tự động trong CI. Không có thay đổi refresh token, gateway, upload ảnh, chuyển kho hoặc kiểm kê.

Lượt tiếp theo đề xuất **hồ sơ khách hàng**, rồi lưu giỏ trong trình duyệt nếu cần giữ qua tải lại. Idempotency tạo đơn, đánh giá sau mua và Testcontainers nên triển khai từng lượt riêng với nghiệp vụ/test rõ ràng.
