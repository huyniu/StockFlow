# Bản đồ chi nhánh bằng Google Maps — không cần API key

## Cách sử dụng

Bấm **Chi nhánh phục vụ** ở đầu trang. Bấm một chi nhánh để xem bản đồ ngay trong website, sau đó **Chọn chi nhánh này** để áp dụng cho giỏ hàng, kiểm tra hàng và tính lại phí vận chuyển bằng luồng hiện có. Lựa chọn được giữ cùng giỏ khi tải lại trang. X, Escape hoặc bấm ngoài hộp sẽ giữ nguyên chi nhánh đang dùng. Liên hệ và footer mở cùng hộp chọn.

- Bản đồ trong trang nhúng giao diện Google Maps công khai theo tọa độ database: `https://www.google.com/maps?q=LAT,LON&z=15&hl=vi&output=embed`.
- **Mở Google Maps** mở bản đồ đầy đủ tại tọa độ đó.
- **Chỉ đường trên Google Maps** mở hướng dẫn đến chi nhánh.
- Không dùng Maps Embed API v1, không cần API key, tài khoản Google Cloud, billing hoặc thẻ thanh toán cho cách nhúng hiện tại. Không gọi Places, Routes hoặc Geocoding API.

Website chỉ chuyển tọa độ công khai của điểm demo/chi nhánh cho Google; không đọc GPS, không chuyển địa chỉ người nhận hoặc token đăng nhập. Bản đồ không tính phí giao hàng GHN. iframe chỉ tải khi mở hộp và bỏ `src` khi đóng. Giữ nguyên logo/bản quyền/điều khoản Google trong bản đồ. Màu hộp chọn theo theme của web; phần địa lý do Google hiển thị.

