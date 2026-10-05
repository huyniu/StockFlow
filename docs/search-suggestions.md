<!-- Ghi nhận tìm kiếm gợi ý ngày 05/10/2026; phân biệt dữ liệu catalog với lịch sử nhập của trình duyệt. -->
# Gợi ý sản phẩm khi gõ tìm kiếm

StockFlow Tech tiếp tục dùng HTML/CSS/JavaScript thuần trong Spring Boot. Thay đổi này chỉ nâng cấp tìm kiếm storefront, không thay DTO, quyền JWT, schema, migration, giỏ hàng hoặc fulfillment.

## Hành vi giao diện

- Sau khi dừng gõ 250 ms, hiển thị tối đa sáu model đang bán kèm ảnh, tên, hãng/SKU và giá hiện tại. Giá “Từ” dùng `min_price` khi model có nhiều mức giá; không tạo giá cũ, số bán hay đánh giá giả.
- Gợi ý tìm trên toàn cửa hàng. Khi bấm “Tìm tất cả kết quả” hoặc Enter lúc chưa chọn gợi ý, kệ vẫn dùng các bộ lọc danh mục/hãng/giá đã chọn và quay về trang đầu.
- Bấm thẻ gợi ý mở `/san-pham/{id}`. ↑/↓ chọn gợi ý, Enter mở lựa chọn, Escape đóng danh sách nhưng giữ từ khóa. Liên kết giữ hành vi mở tab mới bằng Ctrl/Cmd hoặc chuột giữa.
- Tìm kiếm dùng cùng quy tắc hiện có của backend: tên/SKU không phân biệt hoa thường; SKU màu, tên phiên bản hoặc tên màu có thể trả về model chung. Không tự bổ sung tìm kiếm gần đúng hoặc bỏ dấu tiếng Việt.
- Tắt `autocomplete` native để lịch sử nhập trình duyệt không bị nhầm với sản phẩm của cửa hàng.
- Skeleton, trạng thái không có kết quả, ảnh lỗi và nút thử lại giữ dropdown hữu ích khi dữ liệu hoặc mạng chưa sẵn sàng. Lỗi gợi ý không tạo toast và không thay catalog đang xem.
- Hỗ trợ bộ gõ IME, combobox/listbox với nhãn và trạng thái đọc màn hình, theme sáng/tối và chiều rộng điện thoại 375 px trở lên.

## Contract và an toàn dữ liệu

Chỉ tái sử dụng API công khai hiện có:

```http
GET /api/v1/products?q=<từ-khóa>&status=ACTIVE&grouped=true&page=0&size=6&sort=id,asc
```

Kênh GET riêng `search-suggestions` không gửi Authorization và không thay request catalog, phân trang hay số lượng giỏ. Chỉ gửi khi có từ khóa không rỗng và focus còn trong tìm kiếm storefront. Tên, SKU, hãng và URL ảnh được kiểm tra/escape trước khi hiển thị. Từ khóa ở footer dùng `textContent`.

Mỗi lần gõ mới, đóng menu, điều hướng hoặc đổi tài khoản đều hủy timer/request cũ và đổi phiên bản gợi ý. Phản hồi muộn không được ghi đè từ khóa mới hoặc mở lại dropdown đã đóng. Request có hạn chờ 15 giây; lỗi hoặc hết hạn cho phép thử lại hoặc tìm trên kệ. Kết quả mới không được tự đặt hàng, giữ tồn hoặc gọi API vận hành.

## Phạm vi xác minh

<!-- Kết quả đã chạy trên bản source cuối cùng; không coi MockMvc hoặc API giả lập là xác minh production. -->
Đã chạy ngày 05/10/2026:

- `test`: 580 test PASS, 0 lỗi/thất bại/bỏ qua.
- `package`: BUILD SUCCESS, chạy lại 580 test PASS và tạo JAR. SHA-256 của `index.html`, `app.js`, `styles.css` trong JAR khớp source.
- Chrome: 85 kiểm tra gợi ý PASS và 241 kiểm tra các luồng cũ PASS (giỏ/đơn 85, giá 56, hãng/bố cục 55, khám phá/checkout 45). Tổng 326 kiểm tra frontend, không có ngoại lệ JavaScript ngoài luồng.
- Xác minh theme sáng/tối ở chiều rộng 1440/768/390/375 px; màn hình 375 px có chiều cao 640/500/400 px vẫn nhìn thấy footer tìm tất cả và không tràn ngang.
- Đã kiểm tra debounce, Enter/mũi tên/Escape/Tab, bộ gõ IME, tìm SKU màu về model, response chậm, mạng lỗi/thử lại, escape HTML, ảnh lỗi, chuyển role, giữ giỏ/JWT và Ctrl+click mở tab mới.
- GET tìm kiếm công khai trên ứng dụng đang chạy trả HTTP 200 cho `q=iph`. Chỉ đọc dữ liệu, không tạo sản phẩm/đơn hoặc thay tồn kho.
- Kiểm tra cú pháp JavaScript, định dạng Prettier, UTF-8 nghiêm ngặt và `git diff --check` đều đạt.

```powershell
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' test
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' package
```

Chrome dùng máy chủ tĩnh riêng và API giả lập, không ghi dữ liệu vào database cửa hàng. Hai phép kiểm tra thời gian trong bộ QA cũ được đồng bộ theo tick countdown/màu sau transition thay vì thời gian chờ cố định; không sửa code countdown, polling hoặc màu thương hiệu. Chưa kiểm tra bàn phím/thiết bị iOS/Android thật hoặc trình đọc màn hình thật. Maven ở lượt này dùng H2; profile Docker/Testcontainers không chạy lại vì backend/schema không đổi.

File thuộc lượt này: `src/main/resources/static/index.html`, `app.js`, `styles.css`, `README.md`, `ANTIGRAVITY_HANDOFF.md` và tài liệu này. Các thay đổi của lượt trước trong working tree được giữ nguyên.

## Đánh giá mức hoàn thiện hiện tại

Đã có catalog nhiều cấp/hãng/model–phiên bản–màu, ảnh/thông số/chi tiết sản phẩm, tìm kiếm/lọc giá, bán chạy theo giao dịch thật, giỏ và checkout người nhận, hồ sơ khách, lịch sử đơn và vận đơn. Dashboard đã có catalog, nhập kho, tồn đa kho, phạm vi nhân viên, đóng gói/giao/hoàn tất/trả hàng, sổ cái bất biến và báo cáo. Backend giữ transaction nhiều SKU, reserve nguyên tử chống bán vượt tồn, thứ tự khóa ổn định, expiry và retry đặt hàng không tạo đơn trùng.

Đây là MVP có thể trình diễn portfolio. Đánh giá sau mua còn chờ quy tắc nghiệp vụ; liên hệ/bảo hành/đổi trả cần nội dung được chủ cửa hàng chốt. Thanh toán, hoàn tiền và giao vận vẫn mô phỏng. Profile Testcontainers đã có nhưng Docker/CI thật chưa được xác minh tại máy này; Safari/iOS và Android thật chưa kiểm tra. Không coi dự án đã sẵn sàng vận hành thương mại chỉ vì test hiện có PASS.
