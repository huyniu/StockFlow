<!-- Ghi nhận V11 ngày 02/10/2026: bảng thông số và nhóm màu, giữ nguyên SKU/tồn kho/đơn hàng cũ. -->
# Thông số kỹ thuật và màu sắc sản phẩm

<!-- Giữ tài liệu V11 làm lịch sử; V12 mở rộng cả phiên bản và màu theo quyết định mới của người dùng. -->
**Đây là hướng dẫn mốc V11.** Phiên bản hiện tại V12 dùng một thẻ model, chọn phiên bản rồi màu, có thông số riêng và giá từ. Xem [hướng dẫn mới](product-models-versions-and-colors.md); các giới hạn “chỉ gộp màu/dung lượng riêng” bên dưới là phạm vi V11 đã được thay thế.

## Sử dụng trong giao diện

1. Đăng nhập ADMIN → **Danh mục & Sản phẩm → Sản phẩm**.
2. **Thêm sản phẩm** hoặc **Sửa**: phần **Thông số kỹ thuật → Thêm thông số** cho phép nhập từng cặp tên–giá trị. Ví dụ: “Bộ nhớ trong — 256 GB”, “Kích thước màn hình — thông tin do cửa hàng kiểm chứng”. Tối đa 60 dòng, giữ thứ tự đã nhập. Thông số được hiển thị thành bảng trên trang chi tiết, tách khỏi mô tả.
3. Mỗi dòng sản phẩm có nút **Màu sắc**. Lần đầu, nhập màu của SKU hiện tại, rồi nhập SKU/tên màu/giá/ảnh của màu mới. Không cần tạo một thẻ sản phẩm mới trong cửa hàng. Với sản phẩm tên “IPHONE 17 PRO MAX - MÀU CAM”, nên sửa tên chung thành “iPhone 17 Pro Max 256 GB” trước khi cấu hình màu.
4. Màu mới bắt đầu với tồn kho bằng 0. Vào **Tồn kho & Nhập hàng**, chọn đúng SKU và chi nhánh để nhập hàng. Hàng của các màu không được cộng chung để bán thay cho nhau.
5. Cửa hàng hiển thị một thẻ; nút **Chọn màu sắc** mở trang chung. Chọn màu để đổi SKU/giá/ảnh, giữ số lượng và chi nhánh đang chọn. Thông số dùng chung. Giỏ và đơn chứa từng SKU màu riêng biệt.
6. Trong hộp thoại màu, ADMIN sửa giá, ảnh bìa, tối đa 8 ảnh bổ sung hoặc dừng bán riêng một màu. Không xóa vật lý SKU. Tên màu/SKU giữ nguyên sau khi tạo để giữ định danh; có thể dừng màu rồi thêm SKU màu đúng khi cần thay cấu hình.

Lượt này chỉ gộp **màu sắc**. Các bản 256 GB/512 GB vẫn là sản phẩm riêng. Không tự suy đoán chipset, RAM, hãng hay màu từ tên; không tự tìm ảnh. Dữ liệu do ADMIN nhập và tự kiểm chứng.

## Database và tương thích

V11 bổ sung hai bảng, không sửa V1–V10 hoặc ghi đè dữ liệu đã có:

- `product_specifications(product_id, position, specification_name, specification_value)`: khóa chính `(product_id, position)`, giới hạn vị trí 0–59 và chặn tên/giá trị trống.
- `product_variants(id, product_id, sku_product_id, color_name, color_key, color_hex, enabled)`: `product_id` là trang chung, `sku_product_id` là SKU thực bán. SKU gốc cũng có dòng mapping trỏ tới chính nó. Mỗi SKU chỉ thuộc một nhóm; `(product_id, color_key)` duy nhất. Mã màu tùy chọn theo `#RRGGBB`.

`products` tiếp tục là các SKU để giữ nguyên khóa ngoại của `inventories` và `order_items`. SKU gốc giữ nguyên ID, mã, giá/ảnh và lịch sử. Thêm màu sinh một SKU mới phía sau và mapping vào trang chung; không sinh tồn hoặc ledger. SKU con sao chép dữ liệu chung để các API cũ vẫn đọc được; sửa tên/hãng/mô tả/thông số sản phẩm gốc đồng bộ nội dung chung nhưng giữ giá/ảnh/tồn riêng của màu.

Đây là lựa chọn tương thích cho code đang hoạt động, chưa tách một bảng product-model độc lập khỏi toàn bộ SKU. Không tự ghép hai sản phẩm hiện có theo tên, không chuyển dòng tồn hoặc sửa lịch sử để gộp hàng. Ví dụ iPhone 17 và iPhone 18 đã nhập vẫn là hai sản phẩm khác nhau. Gộp các SKU cũ đã có sẵn hoặc mở rộng cả màu+dung lượng cần thiết kế và kiểm chứng riêng.

