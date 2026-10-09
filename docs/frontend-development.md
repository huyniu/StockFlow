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

## Thẻ sản phẩm trên mobile

`productCards()` trong `src/main/resources/static/assets/modules/catalog-navigation.js` tạo thẻ catalog/bán chạy/yêu thích; `configurationPreview()` trong `product-detail.js` cung cấp phần phiên bản/màu. `index.html` chỉ là shell; nút đăng nhập nằm trong fragment `assets/fragments/storefront.html`.

Sửa CSS tên thẻ tại `frontend/css/storefront.css`, liên kết/nút và phần responsive của thẻ tại `frontend/css/product-interactions.css`. File này được ghép sau `responsive.css` theo manifest, nên các rule mobile có thể ghi đè cỡ nút cũ mà không ảnh hưởng nút chi tiết/admin. Sau sửa, chạy build và `--check` như trên; không sửa trực tiếp bundle `styles.css`.

Thẻ giới hạn tên hai dòng, bỏ SKU khỏi kệ khách hàng và dùng nhãn **Chọn phiên bản**. Handler cũ vẫn mở trang chi tiết để chọn phiên bản/màu trước khi thêm SKU vào giỏ. Trang chi tiết và quản trị giữ nguyên tên đầy đủ/mã SKU. Mobile/tablet dưới hoặc bằng 900 px dùng nút 14 px, cao 44 px; màn hình 320 px ẩn dấu cộng trang trí để nhãn không xuống dòng. Desktop giữ kích thước ảnh, số cột, cỡ chữ giá/nút và màu nhận diện. Nút đăng nhập icon có tên truy cập **Đăng nhập hoặc đăng ký**.

Kiểm tra riêng cho thẻ:

```powershell
# --before phục vụ phiên bản HEAD của CSS bundle và các module/fragment liên quan.
node scripts/verify-product-cards.cjs --before
node scripts/verify-product-cards.cjs
```

Script kiểm tra Chrome tại **320/375/414/768/1366 px**, sáng/tối: tràn ngang, tên hai dòng, giá/nhãn nút, chiều cao/vị trí nút cùng hàng, tên trong cây accessibility, mở chi tiết, giữ tên/SKU đầy đủ, chọn 512GB/màu đen rồi thêm đúng SKU/giá vào giỏ. API giả lập chỉ nhận GET; không tạo đơn hay thay đổi tồn kho thật. Ảnh trước/sau và dữ liệu kích thước nằm trong `target/product-card-verification/`. `--before` dùng bản HEAD tại thời điểm chạy, không tự tìm lại bản trước nếu thay đổi đã được commit.

Ảnh dùng sản phẩm mẫu và ảnh logo cục bộ; tài nguyên mạng ngoài bị chặn. Script đo bố cục với chế độ giảm chuyển động, sau đó bật chuyển động bình thường để kiểm tra tất cả thẻ vẫn hiện và tương tác được khi cuộn xuống/lên tại cả năm kích thước, sáng/tối. Kiểm tra thêm mở chi tiết bằng Enter, phản hồi thêm giỏ và phạm vi CSS nền đăng nhập/quản trị so với bản HEAD. Bộ `verify-storefront-ui.cjs` kiểm tra điều hướng đăng nhập và quản trị thực tế. Đây là kiểm tra bằng Chrome headless, chưa thay thế Safari hoặc thiết bị thật.

Kiểm chứng ngày 09/10/2026: kiểm tra thẻ tại cả năm kích thước, sáng/tối và luồng chọn phiên bản/màu/thêm giỏ PASS; bộ kiểm tra Chrome cũ PASS; CSS bundle `--check` PASS; Maven **791 test PASS, 0 failures/errors/skipped**. Không thay đổi backend/API, thương hiệu hoặc nghiệp vụ kho/đặt hàng trong thay đổi thẻ này.

## Tinh gọn cửa hàng

`frontend/css/storefront-polish.css` được ghép cuối manifest, chỉ áp dụng nền trang khi body không có `portal-open` hoặc `auth-page-open`. Nền cửa hàng dùng `--background`; banner dùng `--surface`, chữ dùng `--text`/`--muted`, thao tác chính dùng `--brand`. Các lớp Aura được ẩn riêng ở cửa hàng; nền Arctic Frost của đăng nhập và nền quản trị trong `assets/theme.css` giữ nguyên. Không sửa trực tiếp `styles.css`.

