# Xác thực email đăng ký

V18 đã đánh dấu user tồn tại lúc migration là `email_verified=true`; user mới phải xác thực OTP trước khi đăng nhập. Không sửa lại V18. V31 chỉ vô hiệu hóa OTP cũ, không thay trạng thái xác thực của user. V32 vô hiệu hóa các tài khoản quản trị demo có credential công khai; demo khách hàng vẫn có quyền CUSTOMER.

- `POST /api/v1/auth/register`: `{email,password,full_name}` → HTTP 201, `{email,requires_verification:true,message}`; không phát JWT.
- `POST /api/v1/auth/verify-email`: `{email,otp}` → HTTP 200 và response JWT như login. Chỉ mã mới nhất, chưa dùng và còn hạn 15 phút được chấp nhận.
- `POST /api/v1/auth/resend-otp`: `{email}` → HTTP 200; gửi trước 60 giây trả 429. Mã cũ mất hiệu lực khi gửi lại.
- `POST /api/v1/auth/login`: mật khẩu đúng nhưng email chưa xác thực → HTTP 403, `{error:"EMAIL_NOT_VERIFIED",email}`.

Từ V31, database chỉ lưu `otp_hash` BCrypt, `attempts` và `invalidated_at`, không còn cột `otp_code`. Năm lần sai làm mã bị vô hiệu hóa; nhập đúng sau đó cũng bị từ chối. Verify và resend khóa cùng user (`PESSIMISTIC_WRITE`) nên request đồng thời được tuần tự hóa. `verifyEmail()` dùng `noRollbackFor=BadRequestException.class` để HTTP 400 không rollback bộ đếm sai. Mã mới vẫn sống 15 phút, chỉ mã mới nhất được chấp nhận, thời gian chờ resend vẫn là 60 giây. Mỗi user được phát tối đa 5 mã trong một giờ (tính cả mã đăng ký đầu tiên); gửi lại sau khóa không tự xác thực user.

Backend giới hạn verify: 30 request/IP/phút và 10 request/email/phút; resend: 10 request/IP/phút và 5 request/email/phút. Email được chuẩn hóa, giới hạn IP/email dùng bộ đếm đồng bộ có tối đa 8192 key trong một tiến trình; khi đầy trả 429 thay vì loại bỏ bucket còn hoạt động. Giới hạn phút không chia sẻ giữa nhiều instance và mất khi restart. Giới hạn 5 lần sai và 5 mã/giờ trong database vẫn có hiệu lực giữa các instance; nếu triển khai nhiều instance, cần bổ sung rate limit dùng chung tại reverse proxy hoặc Redis.

`server.forward-headers-strategy=none` giữ IP socket gốc. Mặc định bỏ qua mọi header IP do client gửi. Chỉ đặt `AUTH_TRUSTED_PROXY_CIDRS` khi biết đúng CIDR của proxy và đã xác minh proxy ghi đè/thêm IP thực vào `X-Forwarded-For`. Resolver đọc chuỗi từ phải sang trái và dừng ở IP không đáng tin đầu tiên; không dùng `Forwarded`/`X-Real-IP` hoặc DNS. Không cấu hình `0.0.0.0/0`, `::/0` hoặc đoán IP Render. Khi chưa có allowlist, người dùng qua cùng proxy có thể chung quota IP; giới hạn theo email vẫn độc lập.

Storefront mở modal OTP sau đăng ký hoặc lỗi login chưa xác thực; sau verify lưu session JWT và giữ giỏ hàng khách vãng lai.

Mail được gửi sau commit qua executor `mailTaskExecutor` (core 2, max 5). `application.yml` cấu hình Gmail SMTP `smtp.gmail.com:587`, SMTP auth và STARTTLS. Cung cấp `MAIL_USERNAME` và `MAIL_PASSWORD` trong môi trường chạy ứng dụng; địa chỉ gửi mặc định là `MAIL_USERNAME`, có thể đổi qua `APP_MAIL_FROM`. Không lưu mật khẩu SMTP vào Git.

Service hiện tại tên là `EmailService` (không có `EmailServiceImpl`). `sendVerificationOtp()` gọi `MailDeliveryService`: `MAIL_PROVIDER=smtp` sử dụng `JavaMailSender`, `MAIL_PROVIDER=brevo` gọi API HTTPS. Không ghi OTP hoặc nội dung mail vào log, kể cả khi gửi lỗi. Profile `test`/`postgres-test` không bật Gmail/Brevo thật; integration test lấy mã từ mail mock. Xem [cấu hình email trên Render](email-delivery-render.md).

Kiểm tra: `.\mvnw.cmd "-Dmaven.repo.local=C:/Users/Admin/.m2/repository" test`. Test H2 có bản migration V18 riêng; PostgreSQL dùng migration main và profile `postgres-tests`.
