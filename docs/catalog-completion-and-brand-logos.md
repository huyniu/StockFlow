<!-- Bổ sung danh mục tham khảo và logo do ADMIN nhập; không suy đoán sản phẩm, tồn kho hay hãng từ tên SKU. -->
# Hoàn thiện danh mục và logo thương hiệu

## Phạm vi

Migration V13 bổ sung các loại hàng còn thiếu trong ba ảnh tham khảo do người dùng cung cấp:

- **Tivi, Điện máy:** tivi, tivi di động, tủ lạnh, tủ đông, máy giặt, máy sấy quần áo, máy rửa chén bát, máy lạnh, giá treo tivi và tủ chăm sóc quần áo.
- **Phụ kiện:** phụ kiện di động, laptop, thiết bị mạng, thiết bị lưu trữ và phụ kiện khác. Nhánh con gồm dán màn hình, ốp lưng/bao da, SIM 4G/5G, cáp/sạc, sạc dự phòng, trạm sạc dự phòng, dây đeo điện thoại, bảo hành mở rộng, chuột/bàn phím, thiết bị Wi-Fi, hub/switch, thẻ nhớ, USB, ổ cứng, máy chơi game và quạt mini.
- **Hàng cũ:** điện thoại, máy tính bảng, Mac, laptop, máy ảnh, tai nghe, loa, đồng hồ thông minh, gia dụng, màn hình, phụ kiện, tivi, sức khỏe/làm đẹp và camera giám sát hàng trưng bày.

Hãng gợi ý được gắn vào từng loại hàng bằng ID thật. Hãng mới gồm coocaa, TCL, VSP, Daikin, Casper, Hitachi, Bosch và OnePlus. Các hãng đã có được sử dụng chung, kể cả giữa hàng mới và hàng cũ. Những liên kết model/“hàng hot” trong ảnh không được biến thành sản phẩm hoặc danh mục giả; chủ cửa hàng tự nhập model, phiên bản và SKU phù hợp.

“Bảo hành mở rộng” hiện là loại hàng tham khảo với Apple/Samsung, chưa có nghiệp vụ hợp đồng bảo hành. Danh mục “Hàng cũ” phân loại sản phẩm đã qua sử dụng; chưa bổ sung quy trình thu cũ hoặc schema chấm tình trạng máy.

## Nhập và sửa logo

<!-- Logo đã được chủ cửa hàng yêu cầu bổ sung, lưu thành tài nguyên cùng ứng dụng. -->
Ngày 03/10/2026 đã bổ sung logo cho đủ **84/84 hãng hiện có**: giữ Apple, Samsung, Sony và HP; thêm 80 ảnh từ Simple Icons, các danh mục CellphoneS và website chính thức của hãng. Ảnh nằm trong tài nguyên ứng dụng, được gán qua API ADMIN. Xem [nguồn ảnh, đường dẫn và cách thay logo](local-brand-logos.md) cùng [danh sách đủ 84 logo](brand-logo-sources.md). Hãng mới hoặc logo muốn thay vẫn sử dụng form dưới đây.

1. Đăng nhập **ADMIN**, vào **Danh mục và sản phẩm → Danh mục → Thương hiệu**.
2. Tìm hãng, bấm **Sửa logo**, dán đường dẫn ảnh trực tiếp vào ô **Ảnh / logo thương hiệu**.
3. Kiểm tra ảnh xem trước rồi bấm **Lưu logo**. Tên hãng luôn hiện bên cạnh logo trên menu cửa hàng.
4. Muốn bỏ logo: bấm **Xóa logo** rồi **Lưu logo**. Nút xóa chỉ thay bản nháp cho tới khi lưu.

Khi thêm hãng mới, form cũng có ô logo. Logo là tùy chọn; hãng chưa có ảnh vẫn sử dụng được và có chữ viết tắt trong danh sách quản trị. Dùng đường dẫn HTTP/HTTPS tới ảnh PNG/JPEG/WebP/SVG hoặc đường dẫn tài nguyên `/assets/...` đã được đặt trong ứng dụng. Đây là chức năng **nhập URL**, chưa phải tải file trực tiếp từ máy tính.

URL không an toàn hoặc quá 2.048 ký tự bị chặn. Trình duyệt tải ảnh để xem trước; backend chỉ lưu URL đã kiểm tra, không truy cập máy chủ ảnh bên ngoài. Nếu ảnh bị chặn hoặc không tải được, giao diện báo lỗi ảnh và giữ tên/chữ viết tắt, không lặp tải vô hạn. Việc kiểm tra URL không bảo đảm CDN bên ngoài luôn hoạt động.

## Contract API

- `GET /api/v1/brands`: công khai, trả thêm `logo_url` (có thể `null`); giữ các trường `id`, `name`, `slug`, `category_ids` và bộ lọc `categoryId` hiện có.
- `POST /api/v1/brands`: chỉ ADMIN; nhận thêm `logo_url` tùy chọn. Bỏ qua, `null` hoặc chuỗi trống tạo hãng không có logo.
- `PATCH /api/v1/brands/{id}/logo`: chỉ ADMIN; body bắt buộc có `logo_url` dạng chuỗi. Chuỗi trống xóa logo; bỏ trường hoặc gửi `null` trả 400 để tránh vô tình xóa ảnh. Không thay tên, slug, danh mục gợi ý hoặc các sản phẩm của hãng.
- CUSTOMER, WAREHOUSE_STAFF, MANAGER gọi API sửa logo trả 403; chưa đăng nhập trả 401. ID không hợp lệ trả 400, hãng không tồn tại trả 404.

```json
{
    "logo_url": "https://example.com/images/brand-logo.png"
}
```

## Migration và bảo toàn dữ liệu

