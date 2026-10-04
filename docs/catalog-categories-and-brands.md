<!-- Danh mục và hãng là dữ liệu thật; ghi rõ phần đã kiểm chứng và thông số còn trong lộ trình. -->
# Danh mục nhiều cấp và thương hiệu StockFlow Tech

Lượt này hoàn thiện yêu cầu **Điện thoại → các hãng** và mở rộng bốn nhóm trong ảnh CellphoneS: **Laptop**, **Âm thanh/Mic**, **Đồng hồ/Camera**, **Gia dụng/Làm đẹp**. Tên nhóm và hãng tham khảo từ ảnh người dùng cung cấp ngày 02/10/2026; không tải catalog của CellphoneS hoặc tạo model/giá/bán chạy giả.

## Cách sử dụng

1. Dừng rồi chạy lại Spring Boot trong IntelliJ; mở `http://localhost:8080/` và nhấn **Ctrl + F5**.
2. Mở **Danh mục**: cột trái là nhóm gốc, bên phải có loại hàng, hãng và khoảng giá. Di chuột hoặc dùng bàn phím để xem; bấm lựa chọn để lọc.
3. Chọn nhóm lớn để tìm cả sản phẩm trong mọi nhóm con. Chọn hãng dùng `brand_id` thật; hãng chưa có hàng trả danh sách trống.
4. Đăng nhập **Admin → Danh mục & Sản phẩm → Sản phẩm → Thêm mới**. Bộ chọn **Danh mục** hiển thị đường dẫn đầy đủ, ví dụ `Đồ gia dụng, Làm đẹp › Gia dụng nhà bếp › Nồi cơm điện`. Chọn **Thương hiệu** riêng rồi lưu.
5. Form **Sửa sản phẩm** cho phép chọn/thay hãng; bỏ chọn hãng rồi lưu gửi `clear_brand=true`. Các sản phẩm cũ ngoài danh mục iphone không được đoán hãng tự động: ADMIN cần khai báo hãng cho chúng.
6. Trong tab **Danh mục**, chọn **Thêm danh mục**, điền tên/slug và chọn cha (hoặc để trống tạo nhóm gốc). Cây tối đa ba cấp. Panel **Thương hiệu** có nút thêm hãng và danh mục gợi ý.
7. Giữ giỏ, chi nhánh, JWT và luồng xử lý đơn hiện có. Nhóm mới xuất hiện qua API sau khi lưu, không cần sửa danh sách menu trong app.js.

## Các nhóm tham chiếu

**Điện thoại** có 16 hãng: Apple (hiển thị thêm dòng iPhone), Samsung, OPPO, Xiaomi, TECNO, HONOR, nubia, Sony, Nokia, Nothing, Masstel, Huawei, Meizu, realme, itel, Infinix.

**Laptop** có hãng và các mức giá: đến 10 triệu; 10–15; 15–20; 20–25; 25–30; từ 30 triệu. Apple hiển thị thêm dòng MacBook; không tạo một hãng “Mac” riêng.

### Âm thanh, Mic thu âm

- **Tai nghe:** Tai nghe Bluetooth; Tai nghe chụp tai; Tai nghe nhét tai; Tai nghe có dây; Tai nghe thể thao; Tai nghe gaming.
- **Mic thu âm:** Mic cài áo; Mic phòng thu, podcast; Mic livestream; Micro không dây.
- **Loa:** Loa Bluetooth; Loa karaoke; Loa kéo; Loa soundbar; Loa vi tính.

### Đồng hồ, Camera

- **Đồng hồ:** Đồng hồ thông minh; Vòng đeo tay thông minh; Đồng hồ định vị trẻ em; Dây đồng hồ thông minh.
- **Camera:** Camera an ninh; Camera hành trình; Action camera; Camera AI; Gimbal; Tripod; Máy ảnh; Flycam.

### Đồ gia dụng, Làm đẹp

- **Thiết bị gia đình:** Quạt; Robot hút bụi; Máy chiếu; Máy lọc không khí; Máy hút ẩm; Máy hút bụi cầm tay; TV Box; Máy sưởi, quạt sưởi; Bàn ủi.
- **Gia dụng nhà bếp:** Nồi chiên không dầu; Nồi cơm điện; Máy xay sinh tố; Máy ép trái cây; Máy làm sữa hạt; Bếp điện; Ấm siêu tốc; Nồi áp suất; Nồi nấu chậm; Nồi lẩu điện.
- **Sức khỏe & Làm đẹp:** Máy sấy tóc; Máy massage; Máy cạo râu; Cân sức khỏe; Bàn chải điện; Máy tăm nước; Tông đơ cắt tóc; Máy tỉa lông mũi; Máy rửa mặt; Máy tạo kiểu tóc; Máy triệt lông; Máy đo huyết áp.

