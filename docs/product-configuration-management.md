<!-- Quản trị cấu hình theo hai phần riêng, xóa bằng lưu trữ để giữ SKU và lịch sử nghiệp vụ. -->
# Quản lý phiên bản và màu sắc

Vào **Admin → Danh mục & Sản phẩm → Sản phẩm**. Mỗi sản phẩm có hai nút **Phiên bản** và **Màu sắc**, mở đúng tab tương ứng.

## Phiên bản

- Phiên bản là cấu hình, ví dụ **256 GB**, **512 GB** hoặc **40mm GPS · Dây S/M**. Không dùng tên màu làm tên phiên bản nếu sản phẩm không phân biệt cấu hình bằng màu.
- Chọn phiên bản trong danh sách để sửa tên/thông số, lưu hoặc bấm **Xóa phiên bản**. Nút **Xem màu sắc** mở các màu thuộc phiên bản đang chọn.
- **Thêm phiên bản mới** mở form riêng. Lần đầu khai báo đúng màu của SKU hiện có; các lần sau chỉ tạo cấu hình, chưa tự tạo SKU/tồn. Sau khi thêm, giao diện chuyển sang tab Màu sắc để tiếp tục.

## Màu sắc

- Chọn phiên bản trong ô **Chọn phiên bản để quản lý màu**. Bảng chỉ hiện các SKU màu của phiên bản đó.
- Mỗi màu có giá, trạng thái bán, ảnh bìa/bộ ảnh và các nút **Lưu màu**, **Xóa màu** riêng. **Thêm màu** mở form SKU mới đúng phiên bản.
- Chuyển giữa hai tab giữ nội dung chưa lưu. Tab hỗ trợ phím mũi tên, Home/End; giao diện có bố cục riêng cho điện thoại.

## Xóa và khôi phục

<!-- Không xóa vật lý SKU đang được tồn/đơn/ledger tham chiếu và không tạo movement khi số lượng không đổi. -->
**Xóa màu** ẩn đúng lựa chọn màu khỏi cửa hàng. **Xóa phiên bản** ẩn cả phiên bản và các màu thuộc phiên bản đó. Có xác nhận tên trước khi thực hiện; nút Quay lại giữ hộp thoại và dữ liệu đang nhập.

Đây là xóa bằng lưu trữ: giữ ID, SKU, ảnh, thông số, tồn, đơn hàng và ledger. Không xuất/hoàn kho hoặc tạo movement vì thao tác cấu hình không thay đổi số lượng. API đặt hàng chặn cả SKU trực tiếp của lựa chọn đã xóa; availability báo không còn bán và truy vấn giá từ loại lựa chọn đã xóa trước phân trang.

Bật **Hiện phiên bản và màu đã xóa để khôi phục**:

- Chọn phiên bản đã xóa → **Khôi phục phiên bản**. Giữ trạng thái riêng của từng màu; không tự mở lại màu đã xóa hoặc đang ngừng bán.
- Chọn đúng phiên bản → **Khôi phục màu**. Dùng lại SKU cũ, giữ tồn và lịch sử. Khôi phục phiên bản trước nếu phiên bản đang bị xóa.

**Ngừng bán** khác **Xóa màu**: màu ngừng bán vẫn xuất hiện như lựa chọn bị vô hiệu hóa ở trang chi tiết; màu đã xóa được ẩn khỏi bộ chọn. Tên phiên bản, tên màu và SKU đã dùng vẫn chịu ràng buộc duy nhất; khôi phục cấu hình cũ để dùng lại thay vì tạo trùng. Giới hạn 20 phiên bản/model, 30 màu/phiên bản và 100 SKU/model vẫn tính cả dữ liệu lưu trữ.

Đơn đã đặt trước khi xóa vẫn thanh toán, hủy, giao và hoàn kho theo SKU lịch sử. Không thay thời điểm DISPATCH, atomic reserve hoặc trigger bất biến.

## API và migration

<!-- Quyền ADMIN được kiểm tra tại controller, nghiệp vụ xác minh model và khóa dòng trong transaction. -->
- `DELETE /api/v1/products/{productId}/versions/{versionId}`: ADMIN lưu trữ phiên bản, trả model cập nhật (**200**).
- `POST /api/v1/products/{productId}/versions/{versionId}/restore`: ADMIN khôi phục phiên bản (**200**), không cần body.
- `DELETE /api/v1/products/{productId}/variants/{variantId}`: ADMIN lưu trữ màu (**200**).
- `PATCH /api/v1/products/{productId}/variants/{variantId}` với `{"status":"ACTIVE"}`: khôi phục màu qua contract sẵn có. Nếu phiên bản đang xóa trả **409** và rollback các thay đổi cùng request.
- Response phiên bản/màu thêm trường boolean `archived`; các trường và endpoint cũ giữ nguyên. ID sai model trả 404; không dùng SKU con làm model. CUSTOMER/MANAGER/WAREHOUSE_STAFF gọi API ghi bị 403, chưa đăng nhập bị 401.

