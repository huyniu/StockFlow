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

Regression đăng nhập từ giỏ (10/10/2026): `#create-order` dùng `type=button`/`checkout-login` khi chưa đăng nhập; khi có user đổi thành submit. Listener submit phải rẽ nhánh guest **trước** `reportValidity()`, không dùng `novalidate` để bỏ validation giao hàng. Flow `intent=checkout` giữ giỏ và mở checkout sau login. `node scripts/verify-storefront-ui.cjs --checkout-login-only` kiểm tra ở 375/1366 px với hai SKU, số lượng/trạng thái chọn, form nhận hàng trống trước/sau login và không tạo đơn. Bộ Chrome đầy đủ cũng chạy regression này. Thanh demo chỉ còn CUSTOMER; quyền vận hành công khai bị chặn ở backend, xem [auth-hardening.md](auth-hardening.md).

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

Ảnh dùng sản phẩm mẫu và ảnh logo cục bộ; tài nguyên mạng ngoài bị chặn. Script đo bố cục với chế độ giảm chuyển động, sau đó bật chuyển động bình thường để kiểm tra fade-in/fade-out khi cuộn, tái xuất hiện và thẻ tương tác được khi hiện ở cả năm kích thước, sáng/tối. Kiểm tra thêm mở chi tiết bằng Enter, phản hồi thêm giỏ, nền Aura theo theme cho cửa hàng/đăng nhập và nền quản trị giữ nguyên so với bản HEAD. Bộ `verify-storefront-ui.cjs` kiểm tra điều hướng đăng nhập và quản trị thực tế. Đây là kiểm tra bằng Chrome headless, chưa thay thế Safari hoặc thiết bị thật.

Kiểm chứng ngày 09/10/2026: kiểm tra thẻ tại cả năm kích thước, sáng/tối và luồng chọn phiên bản/màu/thêm giỏ PASS; bộ kiểm tra Chrome cũ PASS; CSS bundle `--check` PASS; Maven **791 test PASS, 0 failures/errors/skipped**. Không thay đổi backend/API, thương hiệu hoặc nghiệp vụ kho/đặt hàng trong thay đổi thẻ này.

## Tinh gọn cửa hàng

`frontend/css/storefront-polish.css` giữ banner dùng `--surface`, chữ dùng `--text`/`--muted`, thao tác chính dùng `--brand`. Theo yêu cầu mới ngày 10/10/2026, nền trang được quản lý riêng tại `frontend/css/aura-background.css`, ghép cuối manifest: Arctic Frost khi sáng, Phantom Arc khi tối, áp dụng cho cả cửa hàng và đăng nhập. Nền quản trị trong `assets/theme.css` giữ nguyên. Không sửa trực tiếp `styles.css`.

Nền sáng đặt base `#faf8f2` trên body, hai lớp linear gradient dùng `multiply`, blur 90 px dưới 768 px/130 px từ 768 px. Nền tối đặt base `#100e0b`, ba lớp radial dùng `screen`/`screen`/`lighten`; blur 50/175/50 px trên mobile và 72/252/72 px trên desktop. Độ mạnh mỗi lớp Phantom Arc bằng 70% mẫu đã cung cấp (opacity .7/.63/.56). Không thêm chuyển động vào nền. Container `.aura-bg` trong fragment `chrome.html` trong suốt, không tự tạo stacking context; từng lớp cố định theo viewport, nằm trên backdrop của body và dưới nội dung. Container/layers có `pointer-events: none`, `aria-hidden="true"`. Giữ các bề mặt sản phẩm/form để chữ dễ đọc; không bọc lại nội dung hoặc thay header sticky.

`assets/theme.css` là CSS nguồn độc lập, nạp sau bundle để điều khiển màu các component. Các rule nền cũ không được ghi đè theme mới của storefront/auth; màu chữ/hình minh họa đăng nhập thích ứng cùng nền sáng/tối.

Chữ phụ/số kết quả/phân trang và liên kết tiêu đề mục nằm trực tiếp trên nền Aura dùng `--text` để dễ đọc; liên kết có gạch dưới. Màu giá và các thao tác mua trong thẻ không đổi.

Kiểm chứng nền Aura ngày 10/10/2026: `verify-product-cards.cjs` PASS tại 320/375/414/768/1366 px sáng/tối, gồm cuộn, bàn phím, chọn phiên bản/màu và thêm đúng SKU. Bộ `verify-storefront-ui.cjs` đầy đủ PASS: đăng nhập/đổi theme (thêm 414 px), checkout, kệ trực tiếp/F5/Back/Forward, danh mục sau cuộn, gallery, địa chỉ, đổi trả và quản trị. CSS build/`--check`, syntax JS, `git diff --check` và Maven `FrontendBundleConsistencyTest` **1/1 PASS**. Log `target/aura-background-{cards,ui,maven}.log`; ảnh trước/sau 375 px và desktop/đăng nhập tại `target/aura-background-verification/`. Đây là Chrome headless/API giả lập, chưa kiểm tra trên điện thoại thật hoặc deploy Render; không chạy lại toàn bộ Maven cho thay đổi nền này. Không sửa backend, dữ liệu, migration hay biến môi trường.

