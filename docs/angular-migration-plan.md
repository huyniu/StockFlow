# Kế hoạch chuyển frontend StockFlow sang Angular

Ngày khảo sát: **10/10/2026**. Mã nguồn tham chiếu: commit `a265271` và implementation trong workspace `D:\IdeaProjects\Stockflow` tại thời điểm đọc.

**Trạng thái: kế hoạch, chưa triển khai Angular.** Lượt này chỉ khảo sát và tạo tài liệu; không sửa ứng dụng, database, cấu hình Render, không commit/push/deploy. Những lệnh và kiểm thử dưới đây là công việc dự kiến, không phải kết quả đã chạy.

## 1. Hướng đi đề xuất

Tạo Angular trong thư mục riêng **`frontend-angular/`**, cùng repository với Spring Boot. Chuyển từng nhóm chức năng, giữ frontend hiện tại trong `src/main/resources/static/` hoạt động. Khi đủ điều kiện triển khai thử, phục vụ Angular tại **`/angular/`** trên cùng dịch vụ Render; frontend cũ tiếp tục ở `/`.

Angular chịu trách nhiệm hiển thị, form, điều hướng và gọi API. Spring Boot tiếp tục quyết định giá bán, cước hợp lệ, tồn kho, reservation, trạng thái đơn, thanh toán, ledger và quyền truy cập. Không chuyển các quy tắc này sang trình duyệt, không tạo backend thứ hai hoặc database chung cho local và Render.

Đây là cách phù hợp với mục tiêu học và portfolio Java Backend: có frontend có cấu trúc, nhưng bằng chứng chính vẫn là API, transaction, kiểm soát đồng thời, phân quyền và kiểm thử PostgreSQL. Chưa cần NgRx, microfrontend, SSR hoặc thiết kế giao diện mới.

## 2. Hiện trạng và nguồn đã đối chiếu

### 2.1. Kiến trúc thực tế

- Backend: **Spring Boot 3.4.5, Java 17**, PostgreSQL, JPA/JDBC, Flyway; Maven Wrapper. Đây là hệ thống một nhà bán lẻ nhiều kho, không phải marketplace nhiều người bán.
- Frontend: HTML fragments + Native ES Modules. `static/app.js` tải fragments, đăng ký module rồi khởi tạo; `context.js` giữ registry nội bộ, không đưa trạng thái lên `window`.
- HTML nguồn: `assets/fragments/{chrome,auth-page,storefront,dashboard,dialogs,aftercare,icons}.html`; `index.html` là shell.
- CSS nguồn: `frontend/css/`, ghép theo thứ tự trong `frontend/manifest.json` bằng `scripts/build-storefront.cjs`. Ngoài bundle còn có `assets/theme.css` và `assets/ui-polish.css`; phải giữ thứ tự cascade khi chuyển.
- Dockerfile hiện chỉ build Maven rồi chạy JAR bằng Java 17 JRE. CI hiện chạy Maven, PostgreSQL/Testcontainers và đóng gói JAR; chưa có bước build Angular.
- URL Render người dùng đang sử dụng: `https://stockflow-tbsw.onrender.com/`. Trong lượt này **chưa kiểm tra dashboard, biến môi trường hoặc database live**; phương án deploy bên dưới dựa vào Dockerfile/config trong repository và URL đã cung cấp.

### 2.2. Các file chính đã đọc

- [README](../README.md), [tài liệu frontend](frontend-development.md), [giỏ và đồng bộ đơn](cart-and-order-sync.md), [tài khoản và sau bán hàng](account-aftercare.md), [Google login](google-login.md), [email trên Render](email-delivery-render.md).
- Entry và trạng thái: [app.js](../src/main/resources/static/app.js), [context.js](../src/main/resources/static/assets/modules/context.js), [core.js](../src/main/resources/static/assets/modules/core.js), [navigation-events.js](../src/main/resources/static/assets/modules/navigation-events.js).
- Module: `auth-profile.js`, `catalog-navigation.js`, `product-detail.js`, `cart-checkout.js`, `orders-fulfillment.js`, `admin.js`, `account-services.js`, `after-sales.js`, `recently-viewed.js`; các helper carousel, image viewer, theme và artwork đăng nhập.
- Controller/DTO: các package `auth`, `catalog`, `user`, `warehouse`, `shipping`, `order`, `payment`, `inventory`, `report` và `review`; chú ý `PageResponse`, `AuthResponse`, `CreateOrderRequest`, `OrderResponse`, `ProductVariantResponse`.
- Bảo mật: [SecurityConfig](../src/main/java/com/stockflow/common/config/SecurityConfig.java), [JwtAuthenticationFilter](../src/main/java/com/stockflow/auth/security/JwtAuthenticationFilter.java), `AuthService`, `RegistrationOtpRateLimiter`, `GlobalExceptionHandler`.
- Nghiệp vụ liên quan: `OrderPlacementService.place()`, `OrderService.createOrder()`, `ShippingQuoteService.quote()`, `VNPayService.handleReturn()/handleIpn()`, `GhnShippingService`, `ReviewService`, `AddressBookService`, `UserAdministrationService`.
- Đóng gói: [Dockerfile](../Dockerfile), [Compose](../compose.yaml), [.dockerignore](../.dockerignore), [CI](../.github/workflows/ci.yml), `application.yml`, `application-demo.yml`.

Một số đoạn tài liệu/comment cũ còn ghi miễn phí giao hàng, chưa có địa chỉ lưu sẵn hoặc OTP được in console. **Không lấy những đoạn này làm contract mới:** implementation hiện có cước GHN, sổ địa chỉ và OTP đăng ký lưu hash. Các tổng số test trong báo cáo cũ là kết quả lịch sử; phải chạy lại để chốt baseline khi bắt đầu chuyển.

## 3. Màn hình, luồng và API hiện có

Các đường dẫn API bên dưới đều bắt đầu bằng **`/api/v1`**. Route giao diện và API là hai loại đường dẫn khác nhau.

### 3.1. Cửa hàng, danh mục, tìm kiếm

Giao diện: `/`, `/#shop`, bộ lọc trong query string và `/#product-shelf`. Có banner, danh mục, menu hãng/khoảng giá, tìm kiếm có gợi ý, bộ lọc thông số, phân trang, bán chạy, chọn chi nhánh, FAQ và liên hệ.

- `GET /products`: `categoryId`, `brandId`, `q`, `status`, `minPrice`, `maxPrice`, `specificationName`, `specificationValue`, `grouped`, `page`, `size`, `sort`.
- Kệ khách dùng `grouped=true` và `status=ACTIVE` để gộp model; không cố định số sản phẩm trong code.
- `GET /categories?q=...`, `GET /brands?categoryId=...`, `GET /products/specification-options`.
- `GET /storefront/bestsellers?limit=...`, `GET /storefront/branches`, `GET /storefront/contact`.
- Yêu thích nằm trong popup, lưu phía trình duyệt; sản phẩm đã xem nằm cuối cửa hàng. Hai chức năng này **chưa có API lưu danh sách riêng**, chỉ đọc lại `GET /products/{id}`.

Nguồn: `catalog-navigation.js`, `product-detail.js:loadCatalog()`, `recently-viewed.js`, các controller catalog và storefront.

### 3.2. Chi tiết sản phẩm

Giao diện: **`/san-pham/{id}`**; `ProductPageController` hiện forward về frontend cũ. Có phiên bản/màu, giá riêng SKU, gallery, zoom theo con trỏ/kéo ảnh, thông số, mô tả, tình trạng bán theo chi nhánh, đánh giá và thanh mua hàng mobile.

- `GET /products/{id}` trả model, `versions`, `variants`, thông số, ảnh và mô tả.
- `GET /products/{id}/availability` trả `in_stock` theo kho, **không trả số lượng tồn nội bộ**.
- `GET /products/{id}/reviews?page=...` trả danh sách và điểm trung bình.
- ID màu `variants[].id` khác với **`variants[].sku_product_id`**. Đặt hàng/nhập kho dùng `sku_product_id` làm `product_id`, không dùng ID model hoặc ID màu.