**V14** thêm `archived BOOLEAN NOT NULL DEFAULT FALSE` vào `product_versions` và `product_variants`, có migration PostgreSQL và H2. Cấu hình hiện có mặc định chưa xóa, giữ dữ liệu/liên kết cũ. Không sửa V1–V13.

**Khởi động lại Spring Boot rồi Ctrl+F5** để áp dụng V14 và nạp API/giao diện mới. Không chỉ tải lại trình duyệt khi backend vẫn chạy phiên bản cũ. Không cần Node.js/npm để sử dụng.

## Kiểm chứng

<!-- Suite API dùng H2; JAR và migration thật được kiểm chứng thêm trên PostgreSQL QA riêng. -->
**496 tests PASS** (17 mới), không failure/error/skipped; `package` BUILD SUCCESS sau suite đầy đủ. Các ca mới kiểm tra xóa/khôi phục phiên bản/màu, màu gốc, idempotency, trạng thái ngừng bán riêng, rollback khi phiên bản đã xóa, quyền/ownership model, giá từ tại SQL, availability, checkout qua SKU và đơn cũ giao/hoàn kho đúng.

```powershell
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' test
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' '-DskipTests' package
```

Trên JAR cuối, **69 kiểm tra Chrome PASS** với PostgreSQL 13.2: 27 ca quản lý cấu hình và 42 ca mua hàng/fulfillment hồi quy, không exception JavaScript. Đã kiểm tra hai nút/tab, giữ form khi chuyển tab, điều khiển bàn phím, xác nhận/quay lại, ẩn và khôi phục, tồn/ledger không đổi, phiên bản đã xóa không thể đặt trực tiếp, storefront ẩn lựa chọn và màn hình 390px.

Nâng V13 → V14 trên bản sao PostgreSQL giữ nguyên **18 nhóm snapshot**; mọi phiên bản/màu cũ có archived=false. Đối chiếu database thật trước/sau QA cũng giữ nguyên 18 nhóm. V1–V13 qua Flyway validate; V1–V12 PostgreSQL/H2 giữ hash baseline. Ba tài nguyên giao diện và 14 migration trong JAR khớp source cuối; JavaScript/Prettier/diff check đều đạt. Backup dựng QA: `target/db-backups/configuration-management-20261003_092923/stockflow.dump`. Không sửa/xóa dữ liệu thật hoặc dừng app 8080. Database/tiến trình QA đã được dọn, giữ backup.

Chưa kiểm chứng Firefox/Safari hoặc CI từ xa. Suite Maven vẫn dùng H2; PostgreSQL được kiểm tra cục bộ bằng JAR và Chrome, chưa dùng Testcontainers. Không có xóa vật lý cấu hình hoặc thao tác chuyển màu sang phiên bản khác trong lượt này.

## File thay đổi

<!-- Danh sách chỉ gồm 20 file của lượt tách phiên bản/màu và bổ sung lưu trữ; giữ thay đổi cũ trong workspace. -->
- `src/main/resources/db/migration/V14__archive_product_versions_and_colors.sql`
- `src/test/resources/db/migration/V14__archive_product_versions_and_colors.sql`
- `src/main/java/com/stockflow/catalog/domain/ProductVersion.java`
- `src/main/java/com/stockflow/catalog/domain/ProductVariant.java`
- `src/main/java/com/stockflow/catalog/dto/ProductVersionResponse.java`
- `src/main/java/com/stockflow/catalog/dto/ProductVariantResponse.java`
- `src/main/java/com/stockflow/catalog/service/ProductVersionService.java`
- `src/main/java/com/stockflow/catalog/service/ProductVariantService.java`
- `src/main/java/com/stockflow/catalog/service/ProductService.java`
- `src/main/java/com/stockflow/catalog/api/ProductVersionController.java`
- `src/main/java/com/stockflow/catalog/api/ProductVariantController.java`
- `src/main/java/com/stockflow/catalog/repository/ProductAvailabilityRepository.java`
- `src/main/java/com/stockflow/order/service/OrderService.java`
- `src/main/resources/static/index.html`
- `src/main/resources/static/app.js`
- `src/main/resources/static/styles.css`
- `src/test/java/com/stockflow/catalog/api/ProductConfigurationArchiveIntegrationTest.java`
- `README.md`
- `ANTIGRAVITY_HANDOFF.md`
- `docs/product-configuration-management.md`
