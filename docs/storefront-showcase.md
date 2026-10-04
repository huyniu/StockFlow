<!-- Thiết kế khu vực đầu trang chỉ thay giao diện; dữ liệu trưng bày lấy từ catalog thật hiện có. -->
# Khu trưng bày công nghệ StockFlow

Ngày 05/10/2026, thay banner minh họa góc làm việc bằng khu trưng bày sản phẩm theo nhận diện xanh dương.

## Giao diện

- Nền navy, vùng sáng xanh, đường quỹ đạo mảnh và tiêu đề ngắn có màu nhấn xanh sáng.
- Tối đa ba thẻ sản phẩm theo lớp; ảnh giữ nguyên màu và tỷ lệ, tên và giá lấy từ API.
- Bấm sản phẩm mở `/san-pham/{id}` bằng điều hướng hiện có; hỗ trợ bàn phím và mở tab mới.
- Hiệu ứng xuất hiện nhẹ; dùng chuột có phản hồi nâng/xoay và phóng ảnh. Chế độ giảm chuyển động tắt hiệu ứng.
- Desktop có khung cao 420px; danh mục dài cuộn riêng trong cùng chiều cao, vẫn mở bảng con tại cột banner.
- Tablet/mobile đưa banner lên trước dải danh mục ngang; vẫn hiển thị ảnh và nút mua sắm.
- Ba ô thông tin phía dưới giới thiệu chi nhánh, giữ hàng 15 phút và theo dõi đơn/vận đơn.

## Nguồn dữ liệu và giới hạn

`renderHeroShowcase` dùng trang ACTIVE/grouped do `loadCatalog` đã tải. Ưu tiên các ngành hàng khác nhau rồi bổ sung nếu còn vị trí; không thêm truy vấn API hoặc bảng dữ liệu. Lọc hay phân trang sẽ cập nhật các sản phẩm được trưng bày. Đây là khu khám phá catalog, không phải danh sách bán chạy được tính bằng doanh số.

Chỉ dùng `image_url` hoặc ảnh bổ sung đã lưu và qua kiểm tra URL hiện có. Khi ảnh lỗi, giữ liên kết/tên/giá và hiện thông báo; khi chưa có ảnh, hiện lời dẫn xem sản phẩm. Không lấy ảnh model khác để đại diện cho sản phẩm trong banner.

Giá dùng `min_price` hoặc `unit_price` từ response; thêm “Từ” nếu giá model có khoảng. Không sinh giảm giá, lượt bán, đánh giá hoặc cam kết mới. Các ảnh hiện có do quản trị nhập; nếu URL ngoài ứng dụng không còn truy cập được, quản trị cần cập nhật ảnh đó.

Các trạng thái đang tải, catalog rỗng hoặc API lỗi có phản hồi riêng; khung ảnh giữ kích thước để hạn chế nhảy bố cục. JWT, giỏ, checkout, fulfillment, schema và backend giữ hợp đồng hiện có.

## Kiểm chứng

- Maven Wrapper, Java 17, `-Dmaven.repo.local=C:/Users/Admin/.m2/repository`: **555/555 test PASS**, không failures/errors/skipped.
- Sau chỉnh giao diện, **14 test tài nguyên web PASS**; package cuối dùng `-DskipTests` sau các lượt test: **BUILD SUCCESS**. Đối chiếu SHA-256 xác nhận JAR chứa đúng bốn file tĩnh mới nhất.
- **85 kiểm chứng Chrome hồi quy PASS**: giỏ/đồng bộ đơn, token/ngữ cảnh role, điều hướng sản phẩm, bảng màu sáng/tối và bố cục dashboard.
- **52 kiểm chứng Chrome banner PASS**: tên/giá/liên kết thật, danh mục cuộn/menu header, ảnh không bị cắt, responsive 1440/1024/768/390/375px, hai theme, giảm chuyển động, một sản phẩm, catalog rỗng/API lỗi, lỗi ảnh và escape tên/loại URL nguy hiểm. Đây là hai nhóm chạy riêng, có một số kiểm tra chung về theme/lỗi JavaScript.
- Ảnh kiểm chứng cuối dùng catalog, danh mục và thương hiệu công khai đang có; mọi thao tác đặt/thanh toán/đổi role trong bộ QA đều được giả lập. Không ngoại lệ JavaScript trong cả hai nhóm.
- Rà trực quan desktop/mobile ở cả sáng/tối; sửa khung ảnh flex để thấy trọn thiết bị và độ ưu tiên màu chữ/nút trong dark mode.
- JavaScript qua kiểm tra cú pháp; CSS parse và formatting PASS; `git diff --check` PASS.

Log, script, catalog công khai dùng cho ảnh kiểm chứng và screenshot QA nằm trong `target/hero-showcase-qa/`. Các bài Chrome thao tác bằng API giả lập; chỉ đọc catalog công khai của ứng dụng để chụp giao diện có ảnh thật, không ghi dữ liệu vào database đang dùng. Maven vẫn dùng môi trường H2 của bộ test hiện có; lượt giao diện này không kiểm thử lại nghiệp vụ trên PostgreSQL.

## File của lượt này

- `src/main/resources/static/index.html`
- `src/main/resources/static/styles.css`
- `src/main/resources/static/assets/theme.css`
- `src/main/resources/static/app.js`
- `README.md`
- `ANTIGRAVITY_HANDOFF.md`
- `docs/storefront-showcase.md`

Các thay đổi từ lượt hồ sơ/giỏ/đơn/bảng màu trước đó được giữ nguyên. Khởi động lại ứng dụng bằng build mới và nhấn Ctrl+F5 để tải lại tài nguyên.