Âm thanh có khoảng giá đến 200 nghìn, 500 nghìn, 1 triệu, 2 triệu và 5 triệu. Tai nghe & Loa cũ được nối vào nhóm Âm thanh, vẫn giữ ID và liên kết sản phẩm.

### Hãng theo ngành hàng

- **Laptop:** Apple, ASUS, Lenovo, Dell, HP, Acer, LG, MSI, Gigabyte, Microsoft Surface, Masstel, Samsung, Colorful.
- **Tai nghe:** Apple, Sony, JBL, Samsung, Marshall, Soundpeats, Bose, Edifier, Xiaomi, Huawei, Sennheiser, Havit, Beats, Tronsmart, Anker, Shokz.
- **Loa:** JBL, Marshall, Harman Kardon, Acnos, Samsung, Sony, Arirang, LG, Alpha Works, Edifier, Bose, Tronsmart.
- **Đồng hồ:** Apple, Samsung, Xiaomi, Huawei, Coros, Garmin, Kieslect, Amazfit, Black Shark, Mibro, Masstel, imoo, Kospet, MyKID, KAVVO.
- **Camera:** Imou, Ezviz, Xiaomi, TP-Link, Tiandy, DJI, Insta360, Fujifilm, Canon, Sony, GoPro.
- **Đồ gia dụng, Làm đẹp:** Philips, Panasonic, Sunhouse, Sharp, Gaabor, Bear, AQUA, Toshiba, Midea, Dreame, Xiaomi, Cuckoo, Ecovacs, Tineco, Dyson, Roborock, Wanbo.

Tổng cộng **76 hãng độc lập**, dùng chung giữa các ngành hàng. Apple ở Điện thoại/Laptop/Đồng hồ vẫn là cùng một bản ghi. Gợi ý hãng theo nhánh giúp menu không trộn hãng loa và hãng tai nghe; sản phẩm vẫn có thể dùng bất kỳ hãng hợp lệ.

## API và phân quyền

```text
GET  /api/v1/categories
POST /api/v1/categories
GET  /api/v1/brands?categoryId={optionalId}
POST /api/v1/brands
GET  /api/v1/products?categoryId={id}&brandId={id}&minPrice=1000000&maxPrice=3000000&page=0&size=8
```

GET công khai; POST danh mục/hãng và POST/PATCH sản phẩm chỉ dành cho ADMIN. MANAGER/STAFF/CUSTOMER bị chặn 403 khi tạo hãng/nhóm. Các API nhập kho, báo cáo và đơn giữ nguyên quyền role/phạm vi kho/chủ đơn.

Tạo nhóm con:

```json
{
    "name": "Thiết bị gia đình mới",
    "slug": "thiet-bi-gia-dinh-moi",
    "parent_id": 123
}
```

Dùng ID trả về từ database của bạn; 123 chỉ minh họa. Không truyền parent_id hoặc truyền null tạo nhóm gốc. CategoryResponse giữ id/name/slug và thêm `parent_id`. Cha không tồn tại trả 404, ID không dương/cấp thứ tư trả 400. API chỉ nối nhóm mới nên không tạo chu trình; database có FK và CHECK chặn tự làm cha. Cây bị sửa sai ngoài API được bảo vệ bằng visited khi duyệt.

Tạo hãng:

```json
{
    "name": "Hãng mới",
    "slug": "hang-moi",
    "category_ids": [123]
}
```

`category_ids` tùy chọn, tối đa 100 ID dương. Danh mục không tồn tại trả 404; tên (không phân biệt hoa/thường) hoặc slug trùng trả 409. BrandResponse gồm id/name/slug/category_ids, hợp nhất gợi ý với các danh mục thật dùng hãng: gợi ý xuống hậu duệ/lên tổ tiên; dữ liệu sản phẩm chỉ mở rộng lên tổ tiên.

POST product thêm `brand_id` nullable; PATCH bỏ qua/null giữ hãng, chọn ID khác đổi hãng, `clear_brand=true` xóa hãng. Vừa đặt và xóa hãng trả 400; hãng không tồn tại trả 404. ProductResponse thêm `category_id`, `brand_id`, `brand_name`.

