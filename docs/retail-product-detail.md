<!-- Hoàn thiện trải nghiệm sản phẩm theo tham chiếu CellphoneS, bảo toàn SKU, transaction và phân quyền hiện có. -->
# Trang chi tiết sản phẩm theo cách mua sắm bán lẻ

## Phạm vi đã chốt

Giữ **một thẻ chung cho model**, ví dụ Apple Watch SE 3. Khách mở thẻ rồi chọn phiên bản và màu.
Tham khảo cách tách **Phiên bản** và **Màu sắc** trên [trang Apple Watch SE 3 của CellphoneS](https://cellphones.com.vn/apple-watch-se-3-40mm.html).
Đây là tham chiếu tương tác và bố cục; không suy đoán schema nội bộ của CellphoneS hoặc tự nhập dữ liệu thương mại vào StockFlow.

Trang chi tiết có tiêu đề phía trên, bộ ảnh bên trái, khu vực giá và chọn mua bên phải.
Phiên bản có tên/giá từ/dấu chọn; màu có ảnh nhỏ, tên, giá riêng và dấu chọn. Giá, ảnh chính và thông số cập nhật theo đúng SKU.
Ảnh nhỏ dùng URL bìa hoặc ảnh đã lưu của màu; thiếu/lỗi ảnh hiển thị chấm màu thay vì lấy ảnh SKU khác.
Giá gạch ngang, đánh giá 4,9/120 lượt bán và nhãn bán chạy giả không được thêm vào trang.

## Điều hướng và dữ liệu

- Breadcrumb gồm các danh mục cha thật → hãng → model. Bấm danh mục/hãng lọc kệ sản phẩm; Ctrl-click và mở tab mới hoạt động bằng liên kết HTML.
- Không biến hãng, model, phiên bản hoặc màu thành các tầng danh mục mới. V9–V12 giữ nguyên.
- Đổi phiên bản giữ màu tương ứng nếu còn bán; nếu không chọn màu ACTIVE đầu tiên. Giữ số lượng và chi nhánh hiện tại.
- Mỗi lựa chọn có URL `/san-pham/{skuId}`. Đổi phiên bản/màu cập nhật URL; reload, chia sẻ và Back/Forward mở đúng SKU.
- Giỏ vẫn tách theo Product ID của SKU. Một thẻ chung không đồng nghĩa các màu hoặc cấu hình dùng chung tồn.
- URL kệ lưu danh mục, hãng, từ khóa, khoảng giá và cách sắp xếp để quay lại hoặc chia sẻ bộ lọc.
- Phiên bản chưa có SKU ACTIVE ghi “Chưa mở bán”; SKU ngừng bán không được thêm giỏ.

## Tình trạng hàng tại chi nhánh

API công khai mới: `GET /api/v1/products/{id}/availability`.
ID model trả tất cả SKU của model; ID SKU con chỉ trả SKU đó. Chỉ trả kho ACTIVE.
Một dòng gồm `product_id`, `warehouse_id`, `warehouse_code`, `warehouse_name`, `in_stock`.

`in_stock=true` khi SKU và model đang ACTIVE, mapping màu được bật, và `available_quantity > 0`.
Không có dòng tồn, hết hàng khả dụng, toàn bộ hàng đã giữ hoặc ngừng bán đều trả false.
API không trả quantity, reserved, physical, inventory ID, địa chỉ nội bộ, actor hoặc ledger.
SQL JOIN đọc mọi SKU/chi nhánh trong một truy vấn và response có `Cache-Control: no-store`.

Trang sản phẩm hiển thị tình trạng tại chi nhánh đang chọn và danh sách “Xem chi nhánh có hàng”.
Đổi chi nhánh tải lại tín hiệu; lỗi mạng hiển thị “Chưa xác nhận” và nút thử lại, không bịa số tồn.
SKU hết hàng tại chi nhánh bị khóa nút thêm giỏ ở trang chi tiết; khách có thể chọn màu hoặc chi nhánh khác.
Đọc tình trạng không giữ chỗ. Hàng có thể thay đổi trước checkout; atomic conditional update vẫn chống bán vượt tồn.
Kệ không gọi API tồn nội bộ hoặc phát sinh một request tồn cho mỗi thẻ; tình trạng được kiểm tra khi mở chi tiết.

## An toàn dữ liệu và quyền

Không thêm schema/migration mới, không sửa V1–V12, không gộp/xóa SKU đã có.
Không đổi price snapshot, movement, thời điểm DISPATCH, fulfillment hoặc phạm vi kho của STAFF.
API mới tận dụng quyền GET công khai của catalog; các endpoint ghi, inventory và ledger giữ quyền JWT/role hiện tại.
Tên model/phiên bản/màu/thông số/breadcrumb được escape trước khi đưa vào HTML; URL ảnh tiếp tục dùng bộ kiểm tra sẵn có.

## Kiểm chứng

- `mvnw.cmd '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' test`: **424 PASS**, gồm 13 bài API mới.
- Test mới kiểm tra khách vãng lai/tất cả role, đúng SKU/chi nhánh, mapping tắt/model INACTIVE, kho INACTIVE,
  reserve hết hàng → cancel mở bán lại, no-store, response tối thiểu, không tạo movement khi đọc, 404 và quyền kho/ledger.
- Lệnh `package` đầy đủ chạy lại **424 PASS** và build JAR thành công. Sau bổ sung nút kiểm tra lại tình trạng,
  đóng gói lại assets với `-DskipTests`; backend không thay đổi. Các kiểm tra Chrome dưới đây chạy trên JAR cuối cùng.
- **114 kiểm tra Chrome PASS**: 33 mới, 39 hồi quy phiên bản/màu và 42 hồi quy mua hàng/fulfillment, 0 exception JavaScript.
  Kiểm tra URL/reload/Back/Forward, breadcrumb và bộ lọc chia sẻ, giữ số lượng/chi nhánh, ảnh/giá/thông số,
  SKU hết hàng, reserve/cancel cập nhật tín hiệu, lỗi mạng/thử lại và mobile 390px/320px không tràn ngang.
- Trên PostgreSQL QA 13.2, 12 yêu cầu tranh ba chiếc vẫn chỉ tạo ba đơn; phiên bản/màu khác giữ số tồn riêng.
  Fulfillment/hoàn kho/ledger và dashboard ADMIN/MANAGER/STAFF tiếp tục hoạt động.
- Đối chiếu cuối: **13 nhóm snapshot và 6 SKU của database thật không đổi**. Mọi migration V1–V12 giữ hash.
  Database/Java/Chrome QA đã dọn, cổng 8108/9240 đã đóng; app 8080 được giữ nguyên. Giữ backup ở
  `target/db-backups/cellphones-detail-20261002_184458/stockflow.dump`. Log/ảnh ở `target/cellphones-*` và `target/web-qa/cellphones-*`.
- Công cụ trình duyệt tương tác không khởi động được; đã kiểm tra bằng Chrome headless thật và xem ảnh desktop/mobile.
  Chưa kiểm chứng Safari/Firefox, Docker Compose/CI triển khai, Testcontainers hoặc dịch vụ thanh toán/vận chuyển thật.

## File thay đổi

- `src/main/resources/static/app.js`
- `src/main/resources/static/index.html`
- `src/main/resources/static/styles.css`
- `src/main/java/com/stockflow/catalog/api/ProductAvailabilityController.java` (mới)
- `src/main/java/com/stockflow/catalog/dto/ProductAvailabilityResponse.java` (mới)
- `src/main/java/com/stockflow/catalog/repository/ProductAvailabilityRepository.java` (mới)
- `src/main/java/com/stockflow/catalog/service/ProductAvailabilityService.java` (mới)
- `src/test/java/com/stockflow/catalog/api/ProductAvailabilityIntegrationTest.java` (mới)
- `README.md`
- `ANTIGRAVITY_HANDOFF.md`
- `docs/product-models-versions-and-colors.md`
- `docs/storefront-roadmap.md`
- `docs/retail-product-detail.md` (mới)

Khởi động lại Spring Boot, sau đó Ctrl + F5 để sử dụng API và giao diện mới.
Nếu chưa từng áp dụng V12, Flyway chạy V12 đã có khi khởi động; lượt giao diện này không tạo thêm migration.
Ảnh, tên phiên bản, giá và thông số vẫn do ADMIN nhập theo hàng thực tế; không tự đổi hàng đang dùng thành Apple Watch SE 3.

Địa chỉ nhận hàng, upload file, đánh giá sau mua, khuyến mãi thật, trang landing riêng cho dòng máy và Testcontainers vẫn thuộc lộ trình tiếp theo.
