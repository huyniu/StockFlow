<!-- Hồ sơ V16 là một giai đoạn nhỏ: liên hệ tự chỉnh sửa, gợi ý checkout và kiểm chứng quyền sở hữu. -->
# Hồ sơ khách hàng và gợi ý thông tin nhận hàng

Triển khai ngày 04–05/10/2026. Giữ nguyên nghiệp vụ giá, giỏ hàng, tồn kho, payment, fulfillment và ledger.

## Sử dụng trên giao diện

Đăng nhập CUSTOMER, chọn **Tài khoản** trên header hoặc **Tài khoản của tôi** trong thanh điều hướng. Đường dẫn trực tiếp là `http://localhost:8080/#shop/account`. Khách chưa đăng nhập được yêu cầu đăng nhập và quay lại hồ sơ sau khi thành công.

- Xem email, ngày tham gia, họ tên và số liên hệ.
- Chỉnh họ tên hoặc số điện thoại, bấm **Lưu thông tin**.
- Điện thoại tùy chọn; để trống rồi lưu để xóa số đã bổ sung.
- **Làm mới** đọc lại dữ liệu từ API. Khi đọc thất bại, form khóa lưu và hướng dẫn thử lại.
- Trong lúc lưu, nút và input bị khóa để tránh gửi lặp hoặc mất nội dung đang nhập.
- Đăng xuất, JWT hết hạn hoặc đổi tài khoản xóa nội dung form, hủy request cũ và không để phản hồi cũ đổi danh tính hiện tại.

Giao diện hồ sơ dành cho CUSTOMER; các role vận hành vẫn vào dashboard. API liên hệ của chính mình dùng được cho mọi role đã xác thực.

## API và validation

`GET /api/v1/users/me` giữ contract hiện có và thêm `phone` nullable. Login/register cũng dùng UserResponse nên trả thêm trường này; tài khoản cũ hoặc chưa bổ sung số có `phone=null`.

```http
PATCH /api/v1/users/me
Authorization: Bearer <access_token>
Content-Type: application/json
```

```json
{
  "full_name": "Nguyễn Minh An",
  "phone": "+84 (90) 123-4567"
}
```

Response **200 OK** dùng UserResponse, số chuẩn hóa thành `+84901234567`. Không trả password hash.

- Họ tên được strip đầu/cuối, dài 1–150 ký tự khi gửi; chặn tên trống và ký tự điều khiển. Không bắt buộc tên chỉ chứa chữ Latin.
- Điện thoại bỏ khoảng trắng, dấu ngoặc và dấu gạch nối như checkout. Dạng lưu gồm 8–15 chữ số ASCII, dấu `+` tùy chọn; không tự đổi mã quốc gia.
- Bỏ qua hoặc `null` giữ giá trị cũ của từng trường. `phone: ""` hoặc chỉ khoảng trắng xóa thành null.
- Request phải có ít nhất một trường liên hệ khác null; `{}`, JSON null hoặc chỉ trường đặc quyền trả **400**.
- Trường `id`, `email`, `role`, `role_id`, `status`, `password_hash` không được DTO tiếp nhận để cập nhật. ID đích luôn lấy từ JWT principal, không lấy từ body hoặc query.
- Không JWT/JWT sai/tài khoản INACTIVE nhận **401**. Không có API PATCH tài khoản khác theo ID.
- Đường dẫn không tồn tại trả **404**, đã sửa lỗi handler tổng quát từng biến trường hợp này thành 500.

Service kiểm tra validation cả khi được gọi ngoài HTTP. Trong một transaction, repository chạy UPDATE chỉ tên/số/thời điểm cập nhật và yêu cầu trạng thái ACTIVE. Cờ riêng cho từng trường phân biệt bỏ qua với xóa số, đồng thời giữ các thay đổi khác trường khi hai request chạy đồng thời. Không ghi lại toàn bộ entity principal cũ, tránh làm mất thay đổi quyền hoặc credential của luồng khác. Hồ sơ được nạp lại sau bulk UPDATE.

## Migration và dữ liệu cũ

V16 PostgreSQL và H2 thêm `users.phone VARCHAR(30)` nullable cùng CHECK định dạng. Không sửa migration V1–V15, không tự lấy số từ đơn cũ, không đổi email, mật khẩu, role hay trạng thái của tài khoản đã tồn tại.

Migration nâng V15→V16 được test trên H2 với đối chiếu các cột user cũ. PostgreSQL QA nạp V1–V16 bằng Flyway và chạy API/constraint thật; chưa thực hiện nâng V15→V16 trên database người dùng trong lượt này.

## Liên hệ hồ sơ và checkout

Khi mở giỏ bằng tài khoản CUSTOMER, frontend gợi ý tên/số liên hệ **chỉ vào ô đang trống**. Người nhận nhập tay, kể cả khác chủ tài khoản, được giữ nguyên khi mở lại giỏ hoặc khi vừa sửa hồ sơ.

