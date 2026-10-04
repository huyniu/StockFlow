<!-- Mốc V12 theo quyết định người dùng: một thẻ model, chọn phiên bản rồi màu, giữ SKU và lịch sử cũ. -->
# Một sản phẩm chung, nhiều phiên bản và màu sắc

## Cấu trúc đã chốt

Cửa hàng hiển thị **một thẻ Apple Watch SE 3**, không tạo thẻ riêng cho mỗi kích thước hoặc màu. Bấm thẻ mở trang model; khách chọn **phiên bản**, rồi chọn **màu** có thật trong phiên bản đó. Mỗi tổ hợp tương ứng một SKU với giá, ảnh và tồn kho độc lập.

Danh mục vẫn phân loại nhóm hàng, hãng vẫn là dữ liệu thương hiệu và tên sản phẩm chung đại diện model. Ví dụ: nhóm đồng hồ → đồng hồ thông minh → hãng Apple → model Apple Watch SE 3. Cấu hình “40mm GPS · Dây cao su S/M” là phiên bản, “Ánh sao · Dây vàng cát” là màu; không biến tất cả thành các cấp danh mục. Khách có thể tìm tên model, tên phiên bản, màu hoặc SKU để thấy thẻ chung.

Tên phiên bản do ADMIN nhập, phù hợp đồng hồ, dung lượng điện thoại hoặc RAM/SSD laptop. Hệ thống không suy đoán cấu hình, màu hoặc thông số từ tên. Ví dụ dưới đây minh họa cách nhập, không cung cấp thông số/giá thương mại đã kiểm chứng.

## Thao tác quản trị

1. **ADMIN → Danh mục & Sản phẩm → Sản phẩm → Thêm sản phẩm**. Nhập tên chung, ví dụ “Apple Watch SE 3”; SKU/giá/ảnh ở bước này thuộc lựa chọn bán đầu tiên. Nhập mô tả và thông số chung cho model.
2. Tại dòng sản phẩm, chọn **Phiên bản & màu**. Với sản phẩm chưa cấu hình, nhập tên phiên bản đầu tiên và màu đúng của SKU hiện tại. Lưu giữ nguyên ID, SKU, giá, ảnh, tồn và lịch sử.
3. **Thêm phiên bản mới**, ví dụ “44mm GPS · Dây cao su M/L”. Cấu hình mới chưa có SKU; tiếp tục thêm các màu vào đúng phiên bản đang chọn. Cùng tên màu được phép ở hai phiên bản khác nhau, nhưng SKU phải duy nhất toàn hệ thống.
4. Mỗi màu có giá/ảnh/trạng thái riêng. Sau khi thêm SKU, vào **Tồn kho & Nhập hàng**, chọn đúng SKU và chi nhánh để nhập. Không tự tạo tồn hoặc movement khi chỉ khai báo catalog.
5. Chọn phiên bản bên trái để sửa tên/thông số và các màu của riêng phiên bản đó. Không sửa SKU hoặc di chuyển SKU cũ sang cấu hình khác; dừng bán một SKU giữ lịch sử và các lựa chọn khác vẫn hoạt động.

Giới hạn: 20 phiên bản/model, 30 màu/phiên bản, 100 SKU/model, 60 dòng thông số hiệu lực, tối đa 8 ảnh bổ sung/SKU. Phiên bản chưa có SKU hoặc không có SKU ACTIVE hiển thị “Chưa mở bán”, không chọn mua được.

## Thông số chung và riêng

Thông số chung nhập trong form thêm/sửa sản phẩm. Thông số riêng nhập trong phiên bản, ví dụ “Kích thước — 40mm” hoặc “Bộ nhớ trong — 512 GB”. Nếu trùng tên sau chuẩn hóa, giá trị riêng ghi đè tại cùng vị trí; nhãn mới được thêm cuối bảng. Đổi thông số chung giữ lại các ghi đè riêng.

PATCH phiên bản bỏ qua/null `specifications` giữ bảng riêng, `[]` xóa ghi đè và dùng bảng chung. Bảng chung + riêng không được vượt 60 nhãn; lỗi rollback toàn bộ cập nhật. SKU con giữ bảng hiệu lực cho client cũ, trong khi trang model trả cả bảng chung và các bảng theo phiên bản.

## Storefront và đơn hàng

