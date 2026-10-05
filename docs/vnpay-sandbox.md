# VNPay Sandbox

Giữ nguyên API/nút mô phỏng thanh toán. CUSTOMER có thêm nút `Thanh toán qua VNPay (Sandbox)` trong chi tiết đơn PENDING, bao gồm đơn vừa đặt thành công.

## Cấu hình

`application.yml` và `application-demo.yml` đọc `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, `VNPAY_RETURN_URL`. Giá trị DEMOVNPAY/DEMOHASHSECRETKEY2026 chỉ là placeholder cho local/test, cần thay bằng thông tin merchant do VNPay Sandbox cấp để thanh toán trên cổng thật. `VNPAY_STOREFRONT_URL` mặc định `http://localhost:8080/`; đổi cùng return URL khi dùng host/port khác. Không lưu secret thật vào Git.

## API và dữ liệu

- `POST /api/v1/payments/vnpay/create`, JWT CUSTOMER và `{ "order_id": 123 }`: chỉ chủ đơn PENDING còn hạn giữ hàng được tạo URL; response `{ "payment_url": "..." }`.
- `GET /api/v1/payments/vnpay/return`: công khai để browser trở về từ VNPay; callback chữ ký sai, số tiền/merchant sai hoặc tham số lặp trả 400. Callback hợp lệ redirect về `/#orders?payment_status=success|failed&order_id=123`.
- Chữ ký HMAC-SHA512 dùng key/data UTF-8 và HEX chữ thường. Key tham số được `Collections.sort`; hashData giữ nguyên key và encode value bằng US-ASCII, query encode cả key/value bằng US-ASCII theo mẫu Java VNPay. Loại bỏ `vnp_SecureHash`/`vnp_SecureHashType`. Amount lấy từ snapshot đơn và nhân 100; thời gian theo GMT+7.
- V19 tạo `payment_transactions`. Ngoài các cột yêu cầu, có `txn_ref` unique để lưu `vnp_TxnRef` của từng lần tạo URL; `transaction_code` dành riêng cho `vnp_TransactionNo` trả về.
- Callback thành công yêu cầu cả `vnp_ResponseCode=00` và `vnp_TransactionStatus=00`, số tiền khớp và đơn vẫn PENDING. Khóa đơn chung với mô phỏng/hủy/hết hạn; xác nhận và ghi DISPATCH/payment/transaction trong cùng transaction. Callback lặp không xuất kho lần nữa. Đơn hết hạn được nhả hàng, không xác nhận.
- Frontend tải trạng thái đơn từ backend khi trở về; query string không tự sửa trạng thái đơn. Khi cần đăng nhập lại, thông tin callback được giữ trong phiên trang để mở đúng đơn sau đăng nhập.

Luồng này triển khai callback Return URL cho Sandbox theo yêu cầu. IPN server-to-server, đối soát và hoàn tiền VNPay chưa nằm trong phạm vi này; hoàn tiền hiện tại của StockFlow là trạng thái nội bộ/mô phỏng, không gọi API hoàn tiền VNPay.

Tài liệu chuẩn: [VNPay PAY 2.1.0](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html).

Kiểm chứng: `.\mvnw.cmd "-Dmaven.repo.local=C:/Users/Admin/.m2/repository" test`.

## Kiểm tra lỗi sai chữ ký

Console in `[VNPAY] TMN Code đang chạy`, `[VNPAY] Hash Data`, `[VNPAY] Secure Hash` và `[VNPAY] paymentUrl`. Các tên tham số VNPay chuẩn không cần escape, nên hashData/query trước `vnp_SecureHash` giống nhau; khi key có ký tự cần encode thì chỉ query encode key, hashData giữ nguyên key. Không encode lại URL trước khi redirect. `vnp_OrderInfo` là `ThanhToanDonHang<id>` không dấu và không có khoảng trắng.

Nếu runtime còn dùng credential placeholder, console có cảnh báo riêng. Cần đặt mã merchant và hash secret Sandbox thuộc cùng tài khoản, rồi khởi động lại ứng dụng; log không in hash secret. Chuẩn hóa encode không thể sửa lỗi do sai credential.

Docker Compose đã truyền các biến `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, `VNPAY_RETURN_URL`, `VNPAY_STOREFRONT_URL` vào container. Sau khi đổi `.env`, tạo lại container bằng `docker compose up -d --build` để cập nhật environment. Spring Boot chạy từ IntelliJ/Maven cần biến môi trường trong chính Run Configuration/shell; file `.env` của Compose không tự được Spring Boot đọc.

TMN Code và hash secret được trim trước khi dùng cho cả tạo URL và kiểm tra callback. Console chỉ hiện 4 ký tự đầu/cuối của secret và độ dài; secret quá ngắn được che toàn bộ. Runtime cảnh báo nếu secret khác dấu hiệu đã yêu cầu XWMV...TFFC hoặc độ dài khác 32. Secret đầy đủ không được log.
