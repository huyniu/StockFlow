# Thanh toán khi nhận hàng (COD)

- Khách chọn COD qua `POST /api/v1/orders/{id}/cod/confirm`; đơn chuyển sang CONFIRMED, payment có method COD, status PENDING và paid_at NULL.
- Khóa đơn và ledger hiện có đảm bảo xuất tồn đúng một lần, tương tự xác nhận VNPay. Nhân viên được phép đóng gói/giao COD chưa thu tiền.
- Payload GHN có cod_amount bằng tổng tiền đơn (đã gồm phí ship). Đơn thanh toán trước gửi cod_amount = 0.
- Nhân viên xác nhận giao thành công qua endpoint deliver đồng nghĩa xác nhận đã thu tiền COD: payment chuyển PAID. Chưa có webhook đối soát tiền thu hộ/tiền GHN chuyển về cửa hàng.
- Quản lý hủy COD chưa thu tiền: hoàn kho, payment FAILED, không ghi nhận hoàn tiền. Báo cáo doanh thu chỉ tính COD đã thu tiền.
- `GET /api/v1/orders/{id}/payment` trả phương thức và tình trạng thanh toán, kiểm tra quyền xem đơn.
- API thanh toán mô phỏng cũ vẫn giữ để tương thích. Giao diện khách hiển thị COD và VNPay Sandbox; logo lưu nội bộ từ https://1889324617.cloud.edgevnpay.vn/assets/images/logo-icon/logo-primary.svg (website https://vnpay.vn/).