Cách dùng mã nhúng Google Maps khi cần cấu hình thủ công: mở vị trí trên Google Maps → **Chia sẻ → Nhúng bản đồ → Sao chép HTML**. [Hướng dẫn Google](https://support.google.com/maps/answer/7101463?hl=vi). Dự án hiện tạo URL nhúng theo tọa độ để điểm ghim cập nhật cùng dữ liệu, không phải lưu mã HTML cố định cho từng chi nhánh. Các tham số URL giao diện công khai không phải hợp đồng API Maps Platform; nếu Google thay đổi cách nhúng thì cần kiểm tra/cập nhật URL này hoặc dùng mã nhúng từ chức năng Chia sẻ.

## Localhost

1. Khởi động **StockflowApplication** với cấu hình database/JWT hiện có.
2. Mở `http://localhost:8080`, Ctrl+F5 và bấm **Chi nhánh phục vụ**.
3. Chọn từng chi nhánh để xem điểm ghim, thử nút mở Google Maps và chỉ đường. Xác nhận chi nhánh để áp dụng cho mua sắm.

Không cần nhập key hay hoàn tất màn hình thanh toán Google Cloud đã mở trước đó. Bản sửa đã bỏ endpoint/cấu hình `map-config`, `GOOGLE_MAPS_EMBED_API_KEY` và import file key riêng. Các key Gemini, Google đăng nhập và JWT giữ nguyên cấu hình của chúng.

Cần Internet truy cập Google Maps. Nếu bản đồ tải chậm, dùng **Tải lại bản đồ** hoặc **Mở Google Maps**. Chi nhánh không có tọa độ công khai hoặc API vị trí lỗi vẫn có thể chọn để mua sắm.

## Render

Chưa commit/push/deploy hoặc cập nhật database Render trong lượt này. Bản sửa local không tự xuất hiện trên Render.

Khi chủ hệ thống triển khai phiên bản mới, **không cần thêm biến môi trường Maps** hoặc đăng ký thanh toán. Mở đúng URL dịch vụ StockFlow, Ctrl+F5 và kiểm tra cả ba bản đồ, chọn/xác nhận chi nhánh và nút chỉ đường.

Không có migration mới cho lần chuyển sang nhúng miễn key. Nếu Render chưa có chức năng bản đồ trước đó, Flyway V34/V35 sẽ chạy khi triển khai phiên bản chứa chúng. Local và Render có thể dùng database khác nhau; sửa tọa độ local không tự đồng bộ sang Render. Nếu bổ sung CSP thì cần cho phép iframe Google Maps tại `https://www.google.com`.

## Dữ liệu và phạm vi

- V34 tạo bảng `storefront_map_locations` và ba vị trí demo Hà Nội/Đà Nẵng/TP.HCM. Đây là điểm minh họa, không phải cửa hàng thật; không dùng để nhận/gửi hàng thực tế.
- V35 liên kết theo `warehouse_code` với WH-HAN-01, WH-DAD-01, WH-SGN-01. Dịch vụ đọc tra kho ACTIVE và trả `warehouse_id` thật, không giả định ID kho bằng ID điểm bản đồ.
- `GET /api/v1/storefront/locations` chỉ trả điểm `visible=true` với `id`, `code`, `name`, `address`, `latitude`, `longitude`, `demo`, `warehouse_id`. Địa chỉ kho nội bộ không được trả ra.
- `GET /api/v1/storefront/branches` trả tên điểm bản đồ đã liên kết (vd. "StockFlow Hà Nội") thay cho tên kho nội bộ ("Kho Tổng Hà Nội"); kho chưa có điểm bản đồ giữ tên kho. Trang quản trị và API vận hành vẫn dùng tên kho.
- Danh sách chọn lấy từ API chi nhánh ACTIVE hiện có. Xem trước chỉ đổi bản đồ, xác nhận mới đổi chi nhánh của giỏ hàng. Chưa có chi nhánh ACTIVE thì nút xác nhận bị vô hiệu hóa.
- Không thay đổi tài khoản/kho, tồn kho, đơn hàng, lịch sử, phân công hoặc địa chỉ kho dùng tính cước. Không sửa migration V32/V34/V35 đã áp dụng, không reset dữ liệu lúc khởi động.
- Chủ hệ thống hiện sửa tên/địa chỉ công khai/tọa độ/visibility/liên kết kho trong bảng riêng trên đúng database. Chưa thêm màn hình quản trị vị trí. Giữ `demo=true` đến khi xác minh cửa hàng thật.

## Kiểm chứng

Ngày 16/10/2026:

- 46 kiểm thử backend PASS trên H2: StorefrontMapIntegrationTest, StorefrontApiIntegrationTest, DemoDataIntegrationTest, OrderIdempotencyIntegrationTest và OpenApiIntegrationTest. Không phải toàn bộ suite PostgreSQL/Testcontainers.
- Build IntelliJ PASS. Fixture giao diện PASS ở 320/375/768/1366px với theme sáng/tối: xem trước/hủy/xác nhận, ID kho thật khác ID bản đồ, giữ giỏ và lưu lựa chọn, bàn phím, tương phản, lỗi API/thử lại, thiếu tọa độ và tên điểm chứa HTML.
- Đã khởi động local bằng cấu hình IntelliJ hiện có, API trả ba điểm với ID kho 1/2/3. Kiểm tra trên localhost với Google Maps thực đã hiển thị cả ba bản đồ và điểm ghim mà không có API key; đã xem ảnh desktop và mobile. Log `target/google-maps-key-free-local-live.log`. Fixture kiểm tra nghiệp vụ riêng chặn toàn bộ mạng HTTPS bên ngoài.

```powershell
node scripts/build-storefront.cjs --check
node scripts/verify-storefront-ui.cjs --store-map-only
# App giả chỉ phục vụ kiểm thử + bản đồ Google thật, không cần database.
node scripts/verify-storefront-ui.cjs --store-map-provider-only
# Kiểm tra app localhost, chặn mạng ngoài.
node scripts/verify-storefront-ui.cjs --store-map-local
# Đọc app localhost + kiểm tra Google Maps thật cho từng chi nhánh.
node scripts/verify-storefront-ui.cjs --store-map-live
```

Log trong `target/google-maps-key-free-*.log`. Ảnh bản đồ thực trong `target/ui-verification/google-maps-key-free-*.png`; ảnh chỉ kiểm tra app giả hoặc phiên bản bản đồ cũ không thay thế bằng chứng localhost hiện tại.