Theo yêu cầu khôi phục animation ngày 10/10/2026, thẻ sản phẩm trở lại `storefrontRevealSelector` của `core.js`. Observer theo dõi thẻ cả sau lần hiện đầu tiên: vào viewport thì hiện dần, ra ngoài thì mờ dần (opacity 500 ms). Giữ hình học thẻ ổn định bằng cách không translate/scale theo reveal để tránh lặp nhấp nháy ở mép viewport. Các mục khác vẫn chỉ reveal một lần. Hover nâng 2 px, focus có viền rõ và hiện thẻ ngay; thẻ đang giữ focus không bị observer ẩn. Chế độ giảm chuyển động luôn hiện đầy đủ và bỏ hover nâng thẻ. Tiêu đề mục, hướng dẫn mua hàng, ID liên kết và nội dung dành cho trình đọc màn hình được giữ lại; chỉ bỏ nhãn phụ lặp ý trong cửa hàng.

Kiểm chứng animation 10/10/2026: kiểm thử mới FAIL trước sửa vì thẻ không tham gia observer (`target/product-scroll-before.log`). Sau sửa, Chrome 320/375/414/768/1366 px sáng/tối PASS: đo opacity ở giữa fade-in/fade-out, tái hiện sau cuộn, focus hiện ngay và rời focus khôi phục hiệu ứng, giảm chuyển động, bàn phím/phiên bản/màu/thêm giỏ. Bộ Chrome storefront đầy đủ PASS, gồm kệ/F5/Back/Forward và đăng nhập từ giỏ/checkout; log `target/product-scroll-{cards,ui}.log`. CSS build/`--check`, syntax JS, `git diff --check` và Maven `FrontendBundleConsistencyTest` 1/1 PASS (`target/product-scroll-maven.log`). Ảnh các giai đoạn nằm tại `target/product-card-verification/scroll-{enter,visible}-*.png`, dùng dữ liệu mẫu/API giả lập. Chưa kiểm tra điện thoại thật, chạy lại toàn bộ Maven hoặc deploy Render trong lượt khôi phục animation.

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

## Ảnh sản phẩm trong bảng quản trị

`productCell()` trong `assets/modules/admin.js` dùng ảnh của đúng SKU qua `saleSku()`, tên/phiên bản/màu và mã sản phẩm. Tồn kho, sản phẩm bán chạy, cảnh báo tồn kho và sổ cái dùng cùng khung ảnh 52 × 52 px. CSS nguồn ở `frontend/css/portal.css`; build bundle như hướng dẫn ở đầu tài liệu. Ảnh dùng `object-fit: contain`, tải lazy, trang trí cạnh tên sản phẩm (`alt=""`, `aria-hidden`), không thêm điểm Tab. Khi thiếu ảnh hoặc URL không hợp lệ, giữ icon hộp; khi tải ảnh lỗi, handler `data-admin-thumbnail` trong `navigation-events.js` ẩn ảnh và để hiện icon, không làm lệch hàng.

API đọc sổ cái bổ sung `product_id`, `product_name`, `product_sku`, `image_url` từ sản phẩm hiện tại. `InventoryService.history()` nạp các inventory thuộc trang bằng một truy vấn có EntityGraph, tránh tải toàn bộ tồn kho hoặc truy vấn từng biến động. Đây là thông tin nhận diện catalog hiện tại; các snapshot số lượng, trước/sau, ghi chú, người thực hiện và thời điểm không đổi. Không có migration hoặc thay đổi quyền/nghiệp vụ ghi sổ. Cần khởi động lại Spring Boot để API đang chạy nhận các trường mới, rồi tải lại trang bằng Ctrl+F5.

```powershell
node scripts/verify-portal-product-images.cjs
# Dùng ảnh thực tế của catalog local; số liệu kho/báo cáo vẫn là fixture tách biệt:
node scripts/verify-portal-product-images.cjs --real-images
```