## API

- `POST /api/v1/products`, `PATCH /api/v1/products/{id}` nhận `specifications: [{name, value}]`; chỉ ADMIN.
- `GET /api/v1/products/{id}` và danh sách trả `specifications`, `variants`, `parent_product_id`. SKU con có `parent_product_id`; cửa hàng đọc trang gốc và chọn đúng SKU khi mở trực tiếp URL SKU con.
- `GET /api/v1/products?grouped=true` trả một trang sản phẩm chung thay vì các SKU màu riêng; điều kiện loại SKU con chạy tại SQL trước COUNT/LIMIT. Mặc định `grouped=false` giữ danh sách SKU cho các API/phiếu nhập hiện có. Tìm SKU/tên màu với `grouped=true` trả trang chung tương ứng.
- `POST /api/v1/products/{id}/variants` tạo màu, trả 201 và toàn bộ trang chung; chỉ ADMIN.
- `PATCH /api/v1/products/{id}/variants/{variantId}` sửa giá/ảnh/trạng thái màu, trả 200; chỉ ADMIN. `variantId` là ID mapping, không phải ID SKU.
- Nhập kho/đặt hàng tiếp tục nhận `product_id = variants[].sku_product_id`. Không gửi `variants[].id` vào đơn hàng.

Ví dụ thêm màu đầu tiên cho một sản phẩm đã có:

```json
{
    "default_color_name": "Cam",
    "default_color_hex": "#ea580c",
    "sku": "IP17-256-XANH",
    "color_name": "Xanh",
    "color_hex": "#2563eb",
    "unit_price": 26000000,
    "image_url": "/assets/products/ip17-xanh.jpg",
    "image_urls": []
}
```

Mã/giá/link chỉ là ví dụ; file ảnh cần có thật hoặc dùng URL ảnh hợp lệ. Các lần thêm màu sau không cần `default_color_name`. Bỏ qua/null giá kế thừa giá SKU gốc; ảnh màu mới để trống không tự dùng ảnh màu khác.

POST thông số bỏ qua/null lưu mảng rỗng. PATCH bỏ qua/null giữ nguyên, `[]` xóa toàn bộ bảng. Nhãn trùng sau trim/không phân biệt hoa thường trả 400; tên tối đa 100 ký tự, giá trị tối đa 1.000. Giá/ảnh của màu tuân thủ validation tương ứng; màu/SKU trùng trả 409. PATCH màu bỏ qua/null ảnh giữ nguyên; chuỗi trống xóa ảnh bìa, `[]` xóa ảnh bổ sung.

Giá ở thẻ/lọc/sắp xếp theo giá là **giá SKU gốc**, giữ semantics của catalog hiện có. Mỗi lựa chọn màu hiển thị giá thật riêng, đơn hàng lấy giá SKU đã chọn từ server. Chưa có lọc giá theo khoảng giá thấp nhất/cao nhất của toàn nhóm hoặc bộ lọc thông số chuyên biệt.

## Phân quyền, transaction và tồn kho

- Tạo/sửa màu và nội dung chung dùng khóa `PESSIMISTIC_WRITE` trên cùng sản phẩm gốc; tất cả nằm trong một transaction. Chặn nhóm lồng nhau, màu thuộc sản phẩm khác và tạo màu cho sản phẩm đã ẩn. `UNIQUE` tại database bảo vệ SKU/màu ngay cả khi nhiều ADMIN tranh chấp.
- ADMIN có quyền catalog; CUSTOMER/MANAGER/STAFF gọi API sửa màu bị chặn 403. Quyền kho, ownership đơn, JWT và các API fulfillment giữ nguyên.
- `products.status` ở trang gốc là trạng thái của cả sản phẩm. `variants[].status` là trạng thái mua của từng màu; màu gốc có thể ngừng bán mà các màu khác vẫn bán. Backend kiểm tra `enabled` và trạng thái trang gốc trước reserve, kể cả request gửi trực tiếp ID SKU.
- Giữ atomic conditional update, thứ tự inventory ID tăng dần và rollback toàn đơn nhiều SKU. Thanh toán DISPATCH đúng SKU; hủy/trả hoàn đúng SKU và ghi movement mới, không xuất lần hai khi ship.
- Không cho sửa catalog chung qua SKU con; dùng sản phẩm gốc hoặc API màu. Không sửa/xóa movement đã ghi.

## Kiểm chứng ngày 02/10/2026