- Một thẻ/model; hiển thị **giá từ** thấp nhất trong các SKU đang bán. Filter/sort giá với `grouped=true` dùng giá từ tại SQL trước COUNT/LIMIT, giữ ID làm thứ tự phụ. `unit_price` vẫn là giá SKU gốc cho client hiện có; `min_price`/`max_price` là khoảng giá lựa chọn.
- Đổi phiên bản chỉ hiển thị màu của phiên bản đó. Giữ màu trước nếu phiên bản mới có đúng màu ACTIVE; nếu không, chọn SKU còn bán đầu tiên. Giữ số lượng và chi nhánh đang chọn. Tên, SKU, giá, ảnh và thông số cập nhật theo lựa chọn.
- Giỏ tách các SKU, kể cả cùng màu nhưng khác phiên bản. Nhập kho/checkout tiếp tục gửi `product_id = variants[].sku_product_id`, không gửi ID phiên bản hoặc ID mapping màu.
- URL `/san-pham/{skuProductId}` mở trang model và chọn đúng phiên bản/màu; reload URL SKU giữ lựa chọn tương ứng. Lượt hoàn thiện trang bán lẻ cập nhật URL khi đổi lựa chọn, có Back/Forward và breadcrumb đầy đủ; không ghi giỏ vào database.
- Catalog public không công khai số tồn chi tiết từng kho. API `GET /api/v1/products/{id}/availability` chỉ báo còn/hết hàng theo SKU và chi nhánh; reserve tại backend vẫn quyết định khi đặt. Chi tiết cập nhật giao diện và kiểm chứng mới ở [Trang sản phẩm bán lẻ](retail-product-detail.md).

## API và tương thích

Chỉ ADMIN được ghi các endpoint dưới đây; CUSTOMER/MANAGER/WAREHOUSE_STAFF trả 403. Khách vãng lai tiếp tục đọc `GET /api/v1/products/{id}`.

- `POST /api/v1/products/{id}/versions`: tạo phiên bản, trả 201 và model đầy đủ. Lần đầu phải khai báo màu SKU gốc.
- `PATCH /api/v1/products/{id}/versions/{versionId}`: sửa tên/thông số, trả 200; null giữ nguyên.
- `POST /api/v1/products/{id}/variants`: thêm SKU với `version_id`, trả 201.
- `PATCH /api/v1/products/{id}/variants/{variantId}`: sửa giá/ảnh/trạng thái SKU như V11.
- `ProductResponse` thêm `versions`, `min_price`, `max_price`. Mỗi màu thêm `version_id`, `version_name`. Mỗi phiên bản trả `specifications` riêng và `effective_specifications` đã ghép.

Khởi tạo cấu hình gốc:

```json
{
    "name": "40mm GPS · Dây cao su S/M",
    "default_color_name": "Ánh sao · Dây vàng cát",
    "default_color_hex": "#e6d7bb",
    "specifications": [
        { "name": "Kích thước", "value": "40mm" }
    ]
}
```

Thêm phiên bản thứ hai chỉ cần tên/thông số. Sau đó thêm màu qua API variants:

```json
{
    "version_id": 123,
    "sku": "AW-SE3-44-GPS-STAR",
    "color_name": "Ánh sao · Dây vàng cát",
    "color_hex": "#e6d7bb",
    "unit_price": 8000000,
    "image_url": "/assets/products/watch-44-star.jpg",
    "image_urls": []
}
```

ID/giá/link chỉ là ví dụ; cần dùng ID vừa tạo và ảnh thật. Contract V11 bỏ `version_id` vẫn dùng phiên bản của SKU gốc; lần đầu thêm màu bằng API cũ tạo phiên bản trung tính “Phiên bản hiện tại”. UI mới yêu cầu ADMIN khai báo phiên bản rõ ràng.

## Migration và transaction

V12 mới cho PostgreSQL/H2, không sửa V1–V11. Thêm `product_versions`, `product_version_specifications` và `product_variants.version_id`. Nhóm màu V11 được giữ nguyên trong một phiên bản tên “Phiên bản hiện tại”; ADMIN có thể đổi tên sau. Sản phẩm chưa có màu không bị tự tạo cấu hình.

`UNIQUE(product_id, name_key)` chặn phiên bản trùng. `UNIQUE(version_id, color_key)` thay ràng buộc màu toàn model, cho phép cùng màu ở các cấu hình. Khóa ngoại ghép `(product_id, version_id)` bảo đảm màu và phiên bản cùng model, kể cả ghi SQL trực tiếp. SKU vẫn duy nhất và `products` tiếp tục là khóa ngoại của inventories/order_items.

Tạo/sửa model, phiên bản và màu dùng cùng khóa `PESSIMISTIC_WRITE` trên model và transaction. Không thay atomic conditional reserve, thứ tự inventory ID, price snapshot, ownership đơn, phạm vi kho hoặc ledger bất biến. Payment vẫn DISPATCH một lần; return hoàn đúng SKU bằng movement mới. Không tự gộp các sản phẩm cũ theo tên hoặc chuyển hàng/lịch sử giữa các SKU.

## Kiểm chứng và giới hạn