Bộ lọc categoryId gồm chính nhóm và hậu duệ. brandId so sánh FK, kết hợp search/status/giá/sort trước phân trang/count tại database. ID không dương trả 400; ID không tồn tại cho kết quả rỗng. Hãng không có hàng không được thay bằng tìm chuỗi tên sản phẩm.

## Migration và dữ liệu thật

- V9 PostgreSQL/H2 thêm brands, brand_categories, products.brand_id và index `products(category_id, brand_id, status)` / `brand_categories(category_id, brand_id)`.
- V9 đổi iphone thành Điện thoại tại chỗ khi chưa có nhóm đích; nếu đích tồn tại, chuyển sản phẩm sang đích rồi bỏ nhóm iphone rỗng. Chỉ hàng thuộc iphone được gán Apple. Nhóm Điện thoại, Tablet rỗng được đổi thành Máy tính bảng; nhóm ghép có sản phẩm được giữ để không đoán sai loại.
- V10 PostgreSQL/H2 thêm categories.parent_id, FK/CHECK và index parent_id. Bổ sung 70 nhóm tham chiếu (4 nhóm lớn + 66 nhóm con); giữ nhóm đã tồn tại theo tên/slug. Bảng staging migration được xóa khi hoàn tất.
- Database mới không demo: 71 danh mục (cộng Điện thoại của V9), 76 hãng, 0 sản phẩm/tồn. Profile demo thêm 5 nhóm Tech: tổng 76 danh mục. `DEMO_SEED_CATALOG=false` vẫn mặc định; không bơm sản phẩm/tồn mẫu.
- Database của người dùng `stockflow` tại `localhost:5432` đã lên **V10**, có **81 danh mục, 76 hãng và 4 sản phẩm cũ**. Nhóm Điện thoại ID 6, Máy tính bảng ID 8; hai iPhone giữ ID/SKU/giá/ảnh và thuộc Apple. Số liệu này là snapshot nghiệm thu, không thay cho số hiện tại khi chủ cửa hàng thêm hàng.
- Backup trước bước V10: `target/db-backups/catalog-expansion-20261002_081730/stockflow.dump`. Backup V8 trước bước hãng: `target/db-backups/phone-brands-upgrade-20261002_062651/stockflow.dump`. Đây là bản sao dữ liệu cục bộ trong target, không đưa lên Git.
- Đối chiếu snapshot giữ sản phẩm/ảnh/category_id, kho/phân công, tồn/movement, đơn/items/payment/shipment và tài khoản. Không sửa migration V1–V8 hoặc reset dữ liệu.

Ở bước chuẩn bị QA trước đó đã phát hiện lỗi ghép tham số helper làm khởi tạo schema vào database postgres mặc định đang trống. Đã dừng helper, sao lưu và gỡ đúng các bảng/hàm helper vừa tạo, xác nhận public trở lại trống; dữ liệu stockflow không bị xóa. Các lần chạy tiếp theo dùng URL database đầy đủ và đối chiếu tên/version trước thao tác. Log/bản sao phục hồi ở target, không còn việc khắc phục tồn đọng.

## Kiểm chứng ngày 02/10/2026

- **353 test PASS**, 0 failures/errors/skipped. Tăng 58 ca so với mốc 295: 34 API hãng, 5 nâng cấp V8→V9, 16 API cây danh mục và 3 nâng cấp V9→V10.
- Bao phủ quyền ADMIN và role khác, dữ liệu gợi ý không có hàng, hãng chung nhiều nhóm, null/clear PATCH, kết hợp hãng/nhóm/giá/search, phân trang/sort, cha thiếu/cấp thứ tư, giữ SKU/ảnh/giá/stock/ledger/orders và migration chạy lại không sinh dữ liệu.
- **Maven package BUILD SUCCESS**, `-DskipTests` sau full suite đã PASS.
- PostgreSQL QA **13.2**: nâng bản sao dữ liệu V8→V9→V10, Hibernate validate thành công; snapshot trước/sau và truy vấn API giữ dữ liệu.
- **42 kiểm tra Chrome PASS**: menu/hãng/nhóm con, lọc+phân trang, giá riêng laptop/âm thanh, empty state, trang chi tiết hãng, giỏ/chi nhánh, form thêm/đổi/xóa hãng, tạo nhóm con/hãng và escape HTML.
- **42 kiểm tra Chrome hồi quy PASS**: card/trang riêng, Back/Forward/reload, giỏ/checkout/pay, staff pack/ship/deliver/return, hoàn kho và ledger đúng, dashboard báo cáo.
- Tổng **84 kiểm tra trình duyệt**, 0 exception JavaScript. Bài hồi quy có 404 chủ động khi thử ID không tồn tại; các API khác thành công.
- Đã xem ảnh desktop 1440px/mobile 390px và form Admin; không tràn ngang. Fixture chỉ ghi trong bản sao QA riêng, không đưa vào catalog thật.
- Node syntax check, Prettier check cho ba file static và `git diff --check` đạt.

