<!-- Nhận diện xanh dương theo lựa chọn của người dùng; không thay API, dữ liệu hoặc nghiệp vụ. -->
# Nhận diện xanh dương StockFlow

Ngày 05/10/2026, đổi nhận diện từ xanh lá sang xanh dương cho storefront và dashboard.

## Bảng màu

- Màu chính sáng: `#2563EB`.
- Hover sáng: `#1D4ED8`.
- Nền nhấn sáng: `#EFF6FF`.
- Chữ nút chính sáng: `#FFFFFF`.
- Màu chính tối: `#60A5FA`.
- Hover tối: `#93C5FD`.
- Nền trang tối: `#0B1220`.
- Chữ nút chính tối: `#081B36`.

Giữ các màu có ý nghĩa nghiệp vụ: xanh lá còn hàng/thành công, đỏ lỗi/hết hàng, cam/vàng cảnh báo tồn thấp. Giá bán dùng màu riêng. Logo hãng, ảnh sản phẩm và chấm màu SKU vẫn giữ màu gốc; chỉ logo StockFlow, favicon và hình minh họa banner của hệ thống đổi nhận diện.

## Phạm vi

Đổi các tông trang trí viết trực tiếp trong CSS, cùng token màu của cả hai theme: header, demo bar, tìm kiếm, giỏ, danh mục/chip lọc, banner, bảng/form, skeleton, biểu đồ, breadcrumb, phiên bản/màu đang chọn và nút mua. Sidebar desktop dùng nền navy, mục đang chọn có nhấn xanh dương. Giữ các khai báo kích thước, bố cục, responsive, chuyển động và trạng thái nghiệp vụ.

Token trong `styles.css`: `--brand`, `--brand-hover`, `--brand-soft`, `--brand-contrast`, `--sidebar-background`, màu nền/chữ/viền. `assets/theme.css` ghi đè token cho dark mode; màu nhấn của trang sản phẩm dùng chung token brand để nút/viền lựa chọn không quay lại đỏ.

`assets/theme.js` chỉ đổi màu metadata `theme-color` theo chế độ. Logic lưu lựa chọn sáng/tối và sự kiện không đổi; app.js, JWT, giỏ, API và database không sửa trong lượt bảng màu.

## Kiểm chứng

- Maven Wrapper Java 17, cache `C:/Users/Admin/.m2/repository`: **555/555 test PASS**, không failures/errors/skipped.
- Sau sửa ô danh mục dark mode, chạy lại **14 test tài nguyên web PASS**; package với `-DskipTests` sau các bước test: **BUILD SUCCESS**.
- Hai file CSS parse thành công. Đối chiếu AST xác nhận các khai báo bố cục và màu trạng thái tường minh được giữ nguyên.
- Đối chiếu theme.js sau khi loại comment/giá trị màu xác nhận logic sự kiện và lưu trữ không đổi.
- Độ tương phản của chữ/nền nút chính: sáng **5,17:1**, hover sáng **6,70:1**, tối **6,77:1**, hover tối **9,54:1**. Đây là kiểm tra các cặp màu nút, không phải chứng nhận khả năng tiếp cận toàn bộ website.
- **85 kiểm chứng Chrome PASS**, không ngoại lệ JavaScript. API giả lập, tài nguyên tĩnh lấy trực tiếp từ source qua server QA riêng: giỏ/đồng bộ đơn, màu chủ đạo, metadata trình duyệt, nút mua/hover, viền phiên bản/màu, hồ sơ, sidebar và bố cục desktop 1440px/mobile 375px ở hai theme.
- Rà ảnh chụp và sửa nền ô danh mục chưa chọn trong dark mode; nền navy và chữ sáng đã được kiểm tra trực tiếp.

Log, script và ảnh QA nằm trong `target/blue-theme-qa/`, không đưa vào repository. Frontend QA không kết nối database hoặc ghi dữ liệu vào backend đang dùng.

## File thay đổi

- `src/main/resources/static/styles.css`
- `src/main/resources/static/assets/theme.css`
- `src/main/resources/static/assets/theme.js`
- `src/main/resources/static/index.html`
- `src/main/resources/static/assets/stockflow.svg`
- `README.md`
- `docs/blue-theme.md`
- `ANTIGRAVITY_HANDOFF.md`

Các thay đổi hồ sơ/giỏ/đơn đang có từ lượt trước được giữ nguyên. Khi chạy ứng dụng, nhấn Ctrl+F5 để trình duyệt đọc lại CSS/JS; nếu chạy JAR, sử dụng bản build mới.