- `mvnw.cmd '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' test`: **411 PASS**, gồm 23 bài mới: 20 API/transaction/concurrency và 3 migration.
- `package` chạy lại toàn suite và build JAR thành công. JavaScript parse và HTML/CSS/JS formatting được kiểm tra.
- Chrome headless/PostgreSQL QA riêng: **81 kiểm tra PASS** (39 mới + 42 hồi quy), 0 exception JavaScript. Có admin khai báo/sửa, một thẻ chung, màu hợp lệ theo phiên bản, giá/ảnh/thông số, escape HTML, giỏ cùng màu khác cấu hình, payment/fulfillment/return, URL/reload và mobile 390px.
- Bốn kiểm tra PostgreSQL riêng cho giá từ/filter/sort/phân trang cũng PASS; SKU giá rẻ ngừng bán được loại khỏi giá từ.
- PostgreSQL 13.2 nâng V11→V12 trên bản sao giữ nguyên **13 nhóm snapshot và 6 SKU hiện có**. 12 request tranh ba chiếc cùng phiên bản chỉ có ba đơn thành công; tồn phiên bản/màu khác giữ nguyên. Database thật/app 8080 không được migrate hoặc ghi dữ liệu bằng helper QA.
- Backup trước kiểm tra: `target/db-backups/product-versions-20261002_173343/stockflow.dump`. Log/snapshot/ảnh tại `target/product-versions-*`, `target/web-qa/product-versions-*`; không commit artifact dữ liệu này.
- Đối chiếu cuối: database thật vẫn V11, 13 nhóm snapshot và 6 SKU không đổi. Helper Java/Chrome đã dừng, database QA đã dọn; giữ lại backup và kết quả kiểm chứng. Migration V1–V11 có hash như đầu lượt.

Chưa có merge SKU đã tạo độc lập, chuyển SKU giữa phiên bản, bộ lọc kỹ thuật chuyên biệt, snapshot tên/cấu hình/ảnh trong order_items, tổng hợp Top Products theo model, upload ảnh hoặc trang landing riêng cho dòng máy. Địa chỉ nhận hàng, số tồn public và Testcontainers tiếp tục trong lộ trình. Không gọi các phần chưa triển khai là đã hoàn thành.

Khởi động lại Spring Boot để Flyway áp dụng V12, sau đó Ctrl + F5. Các nhóm màu cũ giữ nguyên; đổi tên “Phiên bản hiện tại” thành cấu hình đúng rồi thêm các phiên bản tiếp theo trong quản trị.

## File thay đổi trong lượt này

Danh sách 30 file, so với source trước khi mở rộng phiên bản; không tính các thay đổi đã có từ lượt trước:

- `ANTIGRAVITY_HANDOFF.md`
- `HUONG_PHAT_TRIEN.md`
- `README.md`
- `docs/product-models-versions-and-colors.md` (mới)
- `docs/product-specifications-and-colors.md`
- `docs/storefront-roadmap.md`
- `src/main/java/com/stockflow/catalog/api/ProductController.java`
- `src/main/java/com/stockflow/catalog/api/ProductVariantController.java`
- `src/main/java/com/stockflow/catalog/api/ProductVersionController.java` (mới)
- `src/main/java/com/stockflow/catalog/domain/Product.java`
- `src/main/java/com/stockflow/catalog/domain/ProductVariant.java`
- `src/main/java/com/stockflow/catalog/domain/ProductVersion.java` (mới)
- `src/main/java/com/stockflow/catalog/dto/CreateProductVariantRequest.java`
- `src/main/java/com/stockflow/catalog/dto/CreateProductVersionRequest.java` (mới)
- `src/main/java/com/stockflow/catalog/dto/ProductResponse.java`
- `src/main/java/com/stockflow/catalog/dto/ProductVariantResponse.java`
- `src/main/java/com/stockflow/catalog/dto/ProductVersionResponse.java` (mới)
- `src/main/java/com/stockflow/catalog/dto/UpdateProductVersionRequest.java` (mới)
- `src/main/java/com/stockflow/catalog/repository/ProductVariantRepository.java`
- `src/main/java/com/stockflow/catalog/repository/ProductVersionRepository.java` (mới)
- `src/main/java/com/stockflow/catalog/service/ProductService.java`
- `src/main/java/com/stockflow/catalog/service/ProductVariantService.java`
- `src/main/java/com/stockflow/catalog/service/ProductVersionService.java` (mới)
- `src/main/resources/db/migration/V12__add_product_versions.sql` (mới)
- `src/main/resources/static/app.js`
- `src/main/resources/static/index.html`
- `src/main/resources/static/styles.css`
- `src/test/java/com/stockflow/catalog/ProductVersionsMigrationIntegrationTest.java` (mới)
- `src/test/java/com/stockflow/catalog/api/ProductVersionsIntegrationTest.java` (mới)
- `src/test/resources/db/migration/V12__add_product_versions.sql` (mới)
