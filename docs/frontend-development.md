# Phát triển giao diện StockFlow

Giao diện dùng Native ES Modules: trình duyệt tải trực tiếp `app.js` và các module trong `src/main/resources/static/assets/modules/`. Maven/Docker không cần Node, Vite hay Webpack.

- `app.js`: tải fragment HTML, đăng ký chức năng và khởi tạo theo thứ tự.
- `core.js`, `context.js`: trạng thái phiên, API, quyền và hàm dùng chung. Registry nội bộ giữ tương thích giữa các chức năng đã có, không đưa trạng thái lên `window`.
- `auth-profile.js`: đăng nhập, Google và hồ sơ.
- `catalog-navigation.js`, `product-detail.js`: danh mục, bộ lọc, chi tiết sản phẩm.
- `cart-checkout.js`: giỏ hàng, địa chỉ GHN, cước phí, tạo đơn.
- `orders-fulfillment.js`: lịch sử, thanh toán, vận đơn.
- `admin.js`: kho, báo cáo, catalog, tài khoản.
- `navigation-events.js`: điều hướng và sự kiện.
- `account-services.js`: sổ địa chỉ, chọn địa chỉ khi checkout, đổi mật khẩu.
- `after-sales.js`: yêu cầu đổi/trả, ảnh minh chứng và duyệt yêu cầu.

`index.html` là shell. Các vùng giao diện nằm trong `assets/fragments/`: `chrome`, `auth-page`, `icons`, `storefront`, `dashboard`, `dialogs`, `aftercare`. Fragment được thay vào DOM trước khi khởi tạo, không thêm wrapper làm thay đổi layout. Khi tải lỗi, shell hiển thị thông báo và nút tải lại.

`app.js` khởi tạo module `assets/login-artwork.js` sau khi tải fragment đăng nhập. Khối 3D dùng Three.js đã lưu trong dự án, tự xoay liên tục và nghiêng theo vị trí chuột. Bản CSS dự phòng cũng tự xoay khi WebGL không khả dụng. Chế độ giảm chuyển động của hệ điều hành giữ hình tĩnh; WebGL dừng khi trang đăng nhập bị ẩn.

`compact-header.js` khởi tạo sau khi tải fragment: thu gọn tài khoản demo vào menu trên điện thoại và theo dõi chiều cao header để đặt gợi ý tìm kiếm đúng vị trí. `frontend/css/compact-header.css` gom header thành hai hàng dưới 1050 px, vẫn giữ chọn kho, tìm kiếm và các menu tài khoản/danh mục.

Chạm danh mục mở các hãng và khoảng giá ngay trong menu; desktop vẫn mở phần này khi di chuột. Chọn hãng, khoảng giá hoặc nút “Xem sản phẩm” mới đưa khách đến tiêu đề và kết quả bên dưới header cố định. Bộ lọc thông số/hãng/giá nằm trong `catalog-filters-panel`, mặc định thu gọn dưới 900 px và vẫn mở sẵn trên desktop. Kiểm tra Chrome bao gồm chạm chọn hãng, chọn danh mục từ trang chủ/chi tiết/hồ sơ, mở lại URL đã lọc và danh mục trống.

Sửa JavaScript/HTML trực tiếp tại các file trên. CSS nền được chia trong `frontend/css/`; `frontend/manifest.json` chỉ ghép CSS, không ghi đè entry JavaScript.

```powershell
node scripts/build-storefront.cjs
node scripts/build-storefront.cjs --check
.\mvnw.cmd "-Dmaven.repo.local=C:/Users/Admin/.m2/repository" test
```

`FrontendBundleConsistencyTest` kiểm tra CSS đồng bộ và entry ES Modules. Các helper `assets/password-reset.js`, `shipment-ui.js`, `login-artwork.js`, `theme.js` vẫn có API độc lập. `assets/ui-polish.css` xử lý responsive và màn hình tài khoản.

Kiểm tra Chrome headless với Node 22 trở lên và Chrome đã cài:

```powershell
node scripts/verify-storefront-ui.cjs
```

Script chạy server localhost riêng với API giả lập, không chạm dữ liệu thật hoặc gửi email. Kiểm tra 320/375/768/1366 px, sáng/tối, phục hồi mật khẩu, giỏ hàng/cước GHN, sổ địa chỉ/checkout, đổi mật khẩu, khách gửi yêu cầu đổi trả và quản trị duyệt. Ảnh lưu trong `target/ui-verification/`. Đặt `CHROME_PATH` nếu Chrome ở vị trí khác. Đây là kiểm chứng trình duyệt ở kích thước mobile, không thay thế thử trên điện thoại thật.

V26 lưu hash OTP đặt lại mật khẩu, hạn 15 phút, tối đa 5 lần nhập sai, chống gửi lại trước 60 giây/5 email mỗi giờ. Đổi hoặc đặt lại mật khẩu vô hiệu hóa JWT cũ. Rate limit IP phục hồi hiện nằm trong tiến trình; triển khai nhiều instance cần kho giới hạn dùng chung.

V27 lưu loại vận đơn: `SIMULATED`, `GHN_SANDBOX`, `GHN_PRODUCTION`, `MANUAL`. Chỉ GHN production có link tra cứu; không suy đoán vận đơn cũ là đơn GHN thật.

V28 lưu tối đa 20 địa chỉ/tài khoản, đồng bộ địa chỉ mặc định với API hồ sơ cũ. Đơn đã đặt giữ nguyên thông tin người nhận, không thay đổi theo sổ địa chỉ.

V29 lưu email xác nhận/đang giao/đã giao trong outbox cùng transaction đơn hàng. Dispatcher gửi SMTP mỗi 10 giây, thử tối đa 5 lần, cách lần lỗi 5 phút. Cần `MAIL_USERNAME`, `MAIL_PASSWORD`; có thể tắt bằng `app.mail.order-notifications-enabled=false`. Không gửi thông báo ngược cho đơn cũ. Nếu tiến trình dừng sau gửi SMTP nhưng trước ghi `sent_at`, retry có thể gửi trùng (at-least-once).

V30 lưu yêu cầu đổi/trả toàn bộ đơn đã giao, lý do và tối đa 5 ảnh PNG/JPEG × 2 MB. Ảnh được xử lý lại, chỉ chủ yêu cầu hoặc MANAGER/ADMIN đọc được. Duyệt chưa hoàn kho; xác nhận đã nhận lại hàng mới ghi `RETURN_RESTOCK` qua fulfillment hiện có. Gửi sản phẩm thay thế và hoàn tiền thực tế qua ngân hàng/VNPay cần cửa hàng xử lý riêng.
