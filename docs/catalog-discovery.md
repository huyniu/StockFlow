<!-- Danh mục và bộ lọc lấy dữ liệu thật; ghi rõ ranh giới của giai đoạn đầu và kiểm chứng đã thực hiện. -->
# Menu danh mục và tìm sản phẩm theo giá

Storefront có nút **Danh mục** trong header. Menu hiển thị danh mục do ADMIN đã tạo, kèm các lựa chọn khoảng giá và nút **Xem sản phẩm**. Danh mục mới xuất hiện sau khi lưu qua Admin hoặc tải lại trang; không cần sửa danh sách danh mục trong JavaScript.

## Cách sử dụng

1. Dừng rồi chạy lại Spring Boot để nạp API mới; mở `http://localhost:8080/` và nhấn **Ctrl + F5**.
2. Mở **Danh mục**. Trên máy tính, di chuột/focus danh mục để xem các khoảng giá; bấm tên danh mục để xem toàn bộ hàng trong nhóm.
3. Chọn khoảng giá để mở kệ đã lọc. Menu bắt đầu lượt duyệt mới, bỏ từ khóa cũ và áp dụng khoảng giá đã chọn; giữ giỏ, chi nhánh và thứ tự sắp xếp.
4. Trên kệ, nhập **Từ (đ)** / **Đến (đ)** và bấm **Áp dụng**. Để trống một ô nếu chỉ cần giới hạn một đầu mút.
5. Chọn **Giá tăng dần**, **Giá giảm dần**, **Mới nhất** hoặc **Mặc định**. Kết hợp với tên/SKU và danh mục.
6. Bỏ từng chip điều kiện hoặc bấm **Xóa bộ lọc** để trở về kệ mặc định. Giỏ và chi nhánh không bị xóa.

Menu hỗ trợ Escape, nút đóng, bấm ra ngoài và Tab ra ngoài. ArrowDown từ nút mở đưa focus vào danh mục; ArrowUp/ArrowDown di chuyển các lựa chọn. Điện thoại dùng menu nổi có thanh cuộn và nút đóng.

<!-- Giai đoạn tiếp nối có V9/V10; kết quả 295 test dưới đây là mốc lịch sử của bộ lọc giá ban đầu. -->
**Phạm vi hiện tại:** danh mục tối đa ba cấp, thương hiệu, khoảng giá, tìm tên/SKU và sắp xếp. Chọn danh mục cha bao gồm hàng ở các nhóm con, trước phân trang. Danh mục/hãng được lưu qua API và form ADMIN; V9/V10 chuẩn bị các lựa chọn theo ảnh tham khảo của người dùng. RAM/chip/màn hình/nhu cầu laptop chưa có bộ lọc cấu hình. Hướng dẫn và kết quả mới nhất **353 test, 84 kiểm tra Chrome PASS** ở [Danh mục và thương hiệu](catalog-categories-and-brands.md).

## API và validation

`GET /api/v1/products` nhận thêm hai tham số tùy chọn:

- `minPrice`: giá tối thiểu, gồm cả giá bằng đầu mút.
- `maxPrice`: giá tối đa, gồm cả giá bằng đầu mút.
- Không gửi đầu mút nào thì không giới hạn đầu mút đó. Giá 0 là một điều kiện hợp lệ.
- Kết hợp `categoryId` (bao gồm hậu duệ), `brandId`, `status`, `q`, `page`, `size` và `sort`.

Ví dụ:

```text
/api/v1/products?categoryId=1&status=ACTIVE&minPrice=1000000&maxPrice=3000000&sort=unitPrice,asc&page=0&size=8
```

Giá được parse và so sánh bằng BigDecimal. Chặn giá âm, lớn hơn `9999999999.99`, hơn hai chữ số thập phân có nghĩa, sai định dạng hoặc `minPrice > maxPrice` bằng **400 Bad Request**. Hai đầu mút tính cả, nên một giá đúng biên có thể thuộc hai khoảng gợi ý liền nhau.

Sắp xếp hỗ trợ `id`, `sku`, `name`, `unitPrice`, `status`, `createdAt`; nhận thêm alias `unit_price` và `created_at`. Dùng `asc` hoặc `desc`. API giữ các thứ tự phụ hợp lệ được gửi và thêm ID tăng dần nếu chưa có ID, để kết quả ổn định khi nhiều sản phẩm cùng giá. Trường sort khác trả 400; không sort qua collection ảnh hoặc đường dẫn quan hệ làm nhân bản sản phẩm.

Không gửi sort thì mặc định ID tăng dần, giữ kệ hiện tại. `sort=createdAt,desc` dùng cho Mới nhất. Trang bắt đầu từ 0; tổng số hàng/trang là số sau khi áp dụng toàn bộ điều kiện.

