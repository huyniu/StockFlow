<!-- Giỏ lưu trong phiên tab và đồng bộ đơn khách bằng API hiện có; không thay schema hoặc transaction nghiệp vụ. -->
# Giữ giỏ khi tải lại và tự cập nhật đơn hàng

Ngày triển khai: 04/10/2026.

## Giỏ hàng và chi nhánh

- Khóa `stockflow.web.cart.v1` trong `sessionStorage` có phiên bản, chủ giỏ, `warehouse_id` và danh sách `product_id`/`quantity`.
- Giỏ được giữ khi tải lại trong cùng phiên tab. Không lưu dài hạn sau khi đóng tab, đồng bộ giữa thiết bị hoặc tạo bảng `carts`.
- Không lưu giá, ảnh, mật khẩu, token hoặc người nhận trong bản ghi giỏ. JWT tiếp tục dùng khóa phiên riêng hiện có.
- Chủ giỏ là `guest` hoặc `customer:<id>`. ID/role lấy từ login hoặc `/users/me`, không lấy từ dữ liệu giỏ. Đăng xuất/đổi tài khoản xóa giỏ cũ; giỏ khách vãng lai được chuyển vào tài khoản CUSTOMER vừa đăng nhập.
- Khi khôi phục, đọc lại sản phẩm bằng API công khai theo lô tối đa bốn SKU. SKU con đọc thêm model để lấy đúng phiên bản, màu, giá và trạng thái cấu hình.
- SKU 404, ngừng bán hoặc cấu hình đã lưu trữ bị loại bỏ. Hàng tạm hết tồn vẫn ở giỏ; kiểm tra số tồn và reserve cuối cùng do backend quyết định.
- Chi nhánh được đối chiếu danh sách ACTIVE. Nếu kho đã ngừng phục vụ, chọn chi nhánh đang phục vụ đầu tiên; nếu không có kho, không tự tạo ID kho.
- JSON sai, ID/số lượng sai hoặc không đúng chủ giỏ không được tin cậy. Giới hạn 100 mặt hàng khớp contract tạo đơn hiện có.
- Lỗi mạng hoặc khôi phục quá 15 giây giữ nguyên bản lưu, khóa sửa/đặt giỏ và cho bấm **Thử lại**. Không ghi giỏ rỗng đè dữ liệu chưa đọc được hoặc giữ cả cửa hàng ở màn hình tải mãi.
- Khi trình duyệt chặn storage, giỏ vẫn dùng được trong bộ nhớ. Sau đặt hàng thành công, bản lưu giỏ được làm rỗng và giữ chi nhánh; lỗi đặt hàng không xóa giỏ.
- Thông tin người nhận vẫn phải nhập lại sau F5; chưa triển khai hồ sơ/địa chỉ lưu sẵn.

## Đồng bộ đơn của khách

- Chỉ chạy ở mục **Đơn hàng của tôi**, với role CUSTOMER và tab đang hiển thị.
- GET `/api/v1/orders/my?page=...&size=8` theo trang đang xem mỗi 15 giây. Nếu đơn được chọn nằm ngoài trang, đọc thêm GET `/api/v1/orders/{id}`.
- Khi quay lại tab hoặc kết nối trở lại, lấy lại trạng thái ngay. Focus/visibility đến cùng lúc được gom để tránh request trùng.
- Khi countdown của đơn PENDING hết hạn, kiểm tra sớm và sau đó mỗi năm giây. Không tự gán EXPIRED ở client: scheduler/transaction backend có thể chưa quét ngay tại thời điểm bộ đếm bằng 0.
- Bảng, vận đơn và nút thao tác cập nhật theo response. Khi dữ liệu không đổi, không dựng lại chi tiết hoặc thay bảng bằng skeleton.
- Rời màn hình, ẩn tab hoặc đổi actor sẽ hủy request nền. Thao tác tay/phân trang/thanh toán/hủy có ưu tiên; phản hồi cũ không được ghi đè kết quả mới.
- Chỉ có một lượt đồng bộ nền đang chạy; private GET có hạn chờ 15 giây. Lỗi mạng giữ nội dung đã xem và tăng thời gian thử lại từ 30 tới tối đa 60 giây.
- 401 dùng cơ chế hiện có để xóa phiên và thông tin riêng, đưa người dùng về cửa hàng công khai.
- Không gửi POST tự động, không thay thời điểm DISPATCH, không tự thanh toán, hủy đơn hoặc nhả tồn kho từ trình duyệt.
- Dashboard giữ cơ chế tải tay hiện tại để tránh tự làm mất mã vận đơn nhân viên đang nhập.

## File thay đổi

- `src/main/resources/static/app.js`: bản lưu/khôi phục giỏ và vòng đồng bộ đơn khách.
- `README.md`: cập nhật hành vi tải lại và đồng bộ.
- `docs/storefront-roadmap.md`: đánh dấu hai phần đã hoàn thành.
- `docs/cart-and-order-sync.md`: quy tắc và giới hạn của lượt này.

## Kiểm chứng

Kiểm tra frontend dùng Chrome headless với hồ sơ riêng, intercept API bằng dữ liệu giả lập để kiểm soát đổi giá, hết hạn, request chậm và lỗi mạng. Các request ghi nghiệp vụ không được gửi vào database đang sử dụng. Đây là kiểm chứng frontend, không thay thế kiểm thử transaction, ledger hoặc PostgreSQL.

Lệnh kiểm thử/build sử dụng Java 17 và cache Maven đã yêu cầu:

```powershell
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' test
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' -DskipTests package
```

Kết quả bản cuối:

- **526 test Maven PASS**, 0 failures/errors/skipped; thời gian 32,141 giây.
- **56 kiểm tra Chrome PASS**, 0 ngoại lệ JavaScript ngoài luồng. Kiểm tra gồm F5/đổi giá/đổi actor, cấu hình ngừng bán, JSON sai, storage bị chặn, mất mạng và request treo, polling thật theo chu kỳ, hết hạn, vận đơn, giữ phân trang/đơn đang chọn và ưu tiên thao tác thanh toán.
- Cả sáng/tối tại 390px được kiểm tra, không có tràn ngang do thông báo/đồng bộ mới. Đây không phải lượt kiểm tra lại mọi trang tại mọi kích thước.
- `package -DskipTests` sau toàn bộ suite: **BUILD SUCCESS**, JAR chứa đúng bản `app.js` cuối.
- **284 file source khác không đổi** khi đối chiếu SHA-256, gồm backend, cấu hình, migration, test, HTML/CSS và tài nguyên còn lại.

Chưa triển khai giỏ lâu dài/đa thiết bị, lưu địa chỉ trong hồ sơ, idempotency tạo đơn ở server hoặc Testcontainers PostgreSQL. Dữ liệu nghiệp vụ của luồng đơn tự cập nhật trong Chrome được giả lập, không được mô tả là kiểm thử PostgreSQL/transaction mới. Log, script và ảnh QA nằm trong `target/cart-orders-qa/` và không được đưa vào repository.