Nguồn: `product-detail.js`, `product-image-viewer.js`, `ProductResponse`, `ProductVersionResponse`, `ProductVariantResponse`, `ProductAvailabilityController`, `ReviewController`.

### 3.3. Đăng nhập, đăng ký, OTP, Google, quên mật khẩu

Giao diện: `/login`, `/register`, popup OTP và khôi phục mật khẩu; có `intent`/`pre_uri` để quay lại việc đang làm.

- `POST /auth/register`: `{email, password, full_name}` → **201** `{email, requires_verification, message}`; chưa có JWT.
- `POST /auth/verify-email`: `{email, otp}` → `AuthResponse` có `access_token`, `token_type`, `user`.
- `POST /auth/resend-otp`: `{email}`. Giữ mã mới nhất, hạn 15 phút, khoảng chờ 60 giây và giới hạn backend hiện có.
- `POST /auth/login`: `{email, password}` → `AuthResponse`; email chưa xác thực trả **403** `{error: "EMAIL_NOT_VERIFIED", email}` để mở OTP.
- `GET /auth/google/config` → `{enabled, client_id, nonce}` và cookie HttpOnly `stockflow_google_nonce`; Google Identity Services tạo credential, gửi `POST /auth/google` với `{credential}` và cookie nonce.
- `POST /auth/forgot-password`: `{email}`; `POST /auth/reset-password`: `{email, otp, new_password}`.
- Không có endpoint refresh token hoặc đăng xuất server trong các controller đã đọc. Đăng xuất hiện tại xóa phiên phía client; không tự thiết kế thêm refresh/logout API khi chuyển.

OTP đăng ký hiện lưu hash, tối đa 5 lần nhập sai/mã, khóa user để xử lý đồng thời và lưu bộ đếm khi trả lỗi. Angular chỉ hiển thị phản hồi, không đọc mã từ database, không ghi OTP/mật khẩu/credential/token vào log.

### 3.4. Hồ sơ, sổ địa chỉ, đổi mật khẩu

Giao diện: `/#shop/account`, menu tài khoản và các dialog sổ địa chỉ/đổi mật khẩu.

- `GET /users/me`; `PATCH /users/me`: `full_name`, `phone`, `default_address`, `clear_default_address` theo DTO. Không gửi role, email hoặc password vào PATCH hồ sơ.
- `GET/POST /users/me/addresses`; `PUT/DELETE /users/me/addresses/{id}`; `POST /users/me/addresses/{id}/default`.
- Địa chỉ tạo/sửa gồm `label`, `recipient_name`, `recipient_phone`, `province_id`, `district_id`, `ward_code`, `street_address`, `is_default`; tối đa 20 địa chỉ.
- `POST /users/me/password`: `{current_password, new_password}`. Đổi/đặt lại mật khẩu vô hiệu JWT cũ; frontend đưa về đăng nhập.
- Địa chỉ mặc định/sổ địa chỉ có thể điền checkout; đơn đã đặt giữ bản chụp người nhận, không đổi theo hồ sơ sau này.

Nguồn: `auth-profile.js`, `account-services.js`, `UserController`, `AddressBookController`, `PasswordChangeController` và các DTO/service tương ứng.

### 3.5. Giỏ hàng và checkout

Giao diện hiện dùng dialog `#cart-dialog`, không có API giỏ hàng. Có ảnh SKU, số lượng, xóa, tích sản phẩm cần mua, kho xuất, địa chỉ lưu sẵn/địa chỉ mới, cước và tổng dự kiến.

- `GET /locations/mode` → `{test_mode}`.
- `GET /locations/provinces`; `GET /locations/districts?province_id=...`; `GET /locations/wards?district_id=...`.
- `POST /locations/calculate-fee`: `{warehouse_id, to_district_id, to_ward_code, weight}` → `{shipping_fee, service_type_id, test_mode}`. Checkout hiện dùng **500 gram**, khớp `ShippingQuoteService.CHECKOUT_WEIGHT`.
- `POST /orders`, header **`Idempotency-Key`**; payload gồm `warehouse_id`, `items[{product_id, quantity}]`, `delivery{recipient_name, recipient_phone, address, note}`, `to_district_id`, `to_ward_code`, `shipping_fee`.
- Backend đọc giá SKU, tính lại cước khi có mã quận/phường, kiểm tra ward thuộc district và reserve nguyên tử; cước không khớp trả **409**. `OrderResponse.total_amount` là tổng có phí ship. Contract vẫn hỗ trợ client cũ không gửi trường vận chuyển mới với phí mặc định 0; Angular checkout mới tiếp tục dùng quote đầy đủ theo luồng hiện tại.
- Guest bấm **Đăng nhập để đặt hàng** phải vào auth trước khi validate thông tin nhận hàng; sau login CUSTOMER quay lại checkout với giỏ, số lượng và lựa chọn còn nguyên. Khách đã đăng nhập đặt đơn vẫn phải validate form đầy đủ.
- Chỉ xóa các mặt hàng vừa mua sau khi nhận response đơn hợp lệ; hàng không được tích vẫn nằm trong giỏ.

Nguồn: `cart-checkout.js:createOrder()/checkoutKey()`, `ShippingLocationController`, `CreateOrderRequest`, `OrderPlacementService`, `OrderService`.

### 3.6. Thanh toán và đơn hàng khách

Giao diện: `/#shop/orders`, chi tiết đơn dùng chung; VNPay trả về dạng **`/#orders?payment_status=...&order_id=...`**.

- `GET /orders/my?page=...&size=...`, `GET /orders/{id}`, `GET /orders/{id}/payment`.
- `POST /orders/{id}/cod/confirm`, `POST /orders/{id}/payment-simulations/confirm`, `POST /orders/{id}/cancel`.
- `POST /payments/vnpay/create`: `{order_id}` → `{payment_url}`; frontend kiểm tra HTTPS/host Sandbox rồi điều hướng cả trình duyệt sang VNPay.
- `GET /payments/vnpay/return`: callback công khai, backend kiểm tra thông báo rồi trả **302** về `VNPAY_STOREFRONT_URL` + hash trên.
- `GET /payments/vnpay/ipn`: server VNPay gọi backend, trả `RspCode`/`Message`; không phải endpoint Angular tự gọi để xác nhận thanh toán.
- `VNPayService` hiện xử lý thông báo đã ký ở **cả Return và IPN**, kiểm tra merchant, số tiền, mã giao dịch và chống xử lý lặp. Angular đọc lại đơn/thanh toán, không coi `payment_status=success` trong URL là bằng chứng đã trả tiền.
- Trạng thái đơn: `PENDING`, `CONFIRMED`, `PACKED`, `SHIPPED`, `DELIVERED`, `CANCELLED`, `EXPIRED`, `RETURNED`. COD được xác nhận không có nghĩa đã thu tiền; đọc thêm trạng thái thanh toán.
- Đồng bộ đơn khách hiện mỗi 15 giây khi đang xem; gần/sau hạn giữ hàng kiểm tra lại, không tự đổi đơn thành EXPIRED. Khi ẩn tab/rời màn hình/đổi user, ngừng polling.

Nguồn: `orders-fulfillment.js`, `core.js`, `navigation-events.js:consumeVNPayReturn()`, `OrderController`, `PaymentController`, `VNPayService`.

### 3.7. Đánh giá và đổi/trả

