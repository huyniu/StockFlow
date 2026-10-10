# Đăng nhập từ giỏ, quyền demo và OTP — 10/10/2026

Đã đối chiếu bản rà soát `web-audit-20261010/REVIEW.md` với implementation hiện tại. Phạm vi chỉ sửa ba mục P1; không đổi giá, kho, reservation, thanh toán, idempotency, đơn hàng hoặc thiết kế checkout/bộ lọc. Kiểm thử dùng H2, PostgreSQL QA riêng và API giả lập; chưa cập nhật dữ liệu hoặc cấu hình Render.

## Đăng nhập từ giỏ

Nguyên nhân: `#create-order` là submit trong form nhận hàng có `required`; native validation chặn trước khi có submit event. Listener `navigation-events.js` còn gọi `reportValidity()` trước handler.

Nút khách vãng lai dùng `type=button` và action `checkout-login`; listener submit cũng kiểm tra khách vãng lai trước `reportValidity()`. Sau đăng nhập, flow `intent=checkout` hiện có khôi phục giỏ và mở lại checkout. Khi đã đăng nhập, nút trở lại `type=submit`, giữ native validation và validation trong listener/service. Không dùng `novalidate` để bỏ qua thông tin giao hàng.

Regression Chrome tái hiện trước sửa: bấm đăng nhập làm phát sinh bốn sự kiện invalid và test thất bại. Sau sửa kiểm tra 375/1366 px: không validate địa chỉ trước login, giỏ giữ hai SKU, số lượng và trạng thái chọn; sau login thiếu họ tên vẫn bị chặn và không có request tạo đơn. Các screenshot dùng fixture cục bộ, không phải ảnh/dữ liệu Render.

## Tài khoản demo công khai

Nguyên nhân: các nút demo dùng credential công khai để xin JWT với quyền vận hành thật. Chọn cách tắt quyền vận hành công khai ở backend, thay vì cho demo đọc dữ liệu khách thật.

- V32 chuyển `admin@stockflow.com`, `manager@stockflow.com`, `staff.hn@stockflow.com` thành INACTIVE và tăng `auth_version`. Giữ user, phân công kho và ledger lịch sử; không xóa hoặc đổi chủ sở hữu dữ liệu.
- `DemoAccountPolicy` chặn ba định danh này trong login, verify và Google login, kể cả khi ai đó bật lại trạng thái. JWT filter bỏ qua các token của chúng, gồm token có chữ ký/auth_version hợp lệ. Seeder không kích hoạt lại.
- Demo `customer@stockflow.com` chỉ được xác thực nếu role vẫn là CUSTOMER. Đổi role thành quyền vận hành không mở quyền công khai. UI chỉ còn demo khách hàng; tài khoản riêng giữ nguyên phân quyền.
- Bốn địa chỉ demo được dành riêng, không đăng ký qua API kể cả khi không bật profile demo; tránh tạo tài khoản không thể xác thực hoặc bị seeder nhận nhầm là demo khi đổi môi trường.
- Không giữ khóa ký JWT demo công khai: demo profile kế thừa cấu hình secret bắt buộc, Compose yêu cầu biến riêng, `.env.example` để trống; `JwtTokenProvider` từ chối khóa demo từng công khai. Khóa JWT và mật khẩu là hai loại khác nhau: chỉ đổi mật khẩu admin không thay thế việc đổi khóa ký đã lộ.

Không dùng tài khoản khách demo dùng chung để lưu địa chỉ/đơn có dữ liệu cá nhân. Nếu cần demo ADMIN/nhân viên cho người ngoài, triển khai một môi trường/database riêng chỉ có fixture; không mở lại các tài khoản công khai trong môi trường khách thật.

## OTP đăng ký

V31 thay mã rõ bằng BCrypt hash, thêm `attempts`, `invalidated_at`, giới hạn 5 lần sai/mã. Khóa cùng user tuần tự hóa verify/resend; transaction verify không rollback khi trả `BadRequestException`, nên số lần sai được commit trước HTTP 400. Mã bị khóa không dùng được kể cả nhập đúng; gửi lại theo giới hạn tạo mã mới với bộ đếm mới, không đổi `email_verified`.

Giữ hạn 15 phút, chỉ mã mới nhất và chờ resend 60 giây. Tối đa 5 mã/user/giờ trong database (bao gồm mã đầu khi đăng ký). Rate limit mỗi tiến trình: verify 30/IP/phút, 10/email/phút; resend 10/IP/phút, 5/email/phút. Bộ đếm có khóa đồng bộ và giới hạn bộ nhớ; không tin header IP từ client. Chi tiết và giới hạn nhiều instance: [email-verification.md](email-verification.md).