```powershell
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' test
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' '-DskipTests' package
```

Log/ảnh/script/snapshot ở target được Git bỏ qua. JAR: `target/stockflow-0.0.1-SNAPSHOT.jar`.

## Giới hạn và bước tiếp theo

Chưa có bộ lọc RAM/chip/nhu cầu/kích thước màn hình của laptop; cần nhập thông số thật và chọn mô hình dữ liệu trước phần phụ thuộc. Không tạo model “Sản phẩm nổi bật”, nhãn HOT/MỚI hoặc thống kê bán hàng của CellphoneS. Các sao/số bán minh họa hiện có của card là phạm vi cũ, chưa chuyển thành hệ thống đánh giá thật trong lượt này.

Chưa chạy Docker Compose/PostgreSQL 17, GitHub Actions hoặc Testcontainers trong lượt này; chưa đo EXPLAIN ANALYZE mới cho lọc hãng/cây danh mục. Không tuyên bố index mới đã được planner dùng khi chưa đo dữ liệu lớn. Giỏ vẫn ở bộ nhớ, bộ lọc chưa lưu thành URL chia sẻ.

## 39 file thay đổi

Danh sách tính từ trạng thái trước lượt hãng/danh mục này, không gộp các thay đổi cũ đã có trong workspace.

### File mới (16)

- `src/main/java/com/stockflow/catalog/api/BrandController.java`
- `src/main/java/com/stockflow/catalog/domain/Brand.java`
- `src/main/java/com/stockflow/catalog/dto/BrandResponse.java`
- `src/main/java/com/stockflow/catalog/dto/CreateBrandRequest.java`
- `src/main/java/com/stockflow/catalog/repository/BrandRepository.java`
- `src/main/java/com/stockflow/catalog/service/BrandService.java`
- `src/main/java/com/stockflow/catalog/service/CategoryHierarchy.java`
- `src/main/resources/db/migration/V9__create_brands_and_reclassify_phones.sql`
- `src/main/resources/db/migration/V10__expand_catalog_categories_and_brands.sql`
- `src/test/resources/db/migration/V9__create_brands_and_reclassify_phones.sql`
- `src/test/resources/db/migration/V10__expand_catalog_categories_and_brands.sql`
- `src/test/java/com/stockflow/catalog/api/BrandIntegrationTest.java`
- `src/test/java/com/stockflow/catalog/BrandMigrationIntegrationTest.java`
- `src/test/java/com/stockflow/catalog/api/CategoryHierarchyIntegrationTest.java`
- `src/test/java/com/stockflow/catalog/CategoryExpansionMigrationIntegrationTest.java`
- `docs/catalog-categories-and-brands.md`

### File sửa (23)

- `ANTIGRAVITY_HANDOFF.md`
- `HUONG_PHAT_TRIEN.md`
- `README.md`
- `docs/catalog-discovery.md`
- `docs/storefront-roadmap.md`
- `src/main/java/com/stockflow/catalog/api/ProductController.java`
- `src/main/java/com/stockflow/catalog/domain/Category.java`
- `src/main/java/com/stockflow/catalog/domain/Product.java`
- `src/main/java/com/stockflow/catalog/dto/CategoryResponse.java`
- `src/main/java/com/stockflow/catalog/dto/CreateCategoryRequest.java`
- `src/main/java/com/stockflow/catalog/dto/CreateProductRequest.java`
- `src/main/java/com/stockflow/catalog/dto/ProductResponse.java`
- `src/main/java/com/stockflow/catalog/dto/UpdateProductRequest.java`
- `src/main/java/com/stockflow/catalog/repository/CategoryRepository.java`
- `src/main/java/com/stockflow/catalog/service/CategoryService.java`
- `src/main/java/com/stockflow/catalog/service/ProductService.java`
- `src/main/java/com/stockflow/common/config/SecurityConfig.java`
- `src/main/java/com/stockflow/demo/DemoDataSeeder.java`
- `src/main/resources/static/app.js`
- `src/main/resources/static/index.html`
- `src/main/resources/static/styles.css`
- `src/test/java/com/stockflow/demo/DemoDataIntegrationTest.java`
- `src/test/java/com/stockflow/demo/ManualCatalogSeedIntegrationTest.java`