- Đơn DELIVERED: `GET/POST /orders/{id}/reviews`; POST `{product_id, rating, service_rating, comment}`. Chỉ chủ đơn, đúng SKU đã mua, 1–5 sao, tối đa 2.000 ký tự và không đánh giá trùng.
- `GET/POST /returns`, `GET /returns/{id}`; tạo `{order_id, kind: RETURN|EXCHANGE, reason}`.
- `POST /returns/{id}/images`: multipart **`file`**, từng ảnh; tối đa 5 ảnh PNG/JPEG, mỗi ảnh 2 MB. `GET /returns/{id}/images/{imageId}` trả bytes có JWT, không phải URL ảnh công khai.
- MANAGER/ADMIN: `POST /returns/{id}/review` với `{decision: APPROVED|REJECTED, note}`; `POST /returns/{id}/receive` sau khi nhận lại hàng thực tế.
- Luồng hiện áp dụng toàn bộ đơn; giao hàng thay thế và hoàn tiền thật qua ngân hàng/VNPay chưa được tự động hóa. Không diễn giải trạng thái hoàn tiền nội bộ thành đã gọi refund thật.

Nguồn: `navigation-events.js` phần review, `after-sales.js`, `ReviewController/ReviewService`, `ReturnRequestController`.

### 3.8. Portal xử lý đơn và vận chuyển

Giao diện: `/#portal/queue` (alias cũ `/#portal/orders`); lọc kho/trạng thái, đọc chi tiết, đóng gói, nhập mã vận đơn hoặc bắn GHN, xác nhận giao/hủy/nhận trả theo quyền.

- `GET /orders?warehouseId=...&status=...&page=...&size=...`, `GET /orders/{id}`.
- `POST /orders/{id}/pack`, `/ship` với `{tracking_code}` nếu nhập thủ công, `/ghn-ship`, `/deliver`; `/cancel`, `/return` theo quyền và trạng thái service cho phép.
- `GET /warehouses/operating-options` trả kho thuộc phạm vi actor; không dùng danh sách chi nhánh công khai để suy ra quyền nhân viên.
- `shipment` có `tracking_code`, `carrier_mode`, trạng thái và thời điểm. Phân biệt `SIMULATED`, `MANUAL`, `GHN_SANDBOX`, `GHN_PRODUCTION`; link tra cứu thật chỉ hiển thị khi phù hợp mode hiện có.
- WAREHOUSE_STAFF chỉ thao tác kho được giao; MANAGER/ADMIN theo service. Angular không tự ghi ledger hoặc xuất kho lần nữa khi đổi màn hình.

### 3.9. Portal tồn kho, ledger, báo cáo

- `/#portal/inventory`: `GET /inventories?productId=...&warehouseId=...`; `POST /inventories/stock-in` cho ADMIN/WAREHOUSE_STAFF được phép tại kho đó.
- `/#portal/ledger`: `GET /inventories/movements?inventoryId=...`; ADMIN/MANAGER, chỉ đọc sổ cái.
- `/#portal/reports`: `GET /reports/revenue` (`fromDate`, `toDate`, `warehouseId`, `groupBy=DAY|MONTH`, phân trang); `/reports/top-products` (`fromDate`, `toDate`, `limit`, phân trang); `/reports/low-stock` (`threshold`, `warehouseId`, phân trang); `/reports/order-summary`.
- Báo cáo chỉ ADMIN/MANAGER; kỳ doanh thu dùng ngày UTC theo contract hiện có.
- `GET /warehouses`, `/warehouses/order-options`, `/warehouses/operating-options` có phạm vi khác nhau; `POST /warehouses` là API tạo kho ADMIN. Không suy ra có API sửa/xóa kho hoặc màn hình CRUD kho đầy đủ.

Nguồn: `admin.js`, `InventoryController`, `ReportController`, `WarehouseController`.

### 3.10. Portal catalog và tài khoản

- `/#portal/products`, `/#portal/categories`: ADMIN tạo/sửa sản phẩm `POST /products`, `PATCH /products/{id}`; tạo/sửa/xóa danh mục `POST /categories`, `PATCH/DELETE /categories/{id}`; tạo hãng `POST /brands`, sửa logo `PATCH /brands/{id}/logo`.
- Phiên bản: `POST /products/{productId}/versions`, `PATCH/DELETE /.../versions/{versionId}`, `POST /.../versions/{versionId}/restore`.
- Màu: `POST /products/{productId}/variants`, `PATCH/DELETE /.../variants/{variantId}`. DELETE lưu trữ lựa chọn, vẫn giữ SKU/lịch sử; frontend hiện khôi phục màu bằng PATCH `{status: "ACTIVE"}`, không có endpoint `/variants/{id}/restore`. Không tự thêm field `archived` vào request nếu DTO không nhận.
- Giữ form bảng thông số, ảnh bìa/gallery và chỉnh riêng giá/ảnh/trạng thái từng màu. `UpdateProductRequest`: bỏ qua/null giữ nguyên; `image_urls:[]` hoặc `specifications:[]` xóa danh sách; chuỗi trống có ý nghĩa xóa một số trường. Không gửi mọi control rỗng như một bản thay thế toàn bộ.
- `/#portal/users`: `GET /admin/users` (`q`, `role`, `status`, `page`, `size`), `GET /admin/users/{id}`, `/permissions`, `/role-history`; `PATCH /.../{id}/status`, `PATCH /.../{id}/role` với `{role, warehouse_ids}`.
- API đổi role chỉ nhận CUSTOMER/WAREHOUSE_STAFF/MANAGER; nhân viên cần kho được giao. Không tự nâng ADMIN hoặc thay quyền/trạng thái tài khoản admin, bản thân và tài khoản hệ thống được bảo vệ.

Nguồn: `admin.js`, controller/DTO catalog và `UserAdministrationController/Service`.

## 4. Cách frontend hiện quản lý dữ liệu

### Token và quyền

`core.js:saveSession()` lưu **chỉ token** trong `sessionStorage['stockflow.web.session']`; lúc khởi động gọi `/users/me` để lấy lại user. Không có refresh token. Mỗi request private gắn `Authorization: Bearer ...`; JWT filter nạp lại tài khoản ACTIVE, email đã xác thực, `authVersion` và chặn tài khoản vận hành demo công khai.

Menu/`canPortalTab()` giúp hiển thị đúng role. Quyền thật vẫn do `SecurityConfig`, `@PreAuthorize` và service kiểm tra chủ đơn/phân công kho. Role trong storage hoặc nội dung JWT do client giải mã chỉ có giá trị hỗ trợ UI.

### Giỏ, yêu thích và theme

- Giỏ `sessionStorage['stockflow.web.cart.v1']`: `{version, owner, warehouse_id, items}`; mỗi item `{product_id, quantity, selected}`. Owner là guest hoặc `customer:<id>`; không lưu giá/người nhận. Không đồng bộ nhiều thiết bị; storage có thể bị chặn.
- Khôi phục giỏ đọc lại SKU/model/chi nhánh từ API, giới hạn 100 dòng; loại SKU không hợp lệ/ngừng bán, giữ dữ liệu khi lỗi mạng và cho thử lại. Không dùng bản lưu giỏ làm bằng chứng tồn kho.
- `stockflow.web.checkout-attempt.v1` lưu metadata chủ giỏ/hash nội dung/Idempotency-Key. Cùng nội dung retry dùng cùng key; đổi nội dung dùng key mới. Mất response không đồng nghĩa backend chưa tạo đơn.
- Yêu thích: `localStorage['stockflow.wishlist.<owner>']`, tối đa 100 ID; đã xem: `stockflow.recently-viewed`, tối đa 30 ID; theme: `stockflow.web.theme`. Đây không phải dữ liệu server hoặc gợi ý AI.
- Logout/đổi actor xóa dữ liệu riêng và giỏ cũ; chỉ giỏ guest được chuyển vào CUSTOMER mới đăng nhập. Không nhập giỏ của tài khoản khác.

### Điều hướng, tải và lỗi