Script phục vụ UI workspace và API giả lập chỉ GET, không tạo tài khoản/đơn/biến động thật. Chế độ `--real-images` đọc catalog công khai từ `STOCKFLOW_LOCAL_URL` (mặc định `http://localhost:8080`) để lấy ảnh Sony, HP và iPhone. Kiểm tra Chrome 320/375/768/1366/1920 px sáng/tối: ảnh đúng màu SKU, vào thẳng sổ cái, SKU không có trong cache, URL không an toàn, ảnh thiếu/hỏng, kích thước khung, tràn trang. Bảng rộng trên mobile vẫn cuộn ngang trong vùng bảng. Ảnh/metrics tại `target/portal-product-images-verification/`; chưa thay thế điện thoại thật, Safari hoặc Render.

Kiểm chứng ngày 09/10/2026: kiểm tra trình duyệt với ảnh catalog thật PASS; Maven **793/793 PASS, 0 failures/errors/skipped** (giữ 791 test cũ, thêm hai test nối ảnh SKU/sổ cái và trường hợp không có ảnh). Log `target/portal-product-images-tests.log`.

V26 lưu hash OTP đặt lại mật khẩu, hạn 15 phút, tối đa 5 lần nhập sai, chống gửi lại trước 60 giây/5 email mỗi giờ. Đổi hoặc đặt lại mật khẩu vô hiệu hóa JWT cũ. Rate limit IP phục hồi hiện nằm trong tiến trình; triển khai nhiều instance cần kho giới hạn dùng chung.

V27 lưu loại vận đơn: `SIMULATED`, `GHN_SANDBOX`, `GHN_PRODUCTION`, `MANUAL`. Chỉ GHN production có link tra cứu; không suy đoán vận đơn cũ là đơn GHN thật.

V28 lưu tối đa 20 địa chỉ/tài khoản, đồng bộ địa chỉ mặc định với API hồ sơ cũ. Đơn đã đặt giữ nguyên thông tin người nhận, không thay đổi theo sổ địa chỉ.

V29 lưu email xác nhận/đang giao/đã giao trong outbox cùng transaction đơn hàng. Dispatcher gửi SMTP mỗi 10 giây, thử tối đa 5 lần, cách lần lỗi 5 phút. Cần `MAIL_USERNAME`, `MAIL_PASSWORD`; có thể tắt bằng `app.mail.order-notifications-enabled=false`. Không gửi thông báo ngược cho đơn cũ. Nếu tiến trình dừng sau gửi SMTP nhưng trước ghi `sent_at`, retry có thể gửi trùng (at-least-once).

V30 lưu yêu cầu đổi/trả toàn bộ đơn đã giao, lý do và tối đa 5 ảnh PNG/JPEG × 2 MB. Ảnh được xử lý lại, chỉ chủ yêu cầu hoặc MANAGER/ADMIN đọc được. Duyệt chưa hoàn kho; xác nhận đã nhận lại hàng mới ghi `RETURN_RESTOCK` qua fulfillment hiện có. Gửi sản phẩm thay thế và hoàn tiền thực tế qua ngân hàng/VNPay cần cửa hàng xử lý riêng.

## Mở trực tiếp và tải lại kệ sản phẩm

`navigation-events.js:routeFromLocation()` không được bỏ qua tải catalog chỉ vì `state.view/shopTab` mặc định là `shop/catalog`. Khi khởi động, `initialize()` đã tạo skeleton nhưng chưa tải sản phẩm. Nhánh cũ return ngay với `#product-shelf` khiến không có GET catalog sau khi mở URL trực tiếp hoặc F5.

Nhánh mới chỉ dùng lại kệ khi `#catalog-grid.dataset.catalogStatus` là `ready` và bộ lọc trong URL khớp trạng thái hiện tại. Trường hợp khác phục hồi bộ lọc, kích hoạt catalog, giữ nguyên `#product-shelf`, rồi gọi `scrollToCatalogResults()` sau khi tải xong. Hàm cuộn hiện có đặt focus vào tiêu đề và dùng khoảng cách header cố định trong CSS; không thay bố cục thẻ, nền, nút liên hệ hoặc bộ lọc thương hiệu.

`product-detail.js:loadCatalog()` dùng lại promise đang chạy cho cùng phiên/bộ lọc/trang thay vì hủy rồi tải lại khi khách bấm liên kết kệ. GET catalog có thời hạn 15 giây; lỗi HTTP, mạng hoặc hết thời hạn dừng skeleton và hiện **Thử lại**. Handler `retry-catalog` tải lại và đưa khách về kệ. Kết quả rỗng vẫn được coi là đã tải xong.

Kiểm thử hồi quy trong `scripts/verify-storefront-ui.cjs:verifyShelfRoutes()` chạy trước các luồng cũ:

```powershell
node scripts/verify-storefront-ui.cjs --shelf-only
node scripts/verify-storefront-ui.cjs
node scripts/verify-product-cards.cjs
node scripts/build-storefront.cjs
node scripts/build-storefront.cjs --check
.\mvnw.cmd "-Dmaven.repo.local=C:/Users/Admin/.m2/repository" test
```

