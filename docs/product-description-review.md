# Rà soát mô tả sản phẩm StockFlow

Ngày kiểm tra: 09/10/2026. Đọc catalog qua API công khai, không suy luận nội dung database từ mã nguồn hoặc dữ liệu seed.

## Nguồn dữ liệu và cách sửa

Mô tả nằm tại `products.description` (kiểu TEXT), được ánh xạ bởi `catalog/domain/Product.java`, trả về trong `ProductResponse.description` qua `GET /api/v1/products/{id}`. Đây là dữ liệu database, không nằm trong fragment trang chi tiết. `product-detail.js` hiển thị văn bản đã escape.

Để cập nhật Render: đăng nhập ADMIN trên **https://stockflow-tbsw.onrender.com/** → cổng quản trị → **Quản lý sản phẩm** → chọn đúng sản phẩm gốc → **Sửa** → thay riêng ô **Mô tả** → lưu. Kiểm tra lại tên/SKU trước khi lưu; giữ nguyên giá, ảnh, trạng thái và thông số. Mở lại `/san-pham/{id}` và F5 để kiểm tra nội dung đã lưu.

Form `#update-product-description` được nạp bởi `admin.js`; `updateProduct()` gọi `PATCH /api/v1/products/{id}`. API này chỉ cho ADMIN và hỗ trợ body chỉ chứa `description`. `ProductService.updateProduct()` đồng bộ mô tả sản phẩm gốc xuống các SKU con theo hành vi hiện có; không cần sửa từng màu/phiên bản. Không sửa bằng migration hoặc câu UPDATE toàn bảng.

Snapshot trước chỉnh nằm trong `target/product-description-review/local.json` và `render.json` (tệp kiểm chứng, không đưa vào Git). Local `http://localhost:8080` có **18 bản ghi SKU / 7 sản phẩm gốc**; Render có **21 bản ghi SKU / 10 sản phẩm gốc**. Các ID dưới đây là ID thực tế của từng môi trường tại thời điểm kiểm tra; cần đối chiếu lại nếu dữ liệu thay đổi.

## 1. iPhone 17 Pro Max — ID 1

- Local: `IP17-PRO-256-CAM`, mô tả đang trống; không có đoạn quảng cáo cần xóa ở local.
- Render: cùng ID/SKU, mô tả dài 5.000 ký tự, còn nội dung thương mại của CellphoneS. **Chưa cập nhật database Render.**

Đoạn cần bỏ:

- Phần mở đầu hỏi giá tháng 10/2026; khoảng giá thị trường và toàn bộ bảng giá 256GB/512GB/1TB/2TB của CellphoneS.
- Đoạn mua kèm Apple Watch giảm 1,5 triệu, giảm 5% online và quyền lợi thành viên S Vip.
- Nhãn giá lặp lại và đoạn thu cũ lên đời/chính sách giá của các nhà bán lẻ khác. Không đổi tên CellphoneS thành StockFlow trong các đoạn này.
- Các cam kết lưu trữ tuyệt đối như không đầy bộ nhớ hoặc dùng đủ nhiều năm; đoạn cuối bị cắt. Không gọi mức 8x là ống kính zoom quang học nguyên bản: Apple phân biệt 4x và 8x chất lượng quang học.

Bản thay thế để dán vào ô Mô tả trên Render (văn bản thuần):

<!-- replacement:iphone-17-pro-max -->
```text
iPhone 17 Pro Max

iPhone 17 Pro Max sử dụng chip A19 Pro và màn hình OLED Super Retina XDR 6,9 inch, hỗ trợ ProMotion với tần số quét thích ứng lên đến 120 Hz.

Hệ thống camera sau gồm camera chính, góc siêu rộng và telephoto Fusion 48 MP. Camera trước 18 MP Center Stage hỗ trợ chụp ảnh và gọi video. Máy hỗ trợ quay video 4K Dolby Vision.

Chọn phiên bản dung lượng và màu sắc trong phần lựa chọn sản phẩm. Giá và tình trạng còn hàng hiển thị theo lựa chọn đang bán tại StockFlow.
```