- `routeFromLocation()` phối hợp pathname, query và hash; `pushState`/`replaceState`, `popstate`/`hashchange` giữ Back/Forward. `/login`, `/register`, `/san-pham/{id}` được Spring forward về shell cũ.
- Nhánh `#product-shelf` chỉ dùng lại catalog khi **đã ready và khớp bộ lọc**; state mặc định shop/catalog không chứng minh dữ liệu đã tải. Angular phải giữ regression mở trực tiếp/F5, scroll sau tải, offset header và lỗi/thử lại.
- `api()` dùng AbortController, channel và epoch để bỏ request cũ sau đổi trang/user. Các luồng đọc quan trọng có timeout; loading/empty/error phải tách biệt, không để skeleton vô hạn.
- Lỗi thường: `{timestamp,status,error,message,path,errors?}`; lỗi validation có chi tiết field. `EMAIL_NOT_VERIFIED` là response đặc biệt, không có đầy đủ trường lỗi thường.
- HTTP 401 private: xóa phiên/dữ liệu riêng, cho đăng nhập lại và vẫn xem catalog. 403 quyền hạn khác với 403 chờ OTP; 409 là xung đột nghiệp vụ; 429 là thao tác quá nhanh; lỗi mạng status 0 cần thông báo thử lại.
- Thành công thêm giỏ dùng toast nhỏ `aria-live`, không banner trùng. Nút bận bị khóa; abort do rời trang không cần hiện lỗi. Không tự retry các POST nghiệp vụ.

## 5. Cấu trúc Angular đề xuất

