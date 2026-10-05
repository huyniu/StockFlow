# Xác thực email đăng ký

V18 đánh dấu toàn bộ user đã tồn tại là `email_verified=true`; user mới phải xác thực OTP trước khi đăng nhập. Bốn tài khoản demo được seed với email đã xác thực.

- `POST /api/v1/auth/register`: `{email,password,full_name}` → HTTP 201, `{email,requires_verification:true,message}`; không phát JWT.
- `POST /api/v1/auth/verify-email`: `{email,otp}` → HTTP 200 và response JWT như login. Chỉ mã mới nhất, chưa dùng và còn hạn 15 phút được chấp nhận.
- `POST /api/v1/auth/resend-otp`: `{email}` → HTTP 200; gửi trước 60 giây trả 429. Mã cũ mất hiệu lực khi gửi lại.
- `POST /api/v1/auth/login`: mật khẩu đúng nhưng email chưa xác thực → HTTP 403, `{error:"EMAIL_NOT_VERIFIED",email}`.

Storefront mở modal OTP sau đăng ký hoặc lỗi login chưa xác thực; sau verify lưu session JWT và giữ giỏ hàng khách vãng lai.

Mail được gửi sau commit qua executor `mailTaskExecutor` (core 2, max 5). `application.yml` cấu hình Gmail SMTP `smtp.gmail.com:587`, SMTP auth và STARTTLS. Cung cấp `MAIL_USERNAME` và `MAIL_PASSWORD` trong môi trường chạy ứng dụng; địa chỉ gửi mặc định là `MAIL_USERNAME`, có thể đổi qua `APP_MAIL_FROM`. Không lưu mật khẩu SMTP vào Git.

Service hiện tại tên là `EmailService` (không có `EmailServiceImpl`). `sendVerificationOtp()` lấy bean `JavaMailSender`, tạo `SimpleMailMessage` và gọi `sender.send(message)` để gửi email thật. Console luôn in `[EMAIL_OTP]` theo yêu cầu. Profile `test`/`postgres-test` không bật cấu hình Gmail, không yêu cầu credential và dùng console OTP để tránh gửi email thật trong test.

Kiểm tra: `.\mvnw.cmd "-Dmaven.repo.local=C:/Users/Admin/.m2/repository" test`. Test H2 có bản migration V18 riêng; PostgreSQL dùng migration main và profile `postgres-tests`.
