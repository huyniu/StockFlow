<!-- Báo cáo nghiệm thu giao diện mỏng, ghi đúng phạm vi thay đổi và bằng chứng kiểm chứng thực tế. -->
# StockFlow — Nghiệm thu Web Demo

Ngày kiểm chứng: **30/09/2026**.

Dashboard tiếng Việt được Spring Boot phục vụ tại **http://localhost:8080/**.
HTML, CSS, JavaScript và favicon SVG nằm trong `src/main/resources/static/`;
khởi chạy ứng dụng không cần Node.js, CDN hay bước build frontend riêng.
Chạy với profile `demo` để có bốn tài khoản đăng nhập nhanh và dữ liệu mẫu.

## Chức năng đã triển khai

- Header hiển thị tên hệ thống, liên kết Swagger, email, badge vai trò và đăng xuất.
- Bốn nút đăng nhập demo gọi API JWT thật; hỗ trợ đăng nhập và đăng ký Customer thủ công.
- Catalog lọc theo danh mục/trạng thái, phân trang theo ID ổn định, giỏ nhiều sản phẩm,
  chọn kho hoạt động và tạo đơn giữ hàng 15 phút. Admin thêm sản phẩm và cập nhật tên/giá/trạng thái.
- Khách xem đơn của mình; các vai trò tra cứu ID theo quyền backend. Chi tiết hiển thị
  mặt hàng, giá chụp tại thời điểm đặt, tổng tiền, kho, trạng thái và thời hạn giữ hàng.
  Thanh toán mô phỏng và hủy đơn gọi trực tiếp API vòng đời đơn hàng.
- Tồn kho có bộ lọc kho/sản phẩm và ba số khả dụng, đang giữ, thực tế.
  Các thẻ cộng số liệu của **trang đang xem**, có nhãn rõ phạm vi.
  Admin/Staff nhập kho; Manager/Admin đọc ledger với tồn trước → tồn sau.
- Báo cáo gồm tổng hợp trạng thái đơn, doanh thu ngày/tháng, top sản phẩm và hàng sắp hết.
  Khoảng ngày dùng UTC theo contract API; tổng hợp trạng thái tính toàn bộ đơn.
- Banner/toast hiển thị mã HTTP và thông điệp backend thật. Thẻ kiểm tra quyền cho phép
  trình diễn lỗi 403. Đổi tài khoản xóa giỏ/dữ liệu riêng và hủy request cũ.
- Token lưu trong `sessionStorage` của tab; tải lại xác thực qua `/api/v1/users/me`.
  Dữ liệu ghép vào HTML được escape, gồm tên sản phẩm và ghi chú kiểm toán.

## Thay đổi backend cần thiết

`SecurityConfig` mở các tài nguyên giao diện được yêu cầu. Quyền của các API nghiệp vụ
hiện có vẫn do JWT và kiểm tra role/ownership/phạm vi kho quyết định.

Bổ sung `GET /api/v1/warehouses/order-options` cho bốn vai trò đã đăng nhập để Customer
chọn kho bằng ID thực trong database. Response chỉ có `id`, `code`, `name` của kho ACTIVE.
Endpoint danh sách kho vận hành `/api/v1/warehouses` vẫn chặn CUSTOMER.
Không đổi cấu hình datasource trong `application.yml`.

## Kết quả kiểm thử

Đã chạy Maven Wrapper với Java 17 và Maven cache
`C:/Users/Admin/.m2/repository` thông qua `MAVEN_OPTS`:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

- `test`: **89 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS**.
- `package`: chạy lại cùng 89 tests và **BUILD SUCCESS**.
- JAR: `target/stockflow-0.0.1-SNAPSHOT.jar`.
- `WebDemoIntegrationTest` bổ sung **9 lượt kiểm thử** qua HTTP thật:
  welcome page/index trả 200 và chứa giao diện StockFlow; CSS/JS/SVG public;
  API nội bộ vẫn trả 401 khi thiếu JWT; Customer chỉ nhận lựa chọn kho ACTIVE tối thiểu
  và bị 403 khi gọi danh sách kho vận hành, trong khi Admin nhận 200.

