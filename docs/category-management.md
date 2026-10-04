<!-- Chức năng danh mục dành cho ADMIN; không xóa dây chuyền sản phẩm, tồn hoặc lịch sử bán hàng. -->
# Thêm, sửa, xóa và tìm danh mục

Vào **Admin → Danh mục & Sản phẩm → Danh mục**:

- **Thêm danh mục:** giữ form tạo tên/slug và chọn danh mục cha, tối đa ba cấp.
- **Tìm danh mục:** ô tìm ngay trên bảng, hỗ trợ tên, slug, mã và đường dẫn cha; gõ có/không dấu và khác hoa/thường. Tìm trên cây đã tải, không gửi request theo từng phím.
- **Sửa:** nút ở mỗi dòng mở form tên/slug đã lưu. Giữ nguyên ID và vị trí cha/con, cập nhật tên ở bộ lọc/menu/form sản phẩm sau khi lưu.
- **Xóa:** có xác nhận tên danh mục. Chỉ xóa nhóm không còn sản phẩm hoặc danh mục con; SKU đã ẩn cũng chặn xóa. Gợi ý hãng của nhóm trống được gỡ cùng transaction; hãng, sản phẩm và dữ liệu bán hàng được giữ nguyên.

Không chuyển vị trí cha/con trong form sửa hiện tại. Không có thao tác xóa cả nhánh hoặc tự xóa sản phẩm để vượt qua ràng buộc.

## API

<!-- GET cũ vẫn trả toàn bộ danh mục khi không truyền q; thêm quyền ghi đúng role trên từng endpoint. -->
- `GET /api/v1/categories?q=...`: công khai, tìm tên/slug không phân biệt hoa/thường; gõ không dấu có thể khớp slug. Không truyền `q` hoặc từ khóa trắng giữ response danh sách cũ. Giới hạn 150 ký tự, escape `%`, `_`, `!` trong LIKE.
- `POST /api/v1/categories`: ADMIN tạo; tên không được trùng kể cả khác hoa/thường.
- `PATCH /api/v1/categories/{id}`: ADMIN sửa `name` và `slug`, cả hai bắt buộc; tên tối đa 150, slug tối đa 180 ký tự. Chuẩn hóa khoảng trắng đầu/cuối và slug chữ thường. Không nhận yêu cầu đổi danh mục cha.
- `DELETE /api/v1/categories/{id}`: ADMIN xóa nhóm trống, thành công trả **204 No Content**. Có con/sản phẩm trả **409 Conflict** kèm lý do. Không tồn tại trả 404; ID không dương trả 400.
- MANAGER, WAREHOUSE_STAFF và CUSTOMER không được sửa/xóa, trả **403**; chưa đăng nhập trả **401**.

```json
{
    "name": "Tay cầm chơi game",
    "slug": "tay-cam-choi-game"
}
```

## Transaction và khởi động lại

<!-- Khóa dòng phối hợp với UNIQUE/FK; migration đã áp dụng vẫn nguyên trạng. -->
Sửa/xóa khóa dòng danh mục trong transaction. UNIQUE bảo vệ tên/slug khi nhiều cập nhật cạnh tranh; lỗi trùng lúc flush trả 409 và rollback. Xóa kiểm tra cả con và mọi SKU, gỡ `brand_categories`, rồi xóa dòng danh mục và làm sạch persistence context để không giữ collection hãng cũ. FK của database là lớp bảo vệ cuối khi có tham chiếu đồng thời. Không sửa schema hoặc migration V1–V13.

Khi **`app.demo.seed-catalog=false`** (mặc định), runner demo chỉ chuẩn bị tài khoản/kho/phân công; không tự thêm lại danh mục đã xóa hoặc slug cũ sau khi sửa. Database mới sau V13 có **125 danh mục tham khảo**. Bật seed catalog mới thêm năm nhóm Tech và dữ liệu sản phẩm/tồn mẫu. Database đang sử dụng giữ toàn bộ dữ liệu có sẵn.

Khởi động lại Spring Boot để nạp API Java mới, rồi **Ctrl+F5** tải giao diện. Không cần Node.js/npm khi sử dụng website. Các lần sửa/xóa kiểm chứng dùng PostgreSQL QA riêng, không thao tác catalog thật của người dùng.