Thông tin kỹ thuật trong bản thay thế đã đối chiếu [thông số iPhone 17 Pro Max của Apple](https://support.apple.com/en-us/125091). Không liệt kê tất cả dung lượng của nhà sản xuất như thể StockFlow đang bán đủ; không thêm thời lượng pin, sạc, giá hay ưu đãi chưa xác nhận.

## 2. Tay cầm chơi game PS5 DualSense — ID 7

- Local và Render: SKU `TAY-CẦM-CHƠI-GAME PS5 DUALSENSE`; cùng mô tả cũ 1.706 ký tự có lời mời mua ở CellphoneS.
- **Đã cập nhật local:** chỉ PATCH trường `description` của ID 7 qua API quản trị, rồi GET lại để xác nhận nội dung khớp bản dưới đây; tất cả trường còn lại trong response (bao gồm giá, ảnh, phiên bản/màu, thông số và tồn có sẵn) không đổi. Bản trước/sau lưu tại `target/product-description-review/local-7-before.json` và `local-7-after.json`.
- **Chưa cập nhật Render:** cần lưu bản dưới đây trên quản trị Render; bản cập nhật local không chuyển sang database Render.

Đoạn cần bỏ/chỉnh:

- Bỏ tiêu đề mời mua DualSense giá tốt tại CellphoneS và toàn bộ đoạn cuối mời đến chi nhánh, hứa giá tốt, ưu đãi/quà tặng.
- Bỏ các dòng chú thích ảnh lặp tên sản phẩm.
- Sửa tên nút Share thành **Create**; không giữ thông tin nhầm với DualShock 4.

Bảng thông số đang có một dòng gọi nút Share; lần này chỉ sửa mô tả, cần rà lại dòng đó riêng khi biên tập thông số.

Bản thay thế dùng cho local và để dán vào quản trị Render:

<!-- replacement:ps5-dualsense -->
```text
Tay cầm chơi game PS5 DualSense

DualSense là tay cầm không dây của Sony dành cho PlayStation 5. Phản hồi xúc giác và cò thích ứng L2/R2 tạo cảm giác tương tác trong những trò chơi có hỗ trợ.

Tay cầm tích hợp micro để trò chuyện và nút Create để tạo, chia sẻ nội dung chơi game. Cổng USB Type-C hỗ trợ kết nối và sạc pin.

Chọn màu hoặc phiên bản đang bán trong phần lựa chọn sản phẩm. Giá và tình trạng còn hàng hiển thị theo lựa chọn tại StockFlow.
```

Tên Create, micro, phản hồi xúc giác và cò thích ứng được đối chiếu [giới thiệu DualSense của PlayStation](https://blog.playstation.com/2020/04/07/introducing-dualsense-the-new-wireless-game-controller-for-playstation-5/); USB Type-C và điều kiện hỗ trợ trò chơi được đối chiếu [trang DualSense chính thức](https://www.playstation.com/en-us/accessories/dualsense-wireless-controller/). Không thêm quà, giá, bảo hành hoặc thời lượng pin.

## Các sản phẩm còn lại và điểm cần xác minh

Đã đọc mô tả của toàn bộ 10 sản phẩm gốc trên Render và 7 sản phẩm gốc ở local, bao gồm các bản mô tả trùng trên SKU con. Ngoài ID 1 và 7, chưa thấy đoạn giá/ưu đãi/chính sách của nhà bán lẻ khác theo các dấu hiệu CellphoneS, ShopDunk, FPT Shop, Thế Giới Di Động, voucher, cashback, thành viên và bảng giá. Kết quả này **không chứng nhận toàn bộ thông số trong các bài đã chính xác**.

- **ID 2 — IPHONE 18 PRO**, cả hai môi trường, cùng nội dung trên SKU con 36–42: bài mở đầu nói Pro Max trong khi tên hàng là Pro; chứa A20 Pro 2 nm, pin 43 giờ và điểm benchmark chưa có nguồn đối chiếu trong lần này. Không tiếp tục đăng các con số này như thông số đã xác nhận. Cần xác minh đúng model và tài liệu hãng trước khi viết lại; chưa đổi tên/model hoặc mô tả trong database.
- **ID 3 — Sony 1000X The Collexion**, cả hai môi trường; bản sao trên SKU 6: chưa thấy quảng cáo nhà bán lẻ khác. Giữ nguyên trong phạm vi này.
- **ID 4 — HP Omnibook 5 AI 16-AF1048TU BZ7Q9PA**, cả hai môi trường: chưa thấy quảng cáo nhà bán lẻ khác; bài dài 5.000 ký tự và kết thúc giữa câu. Cần biên tập đoạn cuối, giữ thông số theo đúng mã máy; chưa đổi database.
- **ID 5 — Apple Watch Series 12 42mm GPS**, cả hai môi trường: nội dung về sự kiện ra mắt/Readiness chưa có nguồn đối chiếu trong lần này và đoạn cuối bị cắt. Cần xác minh tài liệu hãng; chưa đổi database.
- **ID 8 — Samsung Galaxy Z Fold8 Ultra 5G**, cả hai môi trường, bản sao trên SKU 9–11: bài nói bản 1TB trong khi lựa chọn hiện có gồm 256GB/512GB. Bỏ việc trình bày 1TB như cấu hình đang bán; các con số kỹ thuật còn lại cần nguồn hãng trước khi sửa. Chưa đổi database.
- **ID 43 — Camera IP 360 5MP EZVIZ**, chỉ Render: mô tả H6C G1 5MP vừa ghi 4K, vừa ghi 2880×1620. Không giữ cách gọi 5MP là 4K; cần kiểm tra đúng mã camera trên hàng/tài liệu hãng trước khi thay bằng con số khác. Chưa đổi database.
- **ID 44 — iPhone Air**, chỉ Render: chưa thấy quảng cáo nhà bán lẻ khác. Bài giới thiệu 256GB; cần gắn dung lượng với lựa chọn SKU, không mặc định mọi lựa chọn đều là 256GB. Chưa đổi database.
- **ID 45 — Canon EOS R6 Mark III**, chỉ Render: chưa thấy quảng cáo nhà bán lẻ khác. Giữ nguyên trong phạm vi này; chưa xác minh lại toàn bộ thông số máy ảnh.

Với các model chưa có căn cứ đầy đủ, không tạo bài quảng cáo thay thế chứa thông số phỏng đoán. Chỉ hai bản thay thế ID 1/7 ở trên sẵn sàng sử dụng trong lần sửa này; các điểm kỹ thuật khác là danh sách kiểm tra tiếp theo, không phải lệnh cập nhật hàng loạt.

## Giá, lựa chọn và kiểm tra sau cập nhật

Giá ở kệ và chi tiết vẫn lấy từ `unit_price` của sản phẩm/SKU và các lựa chọn đang bán; phiên bản/màu vẫn đến từ `versions`/`variants`. Không đưa bảng giá ngoài cửa hàng vào mô tả và không thay đổi SKU, ảnh, giá, tồn kho hoặc đơn hàng khi làm sạch nội dung.

Code được commit/deploy không tự chép mô tả local sang Render. Sau khi lưu từng mô tả trên Render, kiểm tra `GET /api/v1/products/1` hoặc `/7` và trang chi tiết tương ứng: nội dung khớp bản mới, không còn CellphoneS/ưu đãi cũ, giá và các lựa chọn vẫn đúng dữ liệu SKU. Lần này chưa thực hiện thao tác ghi trên Render.