V13 thêm cột nullable `brands.logo_url`, danh mục và liên kết hãng còn thiếu. Không sửa V1–V12, không đổi ID/tên/cây danh mục đã có và không thêm hay xóa sản phẩm, SKU, phiên bản, ảnh sản phẩm, tồn kho, movements, đơn hàng hoặc tài khoản.

Seed tránh trùng theo slug hoặc tên không phân biệt hoa/thường. Nếu danh mục người dùng trùng tham khảo nhưng nằm ở cấp khác, giữ vị trí đã nhập; không thêm con dưới cha đã ở cấp thứ ba. Bảng trung gian chỉ tồn tại trong migration và được xóa khi kết thúc. PostgreSQL và H2 có migration tương ứng; chạy lại Flyway không chèn thêm dữ liệu.

Database mới sau V13 có **125 danh mục, 84 hãng**; bật seed catalog mẫu thêm 5 nhóm công nghệ thành **130 danh mục**. Từ lượt CRUD danh mục, chế độ nhập tay mặc định không tạo lại các nhóm của runner để bảo toàn thao tác sửa/xóa khi khởi động lại. Database đã sử dụng có thể có nhiều hơn vì giữ nhóm do chủ cửa hàng tạo. Không tự gán logo từ nguồn chưa được chủ cửa hàng chọn.

## Kiểm chứng

`BrandLogoIntegrationTest` kiểm tra lưu/đọc/sửa/xóa logo, tương thích request cũ, phân quyền, URL không an toàn và bảo toàn sản phẩm/liên kết hãng. `CatalogCompletionMigrationIntegrationTest` kiểm tra nâng V12 lên V13, cây tối đa ba cấp, hãng dùng chung, không trùng seed và bảo toàn dữ liệu nghiệp vụ.

Lệnh trên Windows:

```powershell
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' test
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' -DskipTests package
```

Kiểm thử PostgreSQL/giao diện sử dụng bản sao database và JAR trên cổng riêng, không thao tác hàng hoặc đơn thử nghiệm trong database thật. Khởi động lại Spring Boot sau khi cập nhật source để nạp API logo mới, rồi dùng Ctrl+F5 để tải giao diện mới.

<!-- Kết quả thực tế ngày 03/10/2026; phân biệt test H2 với kiểm chứng migration/UI trên PostgreSQL. -->
Kết quả nghiệm thu:

- **453 tests PASS**, 0 failure/error/skipped; thêm 29 trường hợp mới. Maven test và package đều BUILD SUCCESS. Package dùng `-DskipTests` sau khi suite đầy đủ đã PASS.
- **121 kiểm tra Chrome PASS:** 40 kiểm tra menu/logo/preview/lưu/xóa/phân quyền/layout desktop và mobile 390px; 39 hồi quy phiên bản/màu/SKU; 42 hồi quy mua hàng/fulfillment/báo cáo. Không có exception JavaScript hoặc lỗi API 500 trong các bài kiểm chứng.
- PostgreSQL **13.2**: JAR nâng bản sao từ V12 lên V13, Hibernate validate thành công; Flyway chạy lại không có migration cần áp dụng.
- Database thật **stockflow, localhost:5432** đã lên V13: thêm **51 danh mục và 8 hãng**, tổng **132 danh mục/84 hãng**. Bản sao lưu trước migration: `target/db-backups/brand-logos-live-20261003_072254/stockflow.dump`.
- Snapshot trước/sau giữ nguyên 15 nhóm dữ liệu: sản phẩm, bộ ảnh, thông số, phiên bản, thông số phiên bản, SKU màu, tồn, movements, đơn, mặt hàng đơn, payment, shipment, kho, phân công và tài khoản. Danh mục/hãng/liên kết cũ được giữ nguyên. Không tự điền logo hoặc tạo sản phẩm demo.

Chưa xác minh độ ổn định của mọi CDN do ADMIN nhập; ảnh mẫu kiểm chứng dùng tài nguyên của ứng dụng để tránh phụ thuộc mạng bên ngoài. Chưa có upload file hoặc quản lý chất lượng hàng cũ. Ứng dụng đang mở tại cổng 8080 được giữ chạy nguyên trạng; API/logo mới được kiểm chứng trên JAR QA, cần khởi động lại ứng dụng đang mở để sử dụng bản Java mới.

## File thay đổi trong lượt này

<!-- Danh sách được đối chiếu hash trước lượt sửa, không gom các file bẩn từ công việc trước. -->
- `src/main/resources/db/migration/V13__complete_catalog_and_add_brand_logos.sql`
- `src/test/resources/db/migration/V13__complete_catalog_and_add_brand_logos.sql`
- `src/main/java/com/stockflow/catalog/domain/Brand.java`
- `src/main/java/com/stockflow/catalog/dto/BrandResponse.java`
- `src/main/java/com/stockflow/catalog/dto/CreateBrandRequest.java`
- `src/main/java/com/stockflow/catalog/dto/UpdateBrandLogoRequest.java`
- `src/main/java/com/stockflow/catalog/repository/BrandRepository.java`
- `src/main/java/com/stockflow/catalog/service/BrandService.java`
- `src/main/java/com/stockflow/catalog/api/BrandController.java`
- `src/main/resources/static/index.html`
- `src/main/resources/static/app.js`
- `src/main/resources/static/styles.css`
- `src/test/java/com/stockflow/catalog/api/BrandLogoIntegrationTest.java`
- `src/test/java/com/stockflow/catalog/CatalogCompletionMigrationIntegrationTest.java`
- `src/test/java/com/stockflow/demo/DemoDataIntegrationTest.java`
- `src/test/java/com/stockflow/demo/ManualCatalogSeedIntegrationTest.java`
- `README.md`
- `ANTIGRAVITY_HANDOFF.md`
- `docs/catalog-completion-and-brand-logos.md`
