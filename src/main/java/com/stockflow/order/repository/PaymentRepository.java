package com.stockflow.order.repository;
import com.stockflow.order.domain.Payment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
/** Truy vấn một thanh toán duy nhất theo đơn; ràng buộc UNIQUE là lớp bảo vệ cuối. */
public interface PaymentRepository extends JpaRepository<Payment, Long> {
 /** Tìm thanh toán để bảo đảm gọi xác nhận lặp không tạo thêm bản ghi. */
 Optional<Payment> findByOrderId(Long orderId);
}
