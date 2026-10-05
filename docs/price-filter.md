<!-- Bộ lọc giá chỉ thay tài nguyên giao diện, dùng API catalog đã có và không thay dữ liệu nghiệp vụ. -->
# Thanh trượt lọc giá

Ngày 05/10/2026, bổ sung thanh trượt hai đầu cho kệ sản phẩm của StockFlow.

## Cách sử dụng

- Kéo đầu trái để chọn giá thấp nhất, đầu phải để chọn giá cao nhất. Nhãn tiền cập nhật ngay; thả tay mới lọc sản phẩm.
- Có thể bấm trên thanh để chọn đầu gần nhất, kéo bằng chuột/cảm ứng, hoặc dùng Tab và các phím mũi tên/Home/End.
- Thanh kéo dùng bước 50.000đ và thang mặc định 0–50 triệu. Mốc trên cùng là **Không giới hạn**, không tự loại sản phẩm đắt hơn 50 triệu.
- Mở **Nhập giá chính xác**, nhập hai ô rồi bấm **Áp dụng**. Ô nhập giữ tối đa hai chữ số thập phân; giá lớn hơn thang hiện tại sẽ mở rộng thang kéo.
- Muốn giới hạn đúng tại mốc cuối, nhập giá đó trong ô **Đến**. Ô trống nghĩa là không giới hạn tương ứng.
- Các chip chọn nhanh và nút **Đặt lại** cập nhật cả ô nhập lẫn thanh kéo. “Dưới 500k” vẫn dùng 499.999,99đ; “Trên 5 triệu” dùng 5.000.000,01đ, giữ nghĩa chặt của các mốc đã có.
- Lọc giá giữ danh mục, hãng, từ khóa và thứ tự sắp xếp; quay về trang đầu. URL lưu khoảng giá đã áp dụng để tải lại hoặc dùng Back/Forward.

## Ràng buộc và hành vi

Giá âm, giá từ lớn hơn giá đến, quá hai chữ số thập phân hoặc vượt 9.999.999.999,99đ bị chặn tại form, có thông báo tiếng Việt. Giá đang gõ là bản nháp; chỉ gửi form hoặc thả thanh kéo mới áp dụng. Chạm một đầu không làm tròn phần thập phân của đầu còn lại. Cử chỉ kéo trên nền thanh bị hủy sẽ khôi phục bản nháp, không gọi API.

## Bố cục theo ảnh tham khảo

<!-- Thu gọn giao diện nhưng giữ nguyên bộ lọc và độ chính xác của giá đã nghiệm thu. -->
Tiêu đề kệ hàng hiển thị số sản phẩm của trang hiện tại và tổng số kết quả do backend trả về. Bộ chọn sắp xếp nằm cạnh tiêu đề. Hãng có các nút một chạm cùng dropdown; tên/ID và phạm vi danh mục đều lấy từ API hiện có. Danh sách dài cuộn ngang, nút được chọn có màu xanh dương. Đổi hãng giữ khoảng giá, từ khóa, thứ tự sắp xếp và quay về trang đầu.

Thanh kéo và năm khoảng giá nhanh nằm trong một panel nhỏ. Ô nhập tay thu gọn bằng `details/summary` gốc; hỗ trợ chuột/bàn phím và tự mở khi có lỗi để đưa focus đến trường cần sửa. Đóng phần nhập không đổi bộ lọc hoặc gửi API. Mobile bố trí mốc nhanh thành hai hàng cân đối; dải hãng và phần nhập mở đều không làm tràn ngang.

Ảnh tham khảo có các nhãn khuyến mãi, freeship, bảo hành và sắp xếp bán chạy. Các mục này cần dữ liệu/nghiệp vụ bổ sung; đợt chỉnh bố cục chỉ dùng những điều kiện hiện có của StockFlow. Không hiển thị số sản phẩm, mức giảm giá hoặc cam kết giả.

Chỉ dùng `GET /api/v1/products` với `minPrice`/`maxPrice` hiện có. Không gọi API cho mỗi bước kéo; không gửi lặp khoảng giá đang tải/đã hiển thị. Nếu catalog trả lỗi đọc, bấm Áp dụng cùng mức giá vẫn thử lại được. Backend, migration, JWT, giỏ hàng, thanh toán và fulfillment giữ nguyên.

Hai range input gốc giữ ngữ nghĩa bàn phím và nhãn giá cho công cụ hỗ trợ. Thanh kéo, viền focus, chip đang áp dụng và thông báo dùng token sáng/tối; bố cục mobile đưa thương hiệu/sắp xếp lên trước khoảng giá.

## Kiểm chứng

- Maven Wrapper, Java 17, `-Dmaven.repo.local=C:/Users/Admin/.m2/repository`: **555/555 test PASS**, 0 failures/errors/skipped.
- Sau lần chỉnh cuối, **14 test tài nguyên web PASS**. Đóng gói JAR bằng `package -DskipTests` sau các lượt test: **BUILD SUCCESS**.
- **85 kiểm chứng Chrome hồi quy PASS** cho giỏ, đồng bộ đơn, đổi ngữ cảnh tài khoản, điều hướng và dashboard.
- **56 kiểm chứng Chrome lọc giá PASS**: xem trước không gọi API, một GET khi thả, giữ các bộ lọc khác, về trang đầu, các mốc chip chính xác, giá lẻ, giá lớn, validation, URL, thử lại sau lỗi, thao tác chuột/cảm ứng/bàn phím và hủy cử chỉ.
- **55 kiểm chứng Chrome bố cục PASS**: số trang/tổng thật, hãng từ API và escape tên, chip/dropdown đồng bộ, giữ các bộ lọc khác, phạm vi danh mục, URL, không có hãng/không có kết quả, focus nút hãng, mở/đóng phần giá bằng chuột/bàn phím, tự mở khi lỗi, giá lẻ và nút Đặt lại.
- Responsive 1440/1024/768/390/375px ở hai theme không tràn ngang, cả khi phần nhập mở; rà ảnh chụp desktop/mobile sáng/tối. Không ngoại lệ JavaScript trong ba nhóm Chrome.
- JavaScript kiểm tra cú pháp, CSS/HTML/JS kiểm tra định dạng và `git diff --check` PASS.

Các nhóm Chrome chạy riêng, có một số kiểm tra chung. API được giả lập để kiểm tra giao diện; ảnh chụp dùng bản catalog công khai đã đọc ở lượt banner trước. Maven dùng H2 của bộ test hiện có. Lượt này không kiểm thử lại SQL trên PostgreSQL, Firefox/Safari hoặc thiết bị điện thoại vật lý, không ghi dữ liệu vào database đang dùng. Log, script và ảnh QA của thanh kéo nằm trong `target/price-filter-qa/`; bản bố cục tham khảo dùng `target/catalog-reference-qa/`. Các helper không được đưa vào Git.

## File của lượt này

- `src/main/resources/static/index.html`
- `src/main/resources/static/styles.css`
- `src/main/resources/static/assets/theme.css`
- `src/main/resources/static/app.js`
- `README.md`
- `ANTIGRAVITY_HANDOFF.md`
- `docs/price-filter.md`

Các thay đổi từ những lượt trước được giữ nguyên. Chạy lại ứng dụng bằng build mới rồi nhấn Ctrl+F5 để tải lại giao diện.
