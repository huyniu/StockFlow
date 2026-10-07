# Bật đăng nhập Google cho StockFlow

Tính năng dùng Google Identity Services (popup), chỉ cần **OAuth Client ID loại Web application**. Không dùng Client Secret, không yêu cầu bật Gmail API và không dùng mật khẩu Gmail.

1. Mở https://console.cloud.google.com/, chọn hoặc tạo project.
2. Vào **Google Auth Platform**, cấu hình Branding và Audience. Nếu ứng dụng đang ở chế độ Testing, thêm tài khoản Google dùng thử trong Test users.
3. Vào **Clients → Create client**, chọn **Web application**.
4. Thêm Authorized JavaScript origins: `http://localhost` và `http://localhost:8080`. Nếu dùng địa chỉ `http://127.0.0.1:8080`, thêm địa chỉ đó riêng. Khi chạy qua ngrok hoặc domain khác, thêm origin HTTPS thực tế (không có đường dẫn phía sau). Popup GIS không cần redirect URI.
5. Sao chép Client ID dạng `...apps.googleusercontent.com`.

Chạy bằng IntelliJ: trong Run Configuration, thêm biến môi trường `GOOGLE_CLIENT_ID=<Client ID thật>`, rồi khởi động lại ứng dụng. File `.env` không tự được Spring Boot đọc khi chạy Maven/IntelliJ.

Profile `demo` và Docker Compose đã có Client ID StockFlow làm giá trị mặc định: `1047952604572-ohm0aaqei4kbcae8t7d9gjgtdhg7he43.apps.googleusercontent.com`. Chạy với profile `demo` không cần nhập lại biến này; khởi động lại sau khi cập nhật mã nguồn. Nếu đã đặt biến `GOOGLE_CLIENT_ID`, giá trị biến môi trường sẽ ghi đè cấu hình mặc định (kể cả chuỗi rỗng).

Chạy Docker: thêm `GOOGLE_CLIENT_ID=<Client ID thật>` vào `.env` cạnh `compose.yaml`, rồi chạy `docker compose up --build -d app`.

Nhấn Ctrl+F5, mở Đăng nhập, bấm nút Google. Thiếu Client ID thì giao diện hiện nút chưa hoạt động cùng thông báo cấu hình; đăng nhập email/mật khẩu vẫn hoạt động. Không có chế độ giả lập đăng nhập Google.

## Luồng xác thực

- `GET /api/v1/auth/google/config`: Client ID công khai và nonce 5 phút; cookie nonce HttpOnly, SameSite=Strict, Secure khi dùng HTTPS; response không được cache.
- `POST /api/v1/auth/google`: JSON `{ "credential": "Google ID token" }`; kiểm tra chữ ký RS256 bằng khóa công khai Google, issuer, audience/azp, thời hạn, email_verified và nonce khớp cookie. Nonce chỉ dùng một lần.
- Tạo mới: quyền CUSTOMER, email đã xác thực, mật khẩu ngẫu nhiên được BCrypt (không có mật khẩu mặc định). Trả JWT StockFlow cùng cấu trúc login thường.
- Tài khoản đã liên kết được nhận dạng bằng `sub`, không bằng email có thể thay đổi.
- Tự liên kết tài khoản email hiện có chỉ khi tài khoản ACTIVE, email đã xác thực và Google quản lý email đó (Gmail hoặc Workspace có `hd` phù hợp). Giữ nguyên vai trò, mật khẩu, hồ sơ. Email bên thứ ba hoặc tài khoản chưa xác thực phải dùng luồng mật khẩu/OTP hiện tại.
- Tài khoản bị khóa không được đăng nhập. Không nhận vai trò từ trình duyệt hoặc Google token.
- Flyway V25 thêm `users.google_subject` với unique index; giữ nguyên dữ liệu tài khoản cũ.
- Nonce được lưu trong bộ nhớ phục vụ chạy một instance. Khi triển khai nhiều instance cần kho nonce dùng chung (ví dụ Redis); khởi động lại giữa chừng thì mở lại hộp thoại đăng nhập.

Tài liệu Google: https://developers.google.com/identity/gsi/web/guides/get-google-api-clientid và https://developers.google.com/identity/gsi/web/guides/verify-google-id-token.