## Kiểm chứng frontend với PostgreSQL

Chạy JAR đã đóng gói trên cổng 8096, profile `demo`, PostgreSQL 13.2 và database QA riêng.
Không dùng dữ liệu trong database `stockflow` của người dùng.
JavaScript thực của dashboard được chạy trong môi trường DOM mô phỏng
[Happy DOM](https://github.com/capricorn86/happy-dom/wiki/Getting-started),
gửi request tới server PostgreSQL thật.

Kết quả lần chạy từ seed sạch: **44 mốc kiểm chứng PASS, 88 lần gọi API, 0 response 5xx**.
Các luồng được kiểm chứng:

1. Xem catalog công khai, lọc sáu sản phẩm Thời trang và chuyển trang.
2. Đăng nhập Customer; tạo đơn hai sản phẩm, tổng 27.970.000 VND, giữ hàng đúng 15 phút.
3. Thanh toán và gọi xác nhận lặp; hủy một đơn PENDING khác.
4. Đặt vượt tồn nhận 409 Conflict và giữ giỏ để chỉnh số lượng;
   Customer gọi báo cáo nhận 403 Forbidden.
5. Manager xem đủ bốn báo cáo, nhóm doanh thu theo tháng,
   tra tồn kho và đọc snapshot ledger.
6. Staff mặc định chọn Hà Nội, bị 403 khi xem ledger hoặc nhập sang Đà Nẵng;
   nhập ba điện thoại tại Hà Nội thành công.
7. Admin tạo sản phẩm, cập nhật giá 234.000,25 VND;
   tên chứa chuỗi HTML hiển thị dưới dạng văn bản.
8. Đổi vai trò khi request tồn kho còn chờ không đưa dữ liệu riêng sang tài khoản Customer.
9. Đăng xuất, đăng nhập thủ công, đăng ký Customer mới và phục hồi phiên qua `users/me`.

Đối chiếu trực tiếp bằng SQL sau thao tác:

- Hai đơn: một CONFIRMED, một CANCELLED; **chỉ một payment PAID**.
- Ledger có 79 dòng: 73 GOODS_RECEIPT, 3 RESERVATION_HOLD,
  2 DISPATCH cho hai mặt hàng và 1 RESERVATION_RELEASE.
- Điện thoại `ELE-PHONE-01` tại `WH-HAN-01`: available = 7, reserved = 0, physical = 7.
- Sản phẩm kiểm chứng có giá mới chính xác 234000.25.

Môi trường DOM chỉ dùng cho kiểm chứng phát triển và đặt trong `target/` được Git bỏ qua.
Harness điều chỉnh phép tính `step` thập phân do sai số của DOM mô phỏng;
HTML production vẫn giữ `min="0.01"`, `step="0.01"` và validation backend.
Đây là kiểm chứng logic DOM/API, **chưa kiểm chứng hình thức hiển thị trên trình duyệt thật**:
môi trường chạy không có Chrome, Edge hoặc in-app browser khả dụng.
CSS có các breakpoint responsive, focus bàn phím và hỗ trợ giảm chuyển động,
nhưng cần xem trực tiếp trên desktop/mobile để nghiệm thu bố cục cuối cùng.

Database và tiến trình QA được dọn sau kiểm chứng; dữ liệu ứng dụng hiện có được giữ nguyên.

## Danh sách file thay đổi

**Thêm mới (7):**

- `src/main/resources/static/index.html`
- `src/main/resources/static/app.js`
- `src/main/resources/static/styles.css`
- `src/main/resources/static/assets/stockflow.svg`
- `src/main/java/com/stockflow/warehouse/dto/WarehouseOrderOptionResponse.java`
- `src/test/java/com/stockflow/common/api/WebDemoIntegrationTest.java`
- `docs/web-demo-verification.md`

**Cập nhật (5):**

- `src/main/java/com/stockflow/common/config/SecurityConfig.java`
- `src/main/java/com/stockflow/warehouse/api/WarehouseController.java`
- `src/main/java/com/stockflow/warehouse/service/WarehouseService.java`
- `src/main/java/com/stockflow/warehouse/repository/WarehouseRepository.java`
- `README.md`