Dùng **standalone components**, lazy routes theo feature, typed Reactive Forms, HttpClient và functional interceptors. Service giữ dữ liệu dùng chung; signals cho trạng thái UI đơn giản, RxJS cho request/debounce/hủy request/polling. Đây là lựa chọn triển khai đề xuất, không phải framework đã cài trong repo. [Angular Router](https://angular.dev/guide/routing), [Reactive Forms](https://angular.dev/guide/forms/reactive-forms), [HTTP interceptors](https://angular.dev/guide/http/interceptors).

```text
frontend-angular/
  angular.json, package.json, package-lock.json
  src/
    main.ts, styles.css, proxy.conf.json
    app/
      app.ts, app.html, app.routes.ts, app.config.ts
      core/
        api/          DTO chung, cấu hình API, mapper, ApiError
        auth/         AuthService, guards, interceptor
        state/        session, storage adapter, reset theo actor
      shared/
        ui/           Button, Dialog, Toast, Loading, Empty, Error, Pager
        products/     ProductCard, ProductImage, Price
        forms/        FieldError, AddressSelector
        directives/   scroll/reveal theo reduced-motion
      layouts/        StorefrontLayout, AuthLayout, PortalLayout
      features/
        catalog/      home, list, filters, search, detail, gallery
        auth/         login, register, verify-email, password-reset, Google
        account/      profile, address-book, password-change
        cart/         CartDialog, CartStore
        checkout/     CheckoutForm, ShippingQuote, CheckoutAttempt
        orders/       history, detail, payment-return, review
        aftercare/    return-request, evidence, request-history
        portal/
          catalog/    products, versions, colors, categories, brands
          inventory/  balances, stock-in, warehouse options
          fulfillment/order queue, packing, shipping, returns
          reports/    ledger, revenue, top-products, low-stock
          users/      list, permissions, status, role-history
      storefront/     WishlistStore, RecentlyViewedStore, Contact, FAQ
  public/               logo, icon, ảnh fallback, tài nguyên artwork
```

Tên file cụ thể theo CLI đã ghim; cây trên thể hiện trách nhiệm. Không gom lại thành một `AppService` chứa toàn bộ ứng dụng hoặc copy registry DOM cũ sang Angular.

### Component và Service

- **Component** là một phần màn hình: ProductCard nhận sản phẩm và phát sự kiện chọn/xem; không tự sửa giỏ/database. Trang danh sách/chi tiết điều phối việc đọc API.
- **Service API** chia theo backend: CatalogApi, AuthApi, ProfileApi, AddressApi, ShippingApi, OrderApi, PaymentApi, ReviewApi, ReturnApi, InventoryApi, ReportApi, UserAdminApi. Khai báo DTO đúng JSON hiện có; mapper chuyển sang tên camelCase nếu cần, không đổi JSON gửi backend.
- **Store/facade feature** quản lý trạng thái: `idle/loading/ready/empty/error`, data/error/query/selected SKU; CartStore và CheckoutAttempt sống qua chuyển route, reset theo actor. Không coi `data=[]` là đang tải.
- Tách layout cửa hàng/auth/portal để giữ theme, header và CSS riêng. AuthArtworkComponent quản lý Three.js trong lifecycle, dispose renderer/listener/frame khi rời trang; giữ fallback và reduced-motion.
- Modal giữ semantics dialog, focus vào/ra, Escape, tên truy cập, khóa nền khi cần. Ảnh minh chứng private đọc dạng Blob, tạo Object URL và revoke khi đóng/đổi user.

### Router và form

- Route Angular dự kiến: `/`, `/san-pham/:id`, `/login`, `/register`, `/tai-khoan`, `/don-hang`, `/tro-giup`, `/doi-tra`, `/portal/...`; khi đóng gói cùng Spring chúng nằm dưới **`/angular/`**. Giữ giỏ/checkout dạng dialog trong các giai đoạn đầu để không redesign.
- Dùng query params cho các bộ lọc đang có, cập nhật URL có chủ đích; Back/Forward khôi phục dữ liệu/lựa chọn. Anchor kệ cuộn sau khi dữ liệu ready và đo header. Có adapter cho hash cũ, đặc biệt `#orders?payment_status=...` của VNPay.
- Auth guard chờ hoàn tất bootstrap `/users/me` trước khi quyết định. Role guard điều hướng/ẩn menu phù hợp; backend tiếp tục kiểm tra quyền. Guard không phải hàng rào bảo mật. [Hướng dẫn guards của Angular](https://angular.dev/guide/routing/route-guards).
- Typed Reactive Forms cho auth, hồ sơ, GHN, checkout và catalog admin; FormArray cho item/thông số/ảnh. Đồng bộ validator với DTO, hiển thị lỗi backend vào field phù hợp.
- Nút guest login luôn `type="button"`, không chạy validation checkout. Chỉ submit đơn khi CUSTOMER và form hợp lệ; không dùng `novalidate` như cách bỏ yêu cầu người nhận.
- Đổi tỉnh reset quận/phường/cước; đổi quận reset phường/cước; đổi kho tính lại cước. Hủy/bỏ phản hồi quote cũ nếu người dùng đổi địa chỉ nhanh.

### HTTP interceptor và request đồng thời

- `authInterceptor`: chỉ gắn JWT cho **API StockFlow được cho phép**, không gắn vào URL ảnh, Google SDK, VNPay hoặc GHN bên ngoài. Dùng HttpContext để đánh dấu request công khai/auth không dùng token.
- `errorInterceptor`: chuẩn hóa lỗi; 401 private kết thúc phiên một lần, giữ return URL nội bộ; 403 `EMAIL_NOT_VERIFIED` do AuthService mở OTP, các 403 khác báo quyền. 409/429 để feature xử lý; không đoán mọi 409 là hết tồn.
- `requestPolicy`: deadline cho GET, loading theo feature; không ép JSON Content-Type cho FormData, không parse 204 như response có body. Không tự retry POST/PATCH/DELETE hoặc gán Idempotency-Key cho mọi API.
- `switchMap` cho tìm kiếm/địa chỉ/route; hủy subscription khi component mất hoặc actor đổi; reset cache private. POST đã gửi có thể vẫn được backend thực hiện dù client hủy, nên retry đặt hàng phải dùng đúng key đã lưu.
- Feature thanh toán kiểm tra URL trả về và đọc lại đơn; interceptor không tự ký/gửi request tới gateway hoặc xác nhận thành công.

## 6. API tái sử dụng và điểm cần xử lý

**Không thấy nhu cầu bắt buộc đổi contract nghiệp vụ để bắt đầu Angular.** Các nhóm API ở mục 3 có thể tái sử dụng qua HttpClient. Những điểm dưới đây cần adapter, cấu hình phục vụ frontend hoặc quyết định triển khai; chưa thực hiện thay đổi nào.

1. **Tên field không đồng nhất:** JSON phần lớn snake_case, query catalog/report camelCase, địa chỉ GHN là `ProvinceID`, `DistrictID`, `WardCode`. Ward code phải là string. Dùng DTO/mapper riêng, không đổi tên field tùy tiện.
2. **Response khác hình dạng:** PageResponse có `content,page,size,total_elements,total_pages,last` (page từ 0), không phải Spring Page với `number/first`. ReviewPage có điểm trung bình; category/branch/location là array; address/return là Map; ảnh là bytes; một số DELETE trả 204, VNPay return trả 302. Fixture phải khớp controller thật.
3. **Model/phiên bản/SKU:** lọc kệ theo grouped; giá và tồn của SKU đã chọn. Giữ `archived` và effective specifications; không chọn cấu hình ngừng bán chỉ vì còn ID trong cache.
4. **Money và thời gian:** BigDecimal do backend quyết định; Angular chỉ tính tổng dự kiến/định dạng VND, không gửi `unit_price/total_amount`. ISO Instant hiển thị đúng múi giờ; không đổi kỳ báo cáo UTC theo giờ trình duyệt.
5. **CORS:** không tìm thấy cấu hình CORS/CrossOrigin trong backend hiện tại. Local Angular dùng proxy; deploy cùng origin không cần mở CORS. Nếu tách domain, phải thiết kế allowlist/method/header/credential ở backend, không chữa bằng wildcard hoặc chỉ đổi Angular environment.
6. **Google nonce cookie:** HttpOnly, SameSite Strict, path `/api/v1/auth/google`. Dùng cùng-origin/proxy, giữ hai request config/login có cookie; bổ sung origin `http://localhost:4200` trong Google Console khi test Angular. Không nhầm đây là OAuth callback cần client secret. Khác domain phải kiểm chứng cookie trước khi chọn phương án đó.
7. **Route và SecurityConfig:** hiện chỉ cho public frontend cũ; chưa permit `/angular/**` và chưa forward Angular deep links. Khi deploy sẽ cần cấu hình GET assets/SPA routes ở prefix riêng, giữ method security/API; không forward `/api/**` hoặc asset thiếu thành HTML 200.
8. **VNPay redirect:** contract hiện ghép `VNPAY_STOREFRONT_URL` với `#orders?...`, không tự động thành Angular `/don-hang`. Viết adapter đọc hash, kiểm tra ID/status, đợi auth rồi GET đơn trước khi đổi cấu hình URL. Giữ backend Return/IPN và chữ ký; không sửa thuật toán trong lượt chuyển frontend.
9. **PATCH và archive:** null/bỏ qua/chuỗi rỗng/mảng rỗng có ý nghĩa khác nhau; form dirty và mapper phải giữ khác biệt. Xóa màu/phiên bản không xóa lịch sử hoặc cho tái dùng SKU tùy ý.
10. **Storage giữa hai frontend:** localhost:4200 và localhost:8080 là khác origin, không dùng chung sessionStorage. Deploy cùng domain có thể cùng storage, nhưng không nên để hai app tự ghi chung giỏ/key. Có kế hoạch chuyển dữ liệu rõ ở mục 7.
11. **Quyền và lỗi:** `/users/me` không trả toàn bộ phân công kho; portal dùng `/warehouses/operating-options`. JWT hết hạn/đổi mật khẩu/khóa tài khoản có thể trả 401. Không tạo button đăng nhập demo hoặc tự cấp quyền chỉ để test frontend dễ hơn.
12. **GHN, mail và refund:** fallback/mode do backend quyết định; không gán tên “thật” cho vận đơn mô phỏng. Angular không gọi GHN/mail trực tiếp, không đưa token nhà cung cấp vào bundle; chưa hứa thêm refund thật hay đồng bộ wishlist server.
13. **Nội dung/ảnh:** mô tả hiện là văn bản tối đa 5.000 ký tự, gallery tối đa 8 ảnh bổ sung; hiển thị bằng binding văn bản, giữ xuống dòng, không coi mô tả là HTML được tin cậy. Dữ liệu catalog local và Render độc lập; chuyển framework không seed lại sản phẩm hoặc tự thay nội dung live.

Nếu phát hiện API thiếu khi triển khai, ghi issue có request/response hiện tại, ảnh hưởng frontend cũ và test cần thêm; thảo luận thay đổi tương thích riêng trước khi sửa. Chuyển framework tự nó không đòi hỏi migration database.

## 7. Chạy song song và giữ thiết kế

### Quy tắc chuyển từng phần

1. Frontend cũ vẫn là mặc định. Angular ban đầu là bản local riêng; chức năng chưa chuyển có liên kết quay về frontend cũ bằng điều hướng đầy đủ, không tải `app.js` cũ vào cây Angular.
2. Không tách một lần checkout giữa hai app: **giỏ + checkout + tạo đơn + payment return + lịch sử** phải đạt cùng gate trước khi đưa khách sang luồng mua Angular. Ở giai đoạn catalog có thể thử UI chọn SKU, nhưng mua thực tế vẫn ở frontend cũ.
3. Dùng storage key riêng có version cho Angular, giữ cùng ý nghĩa owner/SKU/quantity/selected/kho/idempotency. Theme có thể chia sẻ `stockflow.web.theme` khi cùng origin. Không sao chép credential hoặc thông tin nhận hàng vào localStorage.
4. Nếu cần nhập giỏ cũ lúc chuyển chính thức: làm một lần, có chủ giỏ rõ ràng, kiểm tra schema và đọc lại SKU/kho từ API; không nhập giá lưu sẵn, giỏ actor khác hoặc cho hai frontend cùng chỉnh một bản lưu. Việc nhập token, nếu được chọn, phải gọi `/users/me` trước; mặc định đăng nhập lại ở Angular để giảm rủi ro.
5. Giữ bảng đối chiếu chức năng/ảnh/test qua từng gate. Nếu gate chưa đạt, tiếp tục frontend cũ; rollback đổi link/URL về cũ, không rollback dữ liệu đơn hoặc Flyway.

### CSS và tài nguyên

- Dùng lại logo light/dark, màu xanh, nội dung thực tế, CSS tokens và các nguồn CSS hiện tại; không tạo một thiết kế Angular Material mới thay giao diện.
- Giai đoạn đầu cấu hình Angular đọc CSS nguồn theo **đúng thứ tự manifest**, rồi các stylesheet bổ sung như shell hiện tại. Kiểm tra URL `url(...)`, tài nguyên `/assets/` và public assets khi chạy 4200/prefix `/angular/`; tránh hardcode đường dẫn asset về root app cũ.
- Tách dần style riêng component sau khi so sánh ảnh; giữ styles toàn cục cần thiết cho body/theme/dialog/layout. Chú ý Angular style encapsulation làm selector xuyên qua component khác có thể không còn áp dụng.
- Giữ nền Arctic Frost/Phantom Arc hiện có, auth 3D, header xanh và dòng thông tin không có nút pause/demo, theme cho cửa hàng/auth/portal. Giữ tên thẻ hai dòng, ẩn SKU trên kệ, nhãn “Chọn phiên bản”, nút mobile dễ đọc, ảnh fallback và toast thêm giỏ gọn.
- Hiện có hiệu ứng reveal khi cuộn đã được khôi phục theo yêu cầu; chuyển thành directive có focus/reduced-motion tương đương, không dùng script querySelector toàn trang cũ. Giữ carousel, lightbox, menu danh mục khi đã cuộn và offset header.
- Nhóm liên hệ/lên đầu trang hiện nằm trong footer **dưới 1440 px**, chỉ nổi ở màn hình lớn; giữ để không che giá/nút mua hoặc thanh mua mobile.
- CSS của frontend cũ vẫn build bằng `node scripts/build-storefront.cjs` và kiểm tra `--check`. Angular có bundle riêng; không ghi đè `static/styles.css` hoặc shell cũ. Nếu sau này chỉnh nguồn CSS chung, build lại và kiểm tra cả hai frontend.

## 8. Các giai đoạn và tiêu chí bàn giao

Mỗi giai đoạn có unit/component test và kiểm chứng với backend thật phù hợp. **Test mock HTTP chỉ chứng minh frontend xử lý request/response; không chứng minh tồn kho, quyền, ledger hoặc gateway thật.** Không bỏ test cũ để đạt PASS.

### Giai đoạn 1 — Khởi tạo và catalog qua API thật

**Phạm vi:** tạo `frontend-angular/`, phiên bản ghim, HttpClient/proxy, StorefrontLayout, header/footer/theme; trang home/list/detail, bộ lọc/query, phiên bản/màu, gallery/zoom, availability, bán chạy, yêu thích/đã xem và đọc review. Chưa chuyển đặt hàng. Tiểu bước đầu tiên chỉ cần shell + list/detail đọc thật để có mốc nhỏ dễ hiểu.

**Hoàn thành khi:** danh sách/chi tiết lấy từ backend local; chọn SKU đúng giá/ảnh/thông số; loading/empty/error có thử lại; F5/deep link/Back/Forward/query và anchor kệ đúng; không sửa app cũ. Bố cục giữ ở 320/375/414/768/1366 px, sáng/tối/reduced-motion, bàn phím và ảnh thật.

**Kiểm tra:** mock CatalogApi bằng HttpTestingController: query, pagination, thiếu ảnh, 404/503/timeout, đổi route khi GET chậm, không GET dư khi cùng dữ liệu ready. Sau đó dùng Chrome/E2E đọc API Spring local thật, đối chiếu model/SKU/giá/chi nhánh; thử mua qua frontend cũ vẫn hoạt động. Mốc này không chứng minh tạo đơn Angular.

**Cần học:** TypeScript là JavaScript có kiểu để phát hiện sai field; component/template/input/output giống chia màn hình thành các phần; dependency injection giúp component nhận service; Observable là luồng kết quả API; `switchMap` bỏ kết quả tìm kiếm cũ; Router và query params giữ URL khi lọc/F5.

### Giai đoạn 2 — Auth, hồ sơ và quyền giao diện

**Phạm vi:** login/register/verify/resend, Google, forgot/reset; AuthStore/guards/interceptors/bootstrap; profile, địa chỉ mặc định/sổ địa chỉ, đổi mật khẩu; portal shell/menu theo role. Giữ auth artwork, return URL nội bộ và intent checkout để giai đoạn 3 sử dụng.

**Hoàn thành khi:** đăng ký chờ OTP, xác thực mới có JWT; EMAIL_NOT_VERIFIED mở OTP; reload kiểm tra `/users/me`; logout/401 xóa dữ liệu riêng; role-menu đúng; tài khoản vận hành demo công khai không được mở quyền. Không có credential hardcode, không bỏ qua email_verified, không đưa secret vào Angular.

**Kiểm tra:** mock phản hồi auth đặc biệt, 401/403/429, bootstrap chậm, logout trong khi GET private đang chạy, storage bị chặn, return URL bên ngoài bị từ chối. Backend test thật kiểm tra OTP 5 lần sai/mã mới nhất/cooldown/đồng thời, vô hiệu JWT khi đổi mật khẩu và phạm vi profile/địa chỉ. Dùng mail capture trong test (helper `VerificationOtpMail`), không lấy OTP rõ từ DB hash. Google test mock verifier phải ghi rõ; thử Google thật riêng trên local có origin được đăng ký và một tài khoản được phép.

**Cần học:** Reactive Forms quản lý giá trị/valid/touched; interceptor thêm header/xử lý lỗi chung; guard quyết định điều hướng; signal giữ user hiện tại; HttpOnly cookie khác JWT trong sessionStorage. Phân quyền UI giúp dễ dùng, backend mới ngăn truy cập trái phép.

### Giai đoạn 3 — Giỏ, checkout, payment, lịch sử và sau bán hàng

**Phạm vi:** chuyển toàn bộ chuỗi mua trong cùng Angular: CartStore/dialog, selected items, khôi phục giỏ/chi nhánh, địa chỉ GHN/địa chỉ lưu sẵn/cước, tạo đơn với Idempotency-Key, COD/mô phỏng/VNPay, hash-return adapter, history/detail/polling, review và yêu cầu đổi/trả/ảnh của khách.

**Hoàn thành khi:** guest login không validate người nhận và quay lại đúng checkout; chỉ CUSTOMER được đặt; đúng SKU/kho/cước; giỏ còn item chưa chọn; F5 và retry không tạo hai đơn; không dùng response cũ của actor khác; đơn/phí thanh toán theo response backend; success hash giả không tạo trạng thái trả tiền; upload/ảnh private đúng quyền; frontend cũ vẫn mua được.

**Kiểm tra mock:** thay giá/trạng thái SKU khi restore, giỏ hỏng, quote địa chỉ trả sai thứ tự, không chọn hàng, login với form trống, 409 cước/tồn, timeout sau POST, retry cùng key, payment failed/success nhưng backend chưa xác nhận, 401 khi polling, modal/focus/thanh mua mobile và hàng chưa tích.

**Kiểm tra backend thật:** database test/local riêng và tài khoản/đơn test: tạo/giữ hàng, COD, mô phỏng, cancel/expiry, idempotency, phí hợp lệ và sửa fee bị từ chối; kiểm tra reservation/ledger qua test Java. Backend kiểm thử callback/IPN có chữ ký test, sai số tiền và thông báo lặp. Một giao dịch **VNPay Sandbox thật** là bước manual riêng có cấu hình callback/IPN hợp lệ; test callback giả lập không thay thế bước đó. GHN mock/stub không chứng minh gọi GHN thật. Không tạo đơn, dò OTP hoặc gửi mail hàng loạt trên Render.

**Cần học:** state dùng chung qua service/store, FormArray và form liên hoàn, debounce/cancel, persist storage có schema/version, idempotency để tránh đặt hai đơn khi mạng chậm, phân biệt redirect trình duyệt với IPN server, polling không ghi đè thao tác khách. Transaction/khóa/ledger vẫn học và giải thích ở Java.

### Giai đoạn 4 — Portal quản trị và vận hành

**Phạm vi:** sản phẩm/phiên bản/màu/ảnh/thông số, danh mục/hãng, kho và nhập hàng theo màn hình hiện có; queue/pack/ship/GHN/deliver/cancel/return; duyệt/nhận đổi trả; ledger/báo cáo/charts; danh sách user, status, role, phân công kho và audit. Không thêm CRUD hoặc refund API chưa có.

**Hoàn thành khi:** parity các thao tác đang có; ADMIN quản catalog/tài khoản; MANAGER xem báo cáo/ledger và xử lý được phép; WAREHOUSE_STAFF chỉ kho được giao, không sửa catalog/role hoặc đọc ledger; CUSTOMER không vào portal. PATCH không xóa dữ liệu ngoài ý muốn; archive/restore giữ lịch sử; chart khớp response, không tự tính lại doanh thu như nguồn chính.

**Kiểm tra:** mock FormArray/PATCH dirty fields, errors, trang rỗng, field missing, phân trang, thủ công tracking và response GHN theo mode; bảng mobile cuộn trong vùng bảng. Backend thật kiểm tra gọi API bằng user sai role/kho, status transition, nhập kho và callback lặp không nhân đôi ledger; role/status protections; báo cáo trên bộ dữ liệu test có kết quả biết trước. Test không dùng login demo hardcode để bỏ qua bảo mật.

**Cần học:** lazy loading chia tải theo tính năng; component bảng/form tái dùng; `CanDeactivate` nhắc form chưa lưu; mapper phân biệt null/rỗng/bỏ qua; RBAC là quyền theo vai trò, warehouse scope là phạm vi dữ liệu; chart chỉ trình bày báo cáo backend.

### Giai đoạn 5 — Kiểm thử, đóng gói và triển khai

**Phạm vi:** hoàn tất bộ kiểm thử Angular/E2E, đối chiếu thiết kế, build production và cấu hình phục vụ dưới prefix; bổ sung CI/Docker Node build stage; chạy staging/local đóng gói trước khi deploy. Chỉ đổi link mặc định/callback storefront sau gate và quyết định chuyển chính thức.

**Hoàn thành khi:** Angular unit/E2E, toàn bộ Maven và PostgreSQL tests PASS có log; frontend cũ cũng PASS; build từ checkout sạch bằng lockfile; JAR/image chứa cả hai app; public static assets và deep links Angular không 401/404, API sai đường dẫn vẫn lỗi JSON/404; không có secret trong bundle/image log. Có checklist rollback link/URL, cấu hình và mốc commit.

**Kiểm tra:** ảnh thật 320/375/414/768/1366 px sáng/tối/reduced-motion, desktop lớn cho nút liên hệ nổi, mobile detail/checkout, keyboard/reader labels; đăng nhập/đăng xuất/filter/F5/Back/Forward/thêm giỏ/return portal trên cả hai app. Khi được phép deploy, trước tiên smoke test chỉ đọc trên Render; giao dịch/email/vận đơn Sandbox manual riêng có phạm vi được đồng ý. Thử điện thoại thật/Safari riêng và ghi rõ nếu chưa có thiết bị.

**Cần học:** `npm ci` dùng lockfile để build lặp lại; dev proxy khác server production; Angular build tạo file tĩnh; Docker multi-stage giữ Node ở bước build; CI tách test mock/frontend và backend/PostgreSQL; deploy code không tự đồng bộ dữ liệu local sang database Render.

## 9. Chạy local và phiên bản

### Phiên bản đề xuất

Tài liệu Angular đã đối chiếu liệt kê Angular 22.0.x trong nhóm được hỗ trợ, tương thích Node `^22.22.3 || ^24.15.0 || ^26.0.0`. Máy khảo sát có Node **v24.21.0**; có thể chọn Angular 22.x, kiểm tra ma trận cho minor/patch cụ thể lúc khởi tạo, lưu lockfile và ghim Node dùng trong CI/Docker. Không lấy phiên bản bất kỳ từ một khóa học cũ hoặc tự nâng major giữa chừng. [Ma trận Angular/Node/TypeScript/RxJS](https://angular.dev/reference/versions).

### Hai terminal, hai frontend

Terminal 1: backend PostgreSQL **local**, Java 17 và biến môi trường hiện có. JWT_SECRET phải là khóa riêng đủ độ dài; dùng IDE environment hoặc môi trường shell, không đưa giá trị vào tài liệu. Spring chạy trực tiếp không tự đọc `.env` Docker. Nếu dùng PostgreSQL Compose thì khởi động **chỉ postgres**, tránh chạy thêm app container chiếm cổng. Compose hiện kiểm tra JWT_SECRET khi đọc toàn bộ file, kể cả chỉ khởi động postgres; cung cấp biến qua `.env` riêng hoặc môi trường Compose. Biến trong `.env` đó không tự chuyển thành environment của Maven chạy ngoài Docker.

```powershell
Set-Location D:\IdeaProjects\Stockflow
# Khi dùng PostgreSQL Compose; bỏ bước này nếu PostgreSQL local đã chạy:
docker compose up -d postgres
.\mvnw.cmd "-Dmaven.repo.local=C:/Users/Admin/.m2/repository" "-Dspring-boot.run.profiles=demo" spring-boot:run
```

Đối chiếu DB_HOST/DB_PORT/DB_NAME/DB_USERNAME/DB_PASSWORD với database local, không sao chép cấu hình Aiven vào môi trường test. Backend mặc định `http://localhost:8080`, trừ khi biến `PORT` đã đặt giá trị khác. Giữ cấu hình seeder hiện có, không bật lại seed catalog chỉ để chuyển framework.

Terminal 2, **chỉ thực hiện ở giai đoạn triển khai được chọn sau tài liệu này**:

```powershell
Set-Location D:\IdeaProjects\Stockflow
# Khởi tạo một lần; chọn/ghim patch CLI theo ma trận trước khi dùng thực tế:
npx --package @angular/cli@22 ng new stockflow-web --directory frontend-angular --routing --standalone --style=css --strict --skip-git --ssr=false --defaults
Set-Location frontend-angular
# Các lần sau, cài theo package-lock.json đã lưu:
npm ci
npx ng serve --port 4200 --proxy-config src/proxy.conf.json
```

`frontend-angular/src/proxy.conf.json` dự kiến:

```json
{
  "/api/**": {
    "target": "http://localhost:8080",
    "secure": false
  }
}
```

Angular gọi URL tương đối **`/api/v1/...`**. Proxy dev giữ nguyên path và chuyển request sang Spring; không rewrite mất `/api/v1`. Thay target nếu backend local dùng cổng khác, restart `ng serve` khi đổi proxy. Không proxy `/` vì Angular cần phục vụ shell riêng. [Proxy của Angular CLI](https://angular.dev/tools/cli/serve).

- Frontend cũ: `http://localhost:8080/`; Angular: `http://localhost:4200/`; Swagger: `http://localhost:8080/swagger-ui.html`.
- Hai cổng khác nhau không đụng nhau và không tự chia sẻ giỏ/token. Chỉ kết nối Angular với **backend local**, không trỏ máy dev vào Render để thử các thao tác ghi.
- Google: thêm origin 4200 phù hợp vào Google Console, lấy config/nonce và gửi credential qua proxy. Giữ cookie path/SameSite; kiểm tra request thật trong DevTools.
- Test thanh toán Angular ở giai đoạn 3 cần quyết định `VNPAY_STOREFRONT_URL=http://localhost:4200/` sau khi adapter đã có. `VNPAY_RETURN_URL` vẫn trỏ callback **backend**; IPN phải đến backend truy cập được từ gateway. Nếu dùng tunnel, ngrok/tunnel chỉ là đường vào backend, không làm thay chức năng Angular.

## 10. Đóng gói phù hợp Render

### Phương án khuyến nghị: cùng Spring Boot, prefix `/angular/`

1. Bổ sung Node build stage sau khi được phép triển khai: `npm ci`, Angular production build với base href `/angular/`. Kiểm tra `angular.json`/output thực tế để copy thư mục chứa `index.html`, JS/CSS browser, không copy nhầm thư mục cha hoặc output SSR.
2. Stage Maven copy bản Angular đã build vào static resources **trong image/build staging dưới `angular/`**, trước package JAR. Giữ nguyên index/app/assets của frontend cũ. Không cần commit `node_modules`/`dist`/generated Angular bundle vào source backend.
3. `.dockerignore` bổ sung loại `frontend-angular/node_modules`, `frontend-angular/dist` và test artifact; vẫn đưa source/package-lock cần build vào context. Runtime giữ Java JRE; không chạy `ng serve` hoặc Node SSR trên Render.
4. Cấu hình Spring phục vụ GET `/angular/` và Angular route allowlist về `/angular/index.html`, public GET assets của prefix. History Router cần fallback khi F5/deep link; asset thiếu phải 404, `/api/**` không bị fallback. Đây là cấu hình phân phối frontend cần thực hiện **sau này**, không đổi quyền API. [Angular deployment/deep links](https://angular.dev/tools/cli/deployment).
5. Frontend cũ ở `https://stockflow-tbsw.onrender.com/`; Angular thử ở `https://stockflow-tbsw.onrender.com/angular/`; API vẫn `/api/v1`. Các link asset phải phù hợp prefix; tránh trùng global ID/listener giữa hai app vì chúng không cùng chạy trong một document.
6. Chưa đổi database, migration, JWT_SECRET, GHN/mail/merchant secret vì chuyển framework. Giữ datasource Render hiện có. Source Docker hiện dùng `0.0.0.0` và `${PORT:8080}`; tiếp tục nhận PORT Render, không chạy thêm public cổng 4200. Render mặc định PORT 10000; người dùng truy cập HTTPS domain, không thêm `:10000` vào URL. [Render Web Services](https://render.com/docs/web-services).
7. Chỉ khi toàn bộ luồng mua Angular đạt gate: cân nhắc `VNPAY_STOREFRONT_URL=https://stockflow-tbsw.onrender.com/angular/`; Return/IPN vẫn backend. Khi đó Return của giao dịch frontend cũ cũng có thể về Angular vì cấu hình chung; cần đảm bảo Angular đọc được đơn sau login và có phương án quay lại cũ. Nếu chưa đảm bảo thì giữ URL cũ, chưa đưa checkout Angular làm mặc định.
8. Google vẫn cùng HTTPS origin hiện có nếu dùng prefix; không thêm path vào danh sách JavaScript origins. Các giá trị nhạy cảm chỉ giữ phía backend/Render Environment. Angular environment/config được tải công khai, chỉ chứa API base/path hoặc feature flag, không chứa secret. [Angular environments](https://angular.dev/tools/cli/environments), [Render Docker](https://render.com/docs/docker).
9. CI thêm Node được ghim, `npm ci`, test và build Angular; giữ toàn bộ Maven/PostgreSQL test. Dockerfile hiện bỏ qua test khi package, vì vậy phải có CI PASS trước deploy và kiểm tra cấu hình Auto-Deploy thực tế trên dashboard lúc triển khai.

### Phương án khác: Angular Render Static Site riêng

Có thể build Angular thành static site và gọi Spring Web Service hiện có. Khi đó cần allowlist CORS ở backend, origin Google mới, kiểm chứng nonce cookie/credential khác origin, URL payment storefront mới và rewrite SPA `/* → /index.html` cho static site. Rewrite của Render Static Site **không áp dụng thay cấu hình Spring trong Docker Web Service**. Phương án này nhiều cấu hình hơn, chưa phải lựa chọn mặc định cho lượt học/chuyển dần. [Render Static Site rewrites](https://render.com/docs/redirects-rewrites).

## 11. Chiến lược kiểm thử và bằng chứng

### A. Frontend với API giả lập

Angular unit/component/service dùng `provideHttpClientTesting` và HttpTestingController; mock đúng DTO, URL, query, header, lỗi và response đặc biệt. Dùng E2E runner phù hợp để kiểm tra trình duyệt/route/ảnh/keyboard/modal. Fixture không được tự thêm field để che sai contract. [Angular HTTP testing](https://angular.dev/guide/http/testing).

Giữ các script frontend cũ; thêm kiểm thử Angular riêng hoặc phần tái dùng độc lập framework. Các script cũ dựa vào ID/handler/DOM Vanilla JS nên không thể coi chạy chúng là đã kiểm thử Angular. Không xóa kiểm thử nghiệp vụ chỉ vì selector thay đổi.

### B. Backend thật và PostgreSQL

Chạy API Spring với database test/local riêng, dịch vụ ngoài mock/stub; xác nhận JSON thực tế và nghiệp vụ qua Java integration tests. H2 kiểm tra phần lớn contract nhưng không thay PostgreSQL migrations/trigger/locking/concurrency. Profile `postgres-tests` dùng Testcontainers và Docker để kiểm chứng PostgreSQL.

Những bộ hiện có cần giữ gồm auth/OTP/public demo access, catalog authorization, user profile/admin, ShippingLocation/GHN, Checkout/OrderIdempotency, COD/Fulfillment, VNPay/IPN, Inventory, ProductReview/PostPurchase và PostgreSQL IT. Ghi test count thực tế từ report từng lần, không lấy số cũ trong README làm baseline.

Lệnh dự kiến khi triển khai:

```powershell
# Frontend cũ, nếu có sửa nguồn CSS chung:
node scripts/build-storefront.cjs
node scripts/build-storefront.cjs --check
node scripts/verify-storefront-ui.cjs
node scripts/verify-product-cards.cjs
node scripts/verify-portal-product-images.cjs
# Đọc catalog thật local, không ghi database:
node scripts/verify-mobile-support.cjs

# Backend, tại thư mục gốc (Docker cần cho profile PostgreSQL):
.\mvnw.cmd "-Dmaven.repo.local=C:/Users/Admin/.m2/repository" test
.\mvnw.cmd "-Dmaven.repo.local=C:/Users/Admin/.m2/repository" -Ppostgres-tests verify

# Angular, sau khi workspace đã được tạo và có test:
Set-Location frontend-angular
npm ci
npx ng test --watch=false
npm run build -- --configuration production --base-href /angular/
# E2E: thêm script/lệnh cụ thể sau khi chốt runner ở giai đoạn 1.
```

Ở các giai đoạn đầu chạy test liên quan trước, toàn suite tại gate phát hành. Lưu log, ảnh, kích thước/theme, commit và phân loại **mock API / Spring thật / PostgreSQL thật / gateway Sandbox thật**; không gom chúng thành một tuyên bố “tất cả đã kết nối thật”.

## 12. Những quyết định cần chốt và bước đầu tiên

Đề xuất mặc định để có thể bắt đầu mà không làm dự án phức tạp:

1. **Workspace:** `frontend-angular/` trong repo hiện tại; Angular standalone CSR, CSS hiện có, chưa Material/NgRx/SSR.
2. **Phiên bản:** Angular 22.x được kiểm tra tương thích lúc tạo; Node 24 tương thích được ghim, package-lock được lưu. Chốt patch và E2E runner trước khi làm giai đoạn 1.
3. **Chạy/deploy:** local 4200 + proxy 8080; sau gate build cùng Spring dưới `/angular/` dùng History Router/fallback prefix. Root cũ chưa đổi.
4. **Session/giỏ:** keys riêng, mặc định đăng nhập lại; quyết định có nhập giỏ guest cũ một lần ở thời điểm chuyển hay không. Không chia giỏ giữa hai origin hoặc hai user.
5. **Thời điểm đổi luồng mua/VNPay:** chỉ sau giai đoạn 3 đạt gate và đã kiểm chứng callback; giữ trang cũ đến khi portal và regression cũng hoàn tất. Chưa hẹn thời điểm xóa frontend cũ.

**Bước triển khai đầu tiên:** tạo workspace + proxy, StorefrontLayout tối thiểu và hai trang danh sách/chi tiết đọc **API backend local thật**, khai báo DTO PageResponse/Product/Variant đúng contract. So sánh vài model hiện có ở sáng/tối/mobile, mở trực tiếp/F5 và kiểm tra frontend cũ vẫn chạy. Chưa đụng auth/checkout, database hoặc Render ở tiểu bước này.

Khi bảo vệ/đưa vào portfolio, trình bày được chuỗi Angular Component → Service/HttpClient → REST Controller → Service transaction → Repository/PostgreSQL và chỉ ra server quyết định giá/tồn/quyền. Nộp kèm ảnh giữ thiết kế, OpenAPI, test request/response, test idempotency/ledger/concurrency và hướng dẫn local. Một luồng hoàn chỉnh có bằng chứng tốt hơn nhiều màn hình chỉ chạy mock.

## 13. Giới hạn của khảo sát này

Đã đọc mã nguồn/tài liệu trong workspace, inventory endpoint/DTO/security và đối chiếu tài liệu chính thức Angular/Render. Chưa tạo Angular, chưa cài package, chưa chạy ứng dụng/browser/Maven suite hoặc giao dịch ngoài trong lượt này; chưa xác nhận môi trường live Render/Aiven/Google/VNPay/GHN. Các kiểm thử và hình ảnh ở tài liệu frontend cũ là bằng chứng lịch sử, không phải kết quả kiểm thử migration Angular.

Chỉ file kế hoạch này được tạo trong phạm vi nhiệm vụ; bước triển khai, thay đổi cấu hình phục vụ Angular và deploy là công việc của lượt tiếp theo.
