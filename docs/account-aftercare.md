# Tài khoản và dịch vụ sau bán hàng

Khởi động lại Spring Boot sau khi cập nhật để Flyway chạy V28–V30. Địa chỉ mặc định cũ được chuyển sang sổ địa chỉ tự động.

## Khách hàng

- Menu tên tài khoản → Thông tin cá nhân → Sổ địa chỉ. Thêm, sửa, xóa hoặc chọn địa chỉ mặc định; tối đa 20 địa chỉ.
- Khi đặt hàng, chọn địa chỉ đã lưu hoặc nhập địa chỉ khác. Hệ thống dùng mã quận/phường GHN và tính lại cước.
- Menu tài khoản → Đổi mật khẩu. Nhập mật khẩu hiện tại và mật khẩu mới; phiên cũ kết thúc. Tài khoản Google chưa có mật khẩu dùng Quên mật khẩu để thiết lập.
- Chi tiết đơn đã giao → Yêu cầu đổi / trả hàng. Chọn loại, nhập lý do, đính kèm ảnh. Theo dõi ở menu tài khoản → Đổi / trả hàng; bổ sung ảnh khi chờ duyệt.

## Quản trị / quản lý

- Cổng quản trị → Đổi / trả hàng. Xem lý do, ảnh, duyệt hoặc từ chối kèm phản hồi.
- Chỉ bấm Xác nhận đã nhận lại hàng sau khi kiểm tra hàng thực tế. Thao tác lặp không cộng tồn hai lần.
- Chức năng áp dụng đổi/trả toàn bộ đơn. Hàng thay thế hoặc hoàn tiền thực tế xử lý riêng; không tự gọi API refund VNPay.

## API

- `GET/POST /api/v1/users/me/addresses`
- `PUT/DELETE /api/v1/users/me/addresses/{id}`
- `POST /api/v1/users/me/addresses/{id}/default`
- `POST /api/v1/users/me/password`: `current_password`, `new_password`
- `GET/POST /api/v1/returns`: `order_id`, `kind=RETURN|EXCHANGE`, `reason`
- `GET /api/v1/returns/{id}`
- `POST /api/v1/returns/{id}/images`: multipart `file`
- `GET /api/v1/returns/{id}/images/{imageId}`: JWT, không cache
- `POST /api/v1/returns/{id}/review`: `decision=APPROVED|REJECTED`, `note`
- `POST /api/v1/returns/{id}/receive`

Email trạng thái dùng Gmail SMTP hiện có. Outbox giữ thông báo khi gửi lỗi. Test suite dùng mail mock, không gửi thư thật.