Đặt hàng thành công xóa form checkout; lần mở giỏ tiếp theo gợi ý liên hệ hiện tại. Profile không bổ sung địa chỉ tự động, không thay delivery snapshot của đơn cũ, không thay giá hoặc số lượng tồn. Backend tiếp tục yêu cầu khách xác nhận đầy đủ tên/số/địa chỉ khi tạo đơn.

Frontend không lưu họ tên/số điện thoại hồ sơ vào cart record hoặc JWT record trong sessionStorage. Form dùng value/textContent cho dữ liệu tự nhập, không diễn giải tên thành HTML.

## Kiểm chứng

```powershell
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' test
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' -DskipTests package
```

- **555/555 test PASS**, 0 failures/errors/skipped; thêm 29 test hồ sơ và migration. Suite gồm quyền sở hữu/mass assignment, validation/rollback, null/bỏ qua/xóa số, JWT cũ/INACTIVE, cập nhật đồng thời, bảo toàn delivery snapshot/tồn/ledger và nâng migration.
- Package sau toàn bộ suite: **BUILD SUCCESS**. Không chạy lại suite trong bước package.
- **40 kiểm chứng Chrome PASS**, không có ngoại lệ JavaScript. Luồng thông thường dùng API thật trên database PostgreSQL 13.2 riêng: sửa hồ sơ, F5, gợi ý giỏ, người nhận riêng, tạo đơn và giữ snapshot sau đổi hồ sơ.
- Giao diện hồ sơ được kiểm tra sáng/tối tại 1440px, 390px và 375px; không tràn ngang. Có kiểm tra tên chứa markup hiển thị như văn bản, lỗi 400, đọc lỗi/thử lại, response chậm/đổi role và đăng xuất.
- **12 kiểm chứng API PostgreSQL PASS** bổ sung: PATCH từng trường có giá trị null, hai request cập nhật khác trường đồng thời, xóa số, mass assignment, validation không ghi nửa request, 404 và đăng nhập bằng credential cũ.
- **7 kiểm tra CHECK PostgreSQL PASS** bằng SQL trực tiếp: năm định dạng sai bị chặn, số quốc tế tối đa và null được chấp nhận; bài chạy trong transaction rollback.
- **56 kiểm chứng hồi quy giỏ/đồng bộ đơn Chrome PASS**, dùng API giả lập, không có ngoại lệ JavaScript. Giữ các quy tắc phiên tab, đổi actor, SKU/giá/chi nhánh, retry khi mất mạng và tự cập nhật đơn từ lượt trước.

GET lỗi và PATCH chậm trong Chrome là fault injection có chủ đích; font/ảnh CDN bên ngoài bị chặn trong bài này. Kết quả không đại diện cho kiểm thử toàn bộ website hoặc dịch vụ bên ngoài. Script/log/ảnh QA nằm trong `target/profile-qa/`, không đưa vào Git.

Database người dùng và ứng dụng cổng 8080 không bị thay đổi dữ liệu hoặc khởi động lại. Khởi động lại Spring Boot để Flyway áp dụng V16 và nạp API mới, rồi Ctrl+F5 để cập nhật giao diện.

## File của giai đoạn này

- `src/main/java/com/stockflow/user/domain/User.java`
- `src/main/java/com/stockflow/user/dto/UserResponse.java`
- `src/main/java/com/stockflow/user/dto/UpdateProfileRequest.java` (mới)
- `src/main/java/com/stockflow/user/repository/UserRepository.java`
- `src/main/java/com/stockflow/user/service/UserProfileService.java` (mới)
- `src/main/java/com/stockflow/user/api/UserController.java`
- `src/main/java/com/stockflow/common/exception/GlobalExceptionHandler.java`
- `src/main/resources/db/migration/V16__add_user_contact_phone.sql` (mới)
- `src/test/resources/db/migration/V16__add_user_contact_phone.sql` (mới)
- `src/test/java/com/stockflow/user/UserProfileIntegrationTest.java` (mới)
- `src/test/java/com/stockflow/user/UserProfileMigrationIntegrationTest.java` (mới)
- `src/main/resources/static/index.html`
- `src/main/resources/static/app.js`
- `src/main/resources/static/styles.css`
- `README.md`
- `ANTIGRAVITY_HANDOFF.md`
- `docs/storefront-roadmap.md`
- `docs/customer-profile.md` (mới)

Các thay đổi giỏ/đồng bộ đơn từ lượt trước trong README, roadmap và app.js được giữ nguyên; `docs/cart-and-order-sync.md` không sửa trong giai đoạn hồ sơ.

## Giới hạn và lượt tiếp theo

Chưa có sổ địa chỉ, đổi email/mật khẩu, quản trị người dùng, refresh token, idempotency tạo đơn ở server, đánh giá sau mua hoặc Testcontainers. Hồ sơ liên hệ không được coi là đã hoàn thành các tính năng này. Đề xuất lượt tiếp theo: idempotency tạo đơn để thao tác gửi lại khi mạng chập chờn không tạo hai đơn giữ hàng.