Mail chứa OTP vẫn được gửi sau commit; không ghi mã/email hoặc exception chứa payload mail vào log ứng dụng. Test lấy mã từ mail mock, không cần tra mã rõ trong database.

## Trước khi deploy lên Render

1. **Chuẩn bị một tài khoản ADMIN riêng trước khi khóa demo.** Đăng ký/xác thực email bằng flow bình thường. Nếu đã có ADMIN riêng hoạt động, giữ tài khoản đó. API cấp quyền hiện chỉ hỗ trợ CUSTOMER/MANAGER/WAREHOUSE_STAFF, không cấp ADMIN. Chủ database có thể cấp ADMIN cho đúng tài khoản đã xác thực bằng SQL giới hạn theo email dưới đây; không chạy lên mọi user và không đặt `email_verified=true` để bỏ qua OTP. Trong lượt sửa này chưa thực hiện SQL trên Render.

```sql
BEGIN;
SELECT id, email, status, email_verified
FROM users WHERE email = 'EMAIL_ADMIN_RIENG_DA_XAC_THUC' FOR UPDATE;

UPDATE users
SET role_id = (SELECT id FROM roles WHERE name = 'ADMIN'),
    auth_version = auth_version + 1
WHERE email = 'EMAIL_ADMIN_RIENG_DA_XAC_THUC'
  AND email_verified = TRUE AND status = 'ACTIVE'
  AND LOWER(email) NOT IN ('admin@stockflow.com', 'manager@stockflow.com',
                          'staff.hn@stockflow.com', 'customer@stockflow.com')
RETURNING id, email;
COMMIT;
```

Thay placeholder bằng email thật, kiểm tra SELECT và UPDATE đúng **một tài khoản**; nếu sai hoặc không có hàng cập nhật thì ROLLBACK thay cho COMMIT. Đăng nhập lại bằng tài khoản riêng và kiểm tra quyền ADMIN trước deploy. Không dùng một địa chỉ demo cho tài khoản riêng.

2. Đặt `JWT_SECRET` riêng trong Render Environment, ít nhất 32 byte ngẫu nhiên. Nếu từng dùng khóa mặc định/công khai, tạo khóa mới; đổi khóa buộc tất cả phiên JWT cũ đăng nhập lại. Không dán secret vào source/Git/chat. Local/Compose cũng cần khóa riêng; test có khóa test riêng.

Tạo khóa trên PowerShell rồi sao chép trực tiếp vào ô biến môi trường của bạn:

```powershell
$keyBytes = New-Object byte[] 48
$keyGenerator = [Security.Cryptography.RandomNumberGenerator]::Create()
$keyGenerator.GetBytes($keyBytes)
$keyGenerator.Dispose()
[Convert]::ToBase64String($keyBytes)
```

3. `SERVER_FORWARD_HEADERS_STRATEGY=none` (đã là mặc định trong application.yml). Để `AUTH_TRUSTED_PROXY_CIDRS` trống nếu chưa xác minh peer CIDR/proxy xử lý header của Render. Không dùng wildcard hoặc tin trực tiếp `X-Forwarded-For` từ Internet. Khi để trống, mọi khách qua cùng proxy có thể chung quota IP; cần xác minh topology trước khi điền allowlist. Không cần thay khóa Gmail/GHN/VNPay vì các sửa này.

4. Commit/push và deploy sau khi cấu hình/tài khoản riêng sẵn sàng. Flyway chạy **V31 và V32 mới**; giữ nguyên mọi migration đã áp dụng. V31 xóa mã rõ và vô hiệu hóa OTP cũ; user đã xác thực không bị đổi trạng thái, user đang chờ xác thực yêu cầu mã mới sau khoảng chờ còn lại. V32 chặn quyền vận hành demo. Không xóa bảng/đơn hoặc dùng Flyway clean trên Render.

5. Sau deploy kiểm tra health/log migration, đăng nhập bằng ADMIN riêng và flow đăng ký/email trên môi trường phù hợp. Lượt này không tạo đơn, dò mã hoặc gửi mail hàng loạt trên website live. Môi trường nhiều instance cần rate limiter dùng chung tại proxy/Redis; các giới hạn lưu DB vẫn dùng chung.

## File thay đổi

