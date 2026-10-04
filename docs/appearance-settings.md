<!-- Tài liệu sử dụng và kiểm chứng nút sáng/tối; không thay đổi quy tắc phân quyền hoặc dữ liệu nghiệp vụ. -->
# Giao diện sáng và tối

Nút **Sáng / Tối** nằm ở thanh demo ghim trên cùng, cạnh thông tin tài khoản. Nút dùng chung cho cửa hàng, trang chi tiết sản phẩm và cổng quản trị. Trên điện thoại, nút chỉ hiện biểu tượng mặt trời hoặc mặt trăng để tiết kiệm chỗ; nhãn truy cập vẫn có sẵn.

- Lần đầu mở trang, màu nền theo chế độ của hệ điều hành; khi không đọc được chế độ này, dùng giao diện sáng.
- Sau khi bấm nút, lựa chọn riêng được lưu tại khóa `stockflow.web.theme` trong `localStorage`, được giữ khi tải lại trang, đổi vai trò hoặc đăng xuất và đồng bộ giữa các tab cùng website.
- Khi chưa có lựa chọn riêng, thay đổi chế độ của hệ điều hành cũng cập nhật giao diện. Xóa khóa lưu trữ rồi tải lại trang để trở về hành vi này.
- Trình duyệt chặn lưu trữ vẫn đổi màu được trong trang hiện tại. Không cam kết nhớ lựa chọn sau khi tải lại trong trường hợp đó.
- Dùng phím Tab để đến nút và Space/Enter để chuyển màu. Trạng thái tối được phản ánh qua `aria-pressed`; hiệu ứng của nút tôn trọng tùy chọn giảm chuyển động.

Bảng màu tối áp dụng cho nền, chữ, biểu mẫu, bảng dữ liệu, thông báo và trạng thái đơn hàng. Khung ảnh sản phẩm và logo có nền trắng được giữ để ảnh dễ nhận diện. Nút không gửi request nghiệp vụ và không sửa JWT, giỏ hàng, dữ liệu sản phẩm, tồn kho hay đơn hàng.

## Triển khai

`index.html` nạp `/assets/theme.js` trong `head` trước CSS để áp dụng lựa chọn ngay từ đầu, hạn chế chớp nền sáng. `/assets/theme.css` chứa nút và các lớp màu tối, được nạp sau `styles.css`. Tài nguyên nằm trong `/assets/**`, vốn đã được cấu hình public; API vẫn đi qua cơ chế bảo mật hiện có.

Không cần Node.js hoặc bước build frontend để chạy ứng dụng. Khi cập nhật giao diện trên máy đang mở trình duyệt, dùng **Ctrl + F5** để tải lại CSS/JS.

## Kiểm chứng ngày 03/10/2026

- `WebDemoIntegrationTest`: **14/14 PASS**, gồm URL cửa hàng/chi tiết, tài nguyên tĩnh công khai và yêu cầu JWT của API nội bộ. Chạy bằng Maven Wrapper với `-Dmaven.repo.local=C:/Users/Admin/.m2/repository`.
- Chrome riêng: **80 kiểm tra PASS**, gồm chuyển màu, giỏ có hàng/JWT không đổi, tải lại, ưu tiên chế độ hệ thống/lựa chọn riêng, đồng bộ hai tab, fallback khi chặn lưu trữ, điều khiển bằng bàn phím, chọn phiên bản/màu và dashboard. Kiểm tra màn hình 1440 px và điện thoại 390/320 px, đo tương phản tại các vùng chữ chính, không có exception JavaScript chưa xử lý.
- Kiểm tra cú pháp JavaScript và định dạng HTML/CSS/JS thành công. `package` tạo JAR thành công; đóng gói lại bảng màu cuối cùng bằng `-DskipTests` sau khi các test giao diện đã PASS.

Lượt này không chạy lại toàn bộ test nghiệp vụ. Kiểm tra trình duyệt chỉ đăng nhập demo, đọc API và thao tác giỏ trong bộ nhớ; không tạo đơn, nhập hàng hoặc sửa dữ liệu thật.

Các file thay đổi: `static/index.html`, `static/assets/theme.js`, `static/assets/theme.css`, `WebDemoIntegrationTest.java`, `README.md` và tài liệu này. `app.js`, `styles.css`, API và migration được giữ nguyên trong thay đổi sáng/tối.

## Chữ thương hiệu trong chế độ tối — 04/10/2026

Tên hãng dưới logo trong menu là văn bản, được sửa sang màu sáng theo bảng màu tối; tên hãng trên dashboard dùng cùng màu chữ. Khung logo trắng được khai báo bằng CSS và có thể đổi riêng. Phần nền trắng nằm trong chính file ảnh cần bản ảnh nền trong suốt để thay nền mà giữ màu thương hiệu; CSS đổi nền khung không xóa được các điểm ảnh trắng đó.

Chrome đã kiểm tra đủ **84 hãng**, xuất hiện tại **244 ô tên hãng** trên các nhóm menu: chữ đạt tương phản tối thiểu 4,5:1, ảnh giữ nguyên màu và khung trắng, chế độ sáng giữ màu chữ cũ, không có exception JavaScript. `package -DskipTests` thành công; lượt sửa CSS này không chạy lại JUnit. File thay đổi trong lượt này: `static/assets/theme.css` và tài liệu này.