Thẻ sản phẩm không còn nằm trong `storefrontRevealSelector` của `core.js`; các mục còn dùng reveal chỉ xuất hiện một lần. Thẻ đã tải luôn hiển thị, hover nâng 2 px và focus có viền rõ. Chế độ giảm chuyển động bỏ phần nâng thẻ. Tiêu đề mục, hướng dẫn mua hàng, ID liên kết và nội dung dành cho trình đọc màn hình được giữ lại; chỉ bỏ nhãn phụ lặp ý trong cửa hàng.

`cart-checkout.js` gọi `notify` với `banner: false` khi thêm giỏ thành công: giữ toast trong vùng `aria-live="polite"`, số lượng giỏ và phản hồi trên icon, không tạo thêm banner trùng ở đầu trang. Luồng báo lỗi và các thông báo quản trị không đổi.

Các quy tắc nội bộ trước đây đặt dưới kệ được ghi tại đây: sản phẩm chưa có ảnh dùng hình dự phòng theo danh mục; **Giá từ** là giá thấp nhất của các lựa chọn đang bán. Khi đặt đơn, backend kiểm tra lại giá và tồn kho theo SKU được chọn. Việc bỏ đoạn giải thích khỏi kệ không thay đổi các quy tắc này, API, nội dung sản phẩm hay dữ liệu catalog.

Các file của thay đổi này:

- CSS: `frontend/css/storefront-polish.css`, `frontend/css/product-interactions.css`, `frontend/manifest.json`; bundle sinh lại tại `src/main/resources/static/styles.css`.
- HTML: `assets/fragments/storefront.html`, `assets/fragments/dialogs.html`.
- JavaScript: `assets/modules/core.js`, `catalog-navigation.js`, `product-detail.js`, `cart-checkout.js`.
- Kiểm chứng và tài liệu: `scripts/verify-product-cards.cjs`, `docs/frontend-development.md`.

Kiểm chứng bản cuối ngày 09/10/2026: Chrome **320/375/414/768/1366 px**, sáng/tối, cuộn bình thường, focus/Enter, giảm chuyển động, chi tiết/chọn phiên bản/màu/thêm giỏ đều PASS. Nền auth/portal khớp bản HEAD; bộ Chrome cũ cho đăng nhập/checkout/quản trị PASS. Build, `--check` và `git diff --check` PASS. Maven **791/791 test PASS, 0 failures/errors/skipped**; log tại `target/storefront-polish-tests.log`. Ảnh trước/sau (sản phẩm mẫu) và metrics tại `target/product-card-verification/`; chưa kiểm tra trên điện thoại thật hoặc Render.

## Liên hệ và lên đầu trang không che sản phẩm

Fragment `assets/fragments/storefront.html` có vùng `#mobile-support-actions` ở đầu footer. `initializeContactWidget()` trong `assets/modules/navigation-events.js` chuyển **cùng một** nhóm nút vào đó khi viewport dưới 1440 px, giữ nguyên ID, liên kết Zalo/Facebook/điện thoại và các handler. Khi màn hình rộng trở lại, nhóm nút về đúng vị trí DOM ban đầu để `position: fixed` không bị ảnh hưởng bởi transform/reveal của footer. Resize đóng bảng liên hệ và giữ focus hợp lệ; lên đầu trang trên màn hình hẹp đưa focus về logo header.

CSS nguồn: `frontend/css/account-contact.css`. Nhóm nút và bảng liên hệ đều nằm trong luồng footer ở màn hình hẹp, không chỉ đổi tọa độ của nút nổi. Nút có chữ, chiều cao tối thiểu 44 px, một cột ở 320 px và hai cột từ 375 px. Bảng mở đẩy nội dung xuống; theme dùng các token có sẵn. Từ 1440 px trở lên giữ nút icon nổi và hành vi hover cũ. Ngưỡng này xử lý cả lỗi tương tự đã đo được ở desktop 1366 px; 1920 px không bị lỗi và giữ nguyên vị trí/kích thước nút.

Kiểm tra với catalog thật, yêu cầu ứng dụng local đang chạy:

```powershell
# --before lưu snapshot UI hiện tại vào target/mobile-support-verification/before-assets nếu chưa có.
# Snapshot dùng worktree lúc bắt đầu, không dùng HEAD vì phải giữ các sửa đổi giao diện chưa commit.
node scripts/verify-mobile-support.cjs --before
node scripts/verify-mobile-support.cjs
# Chỉ kiểm tra/chụp lại trang chi tiết, không ghi đè metrics của toàn bộ kệ:
node scripts/verify-mobile-support.cjs --details-only
```