Đọc catalog vẫn công khai. Storefront gửi `status=ACTIVE`; ADMIN có thể đọc mọi trạng thái qua contract hiện có. Chỉ ADMIN được tạo/sửa catalog; các API JWT, quyền kho, đơn và báo cáo giữ quy tắc hiện tại.

## Persistence và frontend

Specification ghép điều kiện category/status/name/SKU/minPrice/maxPrice vào truy vấn JPA. Database thực hiện WHERE, ORDER BY và phân trang trước khi map DTO. Điều kiện LIKE vẫn escape ký tự đặc biệt. Không lọc/sort riêng các sản phẩm đã tải trong JavaScript.

Giá đang áp dụng tách khỏi bản nháp trong ô nhập. Chỉ bấm Áp dụng mới gửi khoảng giá nhập tay. Đổi điều kiện hoặc sort quay về trang đầu; chuyển trang giữ các điều kiện. Bộ lọc ở bộ nhớ phiên hiện tại, chưa được mã hóa thành URL chia sẻ hoặc lưu dài hạn.

Ở lượt bộ lọc giá ban đầu, schema dừng tại V8. Lượt tiếp nối thêm V9/V10 để lưu hãng và danh mục cha–con; không sửa V1–V8, không reset catalog hoặc bơm tồn mẫu. Giá, SKU, mô tả, ảnh, JWT, giỏ, atomic reserve, payment, fulfillment và ledger giữ nguyên.

## Kết quả kiểm chứng ngày 02/10/2026

- **35 test tích hợp mới**: hai đầu mút, giá thập phân/0, điều kiện một phía/kết hợp, tăng/giảm giá, thứ tự phụ, biên trang có trùng giá, metadata sau lọc, trang rỗng, alias, input sai, sort sai, các role đọc catalog và không ghi giá/tồn/ledger.
- Toàn suite **295 test PASS**, 0 failures/errors/skipped. Suite tự động dùng H2 như hiện tại.
- **BUILD SUCCESS** cho test và đóng gói JAR; package dùng `-DskipTests` sau full suite đã PASS.
- PostgreSQL QA **13.2** áp dụng V1–V8 trên database riêng và chạy JAR mới. Lọc/đếm/sắp xếp thực sự qua PostgreSQL, gồm 12 kết quả khoảng giá với hai trang 8 + 4 hàng.
- **32 kiểm tra Chrome PASS** cho menu, keyboard/Escape/ngoài menu, giá, sort, phối hợp điều kiện, xóa bộ lọc, empty state, mobile, tạo danh mục từ Admin, escape tên, giữ giỏ/chi nhánh/tồn/ledger và không gọi API nội bộ từ khách.
- **42 kiểm tra Chrome hồi quy PASS**, gồm thẻ/trang riêng, giỏ, Back/Forward, reload, checkout/giữ hàng/thanh toán, staff pack/ship/deliver/return và hoàn kho/ledger đúng.
- Tổng **74 kiểm tra trình duyệt**, 103 phản hồi API giao diện, 0 exception JavaScript. Có một 404 chủ động khi bài hồi quy thử ID không tồn tại; các API khác thành công.
- Đã xem ảnh menu/kệ desktop 1440px và mobile 390px; không tràn ngang. Dữ liệu minh họa chỉ ở database QA.
- Chưa chạy Docker Compose/PostgreSQL 17, GitHub CI hoặc Testcontainers trong lượt này; chưa đo EXPLAIN ANALYZE riêng cho bộ lọc catalog hoặc thêm index giá. Quy mô catalog hiện tại chưa cần index mới theo suy đoán.

Lệnh kiểm chứng:

```powershell
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' '-Dtest=CatalogDiscoveryIntegrationTest' test
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' test
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' '-DskipTests' package
```

JAR: `target/stockflow-0.0.1-SNAPSHOT.jar`. Log, script và ảnh QA ở `target/` được Git bỏ qua, không phải source cần commit.

## 11 file thay đổi trong lượt này

- `src/main/java/com/stockflow/catalog/api/ProductController.java`
- `src/main/java/com/stockflow/catalog/service/ProductService.java`
- `src/main/resources/static/index.html`
- `src/main/resources/static/app.js`
- `src/main/resources/static/styles.css`
- `src/test/java/com/stockflow/catalog/api/CatalogDiscoveryIntegrationTest.java` (mới)
- `README.md`
- `ANTIGRAVITY_HANDOFF.md`
- `HUONG_PHAT_TRIEN.md`
- `docs/storefront-roadmap.md`
- `docs/catalog-discovery.md` (mới)

Danh sách này thuộc lượt bộ lọc giá ban đầu. Danh sách file của lượt hãng/cây danh mục V9/V10 nằm ở [tài liệu tiếp nối](catalog-categories-and-brands.md).
