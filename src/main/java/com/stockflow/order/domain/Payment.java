package com.stockflow.order.domain;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
/** Một thanh toán mô phỏng duy nhất cho mỗi đơn, có thể hoàn tiền khi quản lý hủy trước giao hàng. */
@Entity @Table(name = "payments")
public class Payment {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
 @Column(name = "order_id", nullable = false, unique = true) private Long orderId;
 @Enumerated(EnumType.STRING) @Column(nullable = false, length = 50) private PaymentStatus status;
 @Column(nullable = false, precision = 12, scale = 2) private BigDecimal amount;
 @Column(nullable = false, length = 50) private String method;
 @Column(name = "paid_at") private Instant paidAt;
 /** Hàm khởi tạo cho JPA. */
 protected Payment() {}
 /** Ghi nhận thanh toán thành công sau khi xuất phần tồn đã giữ. */
 public Payment(Long orderId, BigDecimal amount) {
  this(orderId, amount, "SIMULATED_BANKING");
 }
 public Payment(Long orderId, BigDecimal amount, String method) {
  this.orderId = orderId; this.amount = amount; this.status = PaymentStatus.PAID;
  this.method = method; this.paidAt = Instant.now();
 }
 /** Hoàn tiền mô phỏng khi đơn đã xác nhận bị hủy trước giao hàng. */
 public void refund() { status = PaymentStatus.REFUNDED; }
 /** Lấy mã thanh toán. */
 public Long getId() { return id; }
 /** Lấy trạng thái thanh toán. */
 public PaymentStatus getStatus() { return status; }
}
