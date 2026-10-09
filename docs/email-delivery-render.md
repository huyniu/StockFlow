# Gửi email trên Render và tự chạy test trước khi deploy

StockFlow có hai cách gửi: Gmail SMTP cho máy local và Brevo API qua HTTPS cho Render. Cả email xác thực OTP, đặt lại mật khẩu, thông báo đổi mật khẩu và thông báo đơn hàng đều sử dụng cùng cấu hình.

Render Free chặn kết nối ra ngoài qua cổng SMTP 25, 465 và 587. Đổi mật khẩu Gmail không khắc phục giới hạn này. Brevo được gọi qua HTTPS, không dùng cổng SMTP. Tham khảo [giới hạn Render Free](https://render.com/docs/free#other-limitations) và [API gửi email Brevo](https://developers.brevo.com/reference/send-transac-email).

## 1. Chuẩn bị Brevo

1. Tạo tài khoản tại [Brevo](https://www.brevo.com/) và hoàn tất các bước xác thực/duyệt tài khoản nếu được yêu cầu.
2. Vào **Settings → Senders, Domains, IPs → Senders → Add a sender**. Đặt tên `StockFlow`, nhập email người gửi mà bạn sở hữu. Bấm Save rồi nhập mã xác thực gửi đến hộp thư đó.
3. Vào **Settings → SMTP & API → API Keys & MCP → Generate a new API key**. Đặt tên `StockFlow Render`, sao chép khóa API vào cấu hình Render. Chọn khóa API thông thường; không chọn SMTP key hoặc MCP key.

Gmail có thể được thêm làm sender nhưng tên miền miễn phí không xác thực DKIM/DMARC được. Khi vận hành cửa hàng thật, dùng email thuộc tên miền của bạn và xác thực tên miền theo hướng dẫn Brevo để tăng khả năng thư vào Inbox. Việc sender đã xác thực không đồng nghĩa email luôn vào Inbox; cần kiểm tra Spam và log giao thư.

Hướng dẫn chính thức: [tạo và xác thực sender](https://help.brevo.com/hc/en-us/articles/208836149-Create-a-new-sender-From-name-and-From-email), [tạo API key](https://help.brevo.com/hc/en-us/articles/209467485-Create-and-manage-your-API-keys).

## 2. Điền Environment trên Render

Mở dịch vụ StockFlow → **Environment**. Thêm hoặc sửa:

```env
MAIL_PROVIDER=brevo
BREVO_API_KEY=THAY_BANG_API_KEY_VUA_TAO
APP_MAIL_FROM=THAY_BANG_EMAIL_SENDER_DA_XAC_THUC
MAIL_SENDER_NAME=StockFlow
```

`MAIL_SENDER_NAME` có thể bỏ qua vì mặc định là `StockFlow`. Ba biến đầu cần có; thay phần `THAY_BANG_...` bằng giá trị thật. Điền tên biến vào cột KEY và giá trị vào cột VALUE. Khóa chỉ đặt trong Environment, không đưa vào Git, ảnh chụp hay chat.

Với `MAIL_PROVIDER=brevo`, StockFlow không sử dụng `MAIL_USERNAME` và `MAIL_PASSWORD` để gửi email. Máy local muốn tiếp tục dùng Gmail thì đặt `MAIL_PROVIDER=smtp` cùng hai biến SMTP như trước. Chế độ `MAIL_PROVIDER=console` không gửi thư; OTP đăng ký vẫn in console theo chế độ demo hiện có.

Bấm **Save, rebuild, and deploy** sau khi bản code mới đã lên GitHub. Đăng ký bằng một địa chỉ email khác mà bạn có thể kiểm tra hoặc thử quên mật khẩu với tài khoản đã có. Kiểm tra Inbox/Spam và **Transactional → Logs** trong Brevo. Thử một đơn hàng và kiểm tra email sau khi xác nhận/giao hàng để kiểm chứng thông báo đơn hàng.

## 3. Cách code xử lý lỗi

- `MailProperties`: đọc `MAIL_PROVIDER`, `BREVO_API_KEY`, `APP_MAIL_FROM`, `MAIL_SENDER_NAME`; loại bỏ khoảng trắng thừa và không hiển thị khóa trong `toString()`.
- `MailDeliveryService`: gọi `POST https://api.brevo.com/v3/smtp/email`, header `api-key`, JSON `sender/to/subject/textContent`. Giới hạn thời gian kết nối 5 giây, đọc 10 giây. Chỉ coi API chấp nhận thư khi nhận được `messageId` hợp lệ. Việc API chấp nhận chưa chứng minh người nhận đã nhận thư.
- `EmailService`: giữ executor bất đồng bộ và các API đăng ký/quên mật khẩu hiện tại. Nếu gửi thất bại, ghi lỗi để kiểm tra; khách có thể dùng chức năng gửi lại OTP hiện có. Không tự chuyển sang SMTP khi Brevo bị lỗi.
- `OrderMailDispatcher`: chỉ đánh dấu thư đã gửi sau khi dịch vụ chấp nhận. Khi lỗi, thư vẫn ở outbox, chờ ít nhất 5 phút trước khi thử lại, tối đa 5 lần. Thiếu API key/email sender thì không tiêu hao số lần thử. Cơ chế lease chống hai instance gửi cùng lúc vẫn được giữ; nếu app dừng ngay sau khi dịch vụ nhận thư nhưng trước khi lưu `sent_at`, lần thử lại vẫn có thể gửi trùng.

Log `[MAIL] Provider: brevo` xác nhận cách gửi đang chọn. `Brevo requires...` nghĩa là thiếu cấu hình; `Brevo rejected email: HTTP 401/403` thường cần kiểm tra API key/quyền/tài khoản/sender, `429` cần kiểm tra hạn mức. Log không chứa API key, mật khẩu hay nội dung lỗi trả về từ nhà cung cấp. Console OTP đăng ký vẫn được giữ theo yêu cầu demo trước đây.

## 4. GitHub Actions và Render

`.github/workflows/ci.yml` chạy trên **master và main** khi push hoặc mở pull request, và có nút **Run workflow** chạy thủ công. Workflow chạy toàn bộ test Maven, kiểm tra PostgreSQL bằng Docker, đóng gói JAR và lưu báo cáo test kể cả khi có lỗi.

Sau khi push, mở GitHub → **Actions → Java CI**, chờ dấu tích xanh. Để Render chỉ tự deploy commit đã qua kiểm tra, mở dịch vụ → **Settings → Build & Deploy → Auto-Deploy → After CI Checks Pass → Save**. Đây là cấu hình trên Render, sửa workflow trong Git không tự bật lựa chọn này. Xem [tài liệu deploy Render](https://render.com/docs/deploys).

Test dùng HTTP giả lập và database H2 riêng, không gửi email thật. Hai profile `test`/`postgres-test` không dùng khóa Brevo từ môi trường máy. Kiểm tra local:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=C:/Users/Admin/.m2/repository" test
```

Kiểm chứng ngày 09/10/2026: **791 test PASS, 0 failures, 0 errors, 0 skipped**, gồm 765 test trước đây và 26 test mới cho cấu hình mail, HTTP payload/lỗi và outbox. Năm trường hợp của `WebDemoIntegrationTest.dashboardIsPublic` vẫn được giữ, cập nhật assertion để nhận diện logo/header gọn hiện tại thay vì dòng chữ cũ đã bỏ. YAML CI được đọc bằng SnakeYAML và xác nhận đủ trigger/command. Chưa gửi email thật qua Brevo khi chưa có API key/sender của chủ dự án; chưa chạy workflow trên GitHub hoặc PostgreSQL Testcontainers trên máy local vì không có Docker trong PATH.