`STOCKFLOW_LOCAL_URL` mặc định `http://localhost:8080`. Script phục vụ UI workspace, chuyển tiếp **chỉ GET** tới API local và dùng ảnh thật của catalog; mọi request ghi bị chặn. Quét mọi trang catalog, giá/nút mua, kiểm tra hit-test của thanh mua hàng cố định, Enter/Tab/ArrowDown/Escape, liên kết, lên đầu trang, resize khi bảng đang mở và ID không trùng. Catalog được kiểm tra với giảm chuyển động; trang chi tiết với chuyển động bình thường. Ảnh và JSON nằm ở `target/mobile-support-verification/`. Script báo lỗi nếu local/API không có sản phẩm, không thay dữ liệu thật bằng fixture.

Kiểm chứng 09/10/2026: API local trả **7 sản phẩm đang bán theo model**; đã quét đủ cả 7. Tại **320/375/414 px**, sáng/tối, không còn nhóm hỗ trợ che giá hoặc nút mua; trang chi tiết/thanh mua hàng, liên hệ/bàn phím/lên đầu trang PASS. 1366 px hết lỗi; 1920 px giữ vị trí/kích thước nút nổi và không chồng lên kệ. Kiểm tra thẻ cũ tại năm kích thước PASS; bộ hồi quy Chrome PASS, gồm carousel tự xoay/dừng khi hover hoặc giảm chuyển động (test chọn rõ media preference để không phụ thuộc máy). CSS build/`--check`, `git diff --check` PASS; Maven **791/791 PASS, 0 failures/errors/skipped**, log `target/mobile-support-tests.log`. Ba tài nguyên UI live (`styles.css`, fragment storefront, module navigation-events) khớp workspace sau build/test.

Ảnh dùng dữ liệu catalog local: iPhone 17, Sony, HP, Apple Watch, tay cầm và Samsung tải ảnh được; riêng ảnh IPHONE 18 PRO đang dùng hình minh họa dự phòng sẵn có. Không sửa dữ liệu/ảnh sản phẩm trong lượt này. Chưa kiểm tra trên điện thoại thật, Safari hoặc Render.

File sửa trong lượt này: `frontend/css/account-contact.css`, `assets/fragments/storefront.html`, `assets/modules/navigation-events.js`, bundle `styles.css`, `scripts/verify-mobile-support.cjs`, `scripts/verify-storefront-ui.cjs`, tài liệu này. Backend, nghiệp vụ mua hàng và các thay đổi tinh gọn cửa hàng trước đó được giữ nguyên.

V26 lưu hash OTP đặt lại mật khẩu, hạn 15 phút, tối đa 5 lần nhập sai, chống gửi lại trước 60 giây/5 email mỗi giờ. Đổi hoặc đặt lại mật khẩu vô hiệu hóa JWT cũ. Rate limit IP phục hồi hiện nằm trong tiến trình; triển khai nhiều instance cần kho giới hạn dùng chung.

V27 lưu loại vận đơn: `SIMULATED`, `GHN_SANDBOX`, `GHN_PRODUCTION`, `MANUAL`. Chỉ GHN production có link tra cứu; không suy đoán vận đơn cũ là đơn GHN thật.

V28 lưu tối đa 20 địa chỉ/tài khoản, đồng bộ địa chỉ mặc định với API hồ sơ cũ. Đơn đã đặt giữ nguyên thông tin người nhận, không thay đổi theo sổ địa chỉ.

V29 lưu email xác nhận/đang giao/đã giao trong outbox cùng transaction đơn hàng. Dispatcher gửi SMTP mỗi 10 giây, thử tối đa 5 lần, cách lần lỗi 5 phút. Cần `MAIL_USERNAME`, `MAIL_PASSWORD`; có thể tắt bằng `app.mail.order-notifications-enabled=false`. Không gửi thông báo ngược cho đơn cũ. Nếu tiến trình dừng sau gửi SMTP nhưng trước ghi `sent_at`, retry có thể gửi trùng (at-least-once).

V30 lưu yêu cầu đổi/trả toàn bộ đơn đã giao, lý do và tối đa 5 ảnh PNG/JPEG × 2 MB. Ảnh được xử lý lại, chỉ chủ yêu cầu hoặc MANAGER/ADMIN đọc được. Duyệt chưa hoàn kho; xác nhận đã nhận lại hàng mới ghi `RETURN_RESTOCK` qua fulfillment hiện có. Gửi sản phẩm thay thế và hoàn tiền thực tế qua ngân hàng/VNPay cần cửa hàng xử lý riêng.