- Maven dùng `-Dmaven.repo.local=C:/Users/Admin/.m2/repository`: **388 test PASS**, 0 failures/errors/skipped. Có **35 test mới**: 32 API/transaction/concurrency và 3 migration V10→V11.
- JAR build thành công. Test đi qua HTTP/JWT thật, Bean Validation và persistence; ca rollback/concurrency chạy không có transaction bao ngoài của test để kiểm tra commit/rollback thực.
- Chrome headless: **86 kiểm tra PASS** (44 thông số/màu, 42 hồi quy mua hàng/fulfillment), 0 exception JavaScript. Kiểm tra desktop 1440 px và mobile 390 px, HTML trong thông số được escape, đổi màu giữ số lượng/chi nhánh, giỏ hai SKU, payment/return đúng màu, URL SKU con và reload.
- PostgreSQL **13.2**: bản sao QA nâng V10→V11 giữ nguyên **13 nhóm snapshot**, gồm **5 sản phẩm đã nhập**, ảnh, tồn, ledger, đơn và tài khoản. Hai bảng mới ban đầu rỗng. 12 request đồng thời tranh ba chiếc xanh chỉ tạo ba đơn (201), chín request còn lại 409; hủy lặp không tăng tồn. Trigger thật chặn UPDATE/DELETE ledger trong transaction thử luôn rollback.
- Database `stockflow` tại 5432 và app cổng 8080 không bị migrate/ghi dữ liệu bằng helper QA. Khởi động lại ứng dụng để Flyway áp dụng V11, rồi **Ctrl + F5** để tải giao diện mới.
- Đối chiếu cuối lượt: database thật vẫn ở V10, cả 13 nhóm snapshot và 5 sản phẩm giữ nguyên. Helper Java/Chrome đã dừng; database QA đã được dọn, backup và kết quả kiểm chứng vẫn giữ lại.
- Backup trước QA: `target/db-backups/product-options-20261002_093650/stockflow.dump`. Log, snapshot và ảnh trong `target/product-options-*`, `target/web-qa/product-options-*`; không commit artifact hoặc dữ liệu này.

## Giới hạn và việc tiếp theo

Chưa gộp dung lượng/RAM, chưa có merge SKU cũ, upload file, lọc theo thuộc tính kỹ thuật, snapshot tên/ảnh/màu vào order item hoặc Testcontainers. Giá trong order item vẫn được snapshot như trước; giao diện tra tên/màu bằng SKU giữ nguyên định danh. Top Products tiếp tục thống kê từng SKU như API cũ, chưa tổng hợp các màu theo model. GitHub Actions/Docker Compose/public deployment chưa được chạy lại trong lượt này. Có thể tiếp tục gộp dung lượng nếu người dùng chốt phạm vi; luồng nhận hàng/địa chỉ checkout vẫn là phần còn lại của MVP.

## File thay đổi trong lượt này

Danh sách này chỉ tính thay đổi của lượt thông số/màu, không tính các file đã sửa từ các lượt trước:

- `README.md`
- `ANTIGRAVITY_HANDOFF.md`
- `HUONG_PHAT_TRIEN.md`
- `docs/storefront-roadmap.md`
- `docs/product-specifications-and-colors.md` (mới)
- `src/main/java/com/stockflow/catalog/api/ProductController.java`
- `src/main/java/com/stockflow/catalog/api/ProductVariantController.java` (mới)
- `src/main/java/com/stockflow/catalog/domain/Product.java`
- `src/main/java/com/stockflow/catalog/domain/ProductSpecification.java` (mới)
- `src/main/java/com/stockflow/catalog/domain/ProductVariant.java` (mới)
- `src/main/java/com/stockflow/catalog/dto/CreateProductRequest.java`
- `src/main/java/com/stockflow/catalog/dto/UpdateProductRequest.java`
- `src/main/java/com/stockflow/catalog/dto/ProductResponse.java`
- `src/main/java/com/stockflow/catalog/dto/ProductSpecificationDto.java` (mới)
- `src/main/java/com/stockflow/catalog/dto/CreateProductVariantRequest.java` (mới)
- `src/main/java/com/stockflow/catalog/dto/UpdateProductVariantRequest.java` (mới)
- `src/main/java/com/stockflow/catalog/dto/ProductVariantResponse.java` (mới)
- `src/main/java/com/stockflow/catalog/repository/ProductVariantRepository.java` (mới)
- `src/main/java/com/stockflow/catalog/service/ProductService.java`
- `src/main/java/com/stockflow/catalog/service/ProductVariantService.java` (mới)
- `src/main/java/com/stockflow/order/service/OrderService.java`
- `src/main/resources/db/migration/V11__create_product_specifications_and_variants.sql` (mới)
- `src/test/resources/db/migration/V11__create_product_specifications_and_variants.sql` (mới)
- `src/main/resources/static/index.html`
- `src/main/resources/static/app.js`
- `src/main/resources/static/styles.css`
- `src/test/java/com/stockflow/catalog/api/ProductOptionsIntegrationTest.java` (mới)
- `src/test/java/com/stockflow/catalog/ProductOptionsMigrationIntegrationTest.java` (mới)