## Kiểm chứng

`CategoryManagementIntegrationTest` kiểm tra quyền JWT, sửa có sản phẩm, giữ ID/cha/giá/SKU, tên/slug trùng, xóa có gợi ý hãng, chặn con/SKU ACTIVE và INACTIVE, validation và tìm kiếm/escape LIKE. `ManualCatalogSeedIntegrationTest` bổ sung ca khởi động lại không khôi phục danh mục đã xóa/đổi slug.

```powershell
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' test
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' -DskipTests package
```

<!-- Số liệu cuối được ghi sau khi suite và QA trên JAR hoàn tất. -->
Toàn bộ **479 tests PASS**, không failure/error/skipped; package BUILD SUCCESS. Suite tăng 26 trường hợp so với mốc 453. Package bỏ chạy lại test sau khi suite đầy đủ đã PASS.

<!-- QA dùng bản sao PostgreSQL và Chrome riêng; dữ liệu kiểm thử không đi vào catalog đang sử dụng. -->
Trên JAR đã đóng gói, **66 kiểm tra Chrome PASS** với PostgreSQL 13.2: 24 ca quản lý danh mục và 42 ca hồi quy mua hàng/fulfillment, không có exception JavaScript. Đã kiểm tra tìm có/không dấu, thêm ba cấp, sửa giữ ID/cha, xác nhận/hủy xóa, gỡ gợi ý hãng, chặn danh mục có con hoặc SKU INACTIVE, 403 cho CUSTOMER, nội dung HTML được escape và form sửa ở màn hình 390px. Hai request PATCH đồng thời tranh cùng tên/slug nhận đúng một 200 và một 409 từ ràng buộc UNIQUE PostgreSQL.

Các ca hồi quy kiểm tra trang sản phẩm, giỏ, đặt/thanh toán, PACKED → SHIPPED → DELIVERED → RETURNED, hoàn tồn và ledger, báo cáo theo role. JavaScript qua kiểm tra cú pháp; ba file giao diện qua Prettier. Chưa kiểm chứng Firefox/Safari hoặc GitHub Actions từ xa. Suite Maven vẫn dùng H2; PostgreSQL được kiểm chứng cục bộ bằng JAR và Chrome, chưa chuyển suite sang Testcontainers.

Backup để dựng QA: `target/db-backups/category-management-20261003_080003/stockflow.dump`. Đối chiếu trước/sau giữ nguyên 13 nhóm snapshot database thật, gồm danh mục, sản phẩm/ảnh, tồn, ledger, đơn/thanh toán/vận đơn, tài khoản/kho/phân công. V1–V12 PostgreSQL/H2 giữ nguyên hash baseline; ba tài nguyên giao diện và toàn bộ 13 migration trong JAR khớp source cuối. Không sửa/xóa danh mục thật, không dừng app 8080 và không thay migration đã áp dụng. Database và tiến trình QA đã được dọn; backup được giữ lại.

## File thay đổi

<!-- Chỉ các file của lượt CRUD danh mục, giữ các thay đổi đã có trước đó trong workspace. -->
- `src/main/java/com/stockflow/catalog/api/CategoryController.java`
- `src/main/java/com/stockflow/catalog/domain/Category.java`
- `src/main/java/com/stockflow/catalog/dto/UpdateCategoryRequest.java`
- `src/main/java/com/stockflow/catalog/repository/CategoryRepository.java`
- `src/main/java/com/stockflow/catalog/service/CategoryService.java`
- `src/main/java/com/stockflow/demo/DemoDataSeeder.java`
- `src/main/resources/static/index.html`
- `src/main/resources/static/app.js`
- `src/main/resources/static/styles.css`
- `src/test/java/com/stockflow/catalog/api/CategoryManagementIntegrationTest.java`
- `src/test/java/com/stockflow/demo/ManualCatalogSeedIntegrationTest.java`
- `README.md`
- `ANTIGRAVITY_HANDOFF.md`
- `docs/catalog-completion-and-brand-logos.md`
- `docs/category-management.md`