Kiểm chứng ngày 09/10/2026: trước sửa, bốn trường hợp mở trực tiếp/F5 sau bấm “Khám phá sản phẩm” tại 375/1366 px đều FAIL vì không có thẻ catalog (`target/product-shelf-before.log`). Sau sửa, bộ Chrome đầy đủ PASS: bốn trường hợp trên; giữ bộ lọc, Back/Forward, chi tiết, thêm giỏ và F5 giữ giỏ; không GET dư khi kệ sẵn sàng/đang tải; lỗi 503 và timeout/thử lại; kết quả rỗng; callback VNPay success/failed tại 375/1366 px. Log cuối: `target/product-shelf-ui-tests.log`; ảnh kệ mẫu: `target/ui-verification/shelf-direct-375.png` và `shelf-direct-1366.png`.

Bộ thẻ Chrome tại 320/375/414/768/1366 px sáng/tối, cuộn, bàn phím, phiên bản/màu/thêm giỏ PASS (`target/product-shelf-card-tests.log`). Đây là trình duyệt headless với API giả lập tách biệt, chưa kiểm tra trên điện thoại thật hoặc trình duyệt Render sau deploy. CSS đã build lại và `--check` PASS; bundle không có thay đổi nội dung. Maven **793/793 PASS, 0 failures/errors/skipped** (`target/product-shelf-maven-tests.log`); không sửa Java/API/nghiệp vụ.

Mô tả là dữ liệu database riêng cho từng môi trường. Nội dung đã rà soát, bản thay thế và phạm vi cập nhật local/Render được ghi tại [product-description-review.md](product-description-review.md). Deploy bản sửa định tuyến không tự cập nhật các mô tả này.

## Danh mục khi đã cuộn khỏi banner

`catalog-navigation.js:canUseHomeCategoryMenu()` kiểm tra vị trí thật của ô danh mục cạnh banner: mép trên phải nằm dưới header và còn đủ chỗ hiển thị. Ở đầu catalog trên desktop, giữ menu cạnh banner như trước. Nếu banner đã cuộn khỏi màn hình hoặc bị header che một phần, `setShopCategoryMenu()` dùng `#category-menu-panel` trong header, giữ nguyên vị trí cuộn và URL khi khách chỉ xem trước danh mục.

CSS nguồn `frontend/css/product-interactions.css` đặt panel theo viewport, ngay dưới thanh demo/header, giới hạn chiều cao theo màn hình và cho từng cột cuộn riêng. Cột danh mục trên desktop giữ nút đóng trong tầm nhìn kể cả cửa sổ thấp. `frontend/css/compact-header.css` dùng cùng tọa độ cho tablet; giữ bố cục mobile hiện có. Fragment `storefront.html` chỉ cập nhật chú thích về hai cách mở, không đổi cấu trúc HTML.

Kiểm tra riêng: `node scripts/verify-storefront-ui.cjs --category-menu-only`. Kiểm thử đo khung menu, hit-test nút, giữ scroll/URL/bộ lọc khi xem trước, Escape/focus, bấm nền để đóng, ô cạnh banner ở đầu trang, resize, bàn phím ArrowDown, con lăn trong menu và chọn hãng. Chrome dùng dữ liệu giả lập, không thay đổi database.

Kiểm chứng 10/10/2026: trước sửa, cả sáng/tối ở 1050/1366/1920 px đều mở ô cạnh banner đã nằm ngoài màn hình (`target/category-scroll-before.log`). Sau sửa, 320/375/414/768/1050/1366/1920 px sáng/tối PASS; các cửa sổ desktop cao 600 px và trường hợp banner che một phần cao 420 px đều dùng được menu. Ảnh trước lỗi/sau sửa lưu tại `target/ui-verification/category-scroll-*-failed.png` và `category-scroll-*-passed.png`; ảnh dùng sản phẩm mẫu. CSS build/`--check`, `git diff --check` và Maven `FrontendBundleConsistencyTest` (1 test) PASS. Không sửa backend, dữ liệu sản phẩm hoặc nghiệp vụ mua hàng trong bản sửa danh mục này.

Bộ Chrome đầy đủ cũng PASS: hồi quy `#product-shelf`, Back/Forward/giỏ, callback VNPay, đăng nhập, danh mục từ chi tiết/hồ sơ, gallery, checkout/GHN, sổ địa chỉ và đổi trả (`target/category-scroll-ui-tests.log`). Không chạy lại toàn bộ Maven trong lượt sửa giao diện danh mục; kết quả 793/793 ở mục trước là lần chạy cho bản sửa kệ.