- Frontend: `assets/fragments/dialogs.html`, `chrome.html`; `assets/modules/auth-profile.js`, `navigation-events.js`, `core.js`; `scripts/verify-storefront-ui.cjs`.
- Backend: `AuthController`, `AuthService`, `EmailService`, `GoogleAuthService`, `EmailVerificationToken`, `EmailVerificationTokenRepository`, `JwtAuthenticationFilter`, `JwtTokenProvider`, `DemoDataSeeder`; thêm `DemoAccountPolicy`, `RegistrationOtpRateLimiter`, `ClientIpResolver`.
- Cấu hình: `application.yml`, `application-demo.yml`, `compose.yaml`, `.env.example`; V31/V32 ở cả main/test migration.
- Test mới: `EmailVerificationSecurityIntegrationTest`, `EmailServiceSecurityTest`, `PublicDemoAccessIntegrationTest`, `AuthHardeningMigrationTest`, `ClientIpResolverTest`, `RegistrationOtpRateLimiterTest`, `JwtSecretConfigurationTest`, `EmailVerificationPostgresIT`; helper `VerificationOtpMail`.
- Giữ số ca cũ, cập nhật fixture/assertion trong `AuthIntegrationTest`, `CatalogAuthorizationIntegrationTest`, `EmailVerificationIntegrationTest`, `PasswordResetIntegrationTest`, `DemoDataIntegrationTest`; thêm một ca Google login trong `GoogleAuthIntegrationTest`.
- Tài liệu: file này, `email-verification.md`, `frontend-development.md`, README.

Không sửa CSS nguồn/bundle. `build-storefront --check` kiểm tra bundle hiện có vẫn đồng bộ.

## Kết quả kiểm chứng

Đã chạy các kiểm tra sau trên workspace ngày 10/10/2026:

- Maven `test package`: **828/828 PASS, 0 failures, 0 errors, 0 skipped**. Giữ 793 ca cũ, bổ sung 35 ca bảo mật. JAR Spring Boot đóng gói thành công. Log: `target/auth-hardening-final.log`.
- `verify -Ppostgres-tests` với hai database QA mới, riêng trên loopback (PostgreSQL **18.3**): **13/13 PASS**, gồm 4 ca OTP/migration production và 9 ca reservation/ledger/idempotency đã có. Log: `target/auth-hardening-postgres.log`. Đã dừng PostgreSQL QA sau chạy; không dùng database local đang vận hành hoặc Render. CI mặc định dùng Testcontainers PostgreSQL 17; lần này dùng binary PostgreSQL 18 cục bộ, chưa chạy container 17.
- Bộ liên quan ban đầu **88/88 PASS**; kiểm tra bổ sung bằng địa chỉ riêng trong H2 **20/20 PASS**, chứng minh demo không đọc/xóa được địa chỉ khách khác. Log: `target/auth-hardening-focused.log`, `target/auth-hardening-private-data.log`. Sau đó suite cuối 828 cũng chạy lại các ca này và bốn ca email demo dành riêng.
- `node scripts/verify-storefront-ui.cjs`: PASS. Luồng guest login/giữ SKU, số lượng, trạng thái chọn/validation sau login ở **375 và 1366 px**; bộ sẵn có kiểm tra **320/375/768/1366 px**, sáng/tối, login/recovery/checkout/GHN/address book/returns/admin, và danh mục sau cuộn đến 1920 px. Chrome dùng API giả lập và chặn tài nguyên HTTPS ngoài, không gửi mail hay tạo đơn thật. Log: `target/auth-hardening-ui.log`.
- Regression guest checkout đã FAIL trên implementation trước sửa vì phát sinh native validation, rồi PASS sau sửa. Ảnh: `target/ui-verification/checkout-login-375.png`, `checkout-return-375.png`, `checkout-login-1366.png`, `checkout-return-1366.png`. Dữ liệu sản phẩm/ảnh là fixture; chưa kiểm chứng trên điện thoại thật hoặc Safari.
- `node scripts/build-storefront.cjs --check`, `node --check scripts/verify-storefront-ui.cjs`, `git diff --check`: PASS. Không đổi CSS nên không sinh lại bundle khác nội dung.

Chưa deploy hoặc đổi cấu hình/dữ liệu live Render; chưa kiểm chứng gửi OTP thật sau deploy. Giới hạn phút theo IP/email còn là in-memory từng instance; triển khai nhiều instance cần limiter chia sẻ như đã nêu. Không coi lần chạy này là chứng nhận bảo mật toàn bộ hệ thống.
