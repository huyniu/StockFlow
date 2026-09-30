package com.stockflow.order.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Đơn hàng lưu chủ sở hữu, kho xử lý và hạn giữ chỗ; thay đổi trạng thái được dịch vụ tuần tự hóa. */
@Entity @Table(name = "orders")
public class Order {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
 @Column(name = "order_code", nullable = false, unique = true, length = 50) private String orderCode;
 @Column(name = "customer_id", nullable = false) private Long customerId;
 @Column(name = "warehouse_id", nullable = false) private Long warehouseId;
 @Enumerated(EnumType.STRING) @Column(nullable = false, length = 50) private OrderStatus status;
 @Column(name = "total_amount", nullable = false, precision = 12, scale = 2) private BigDecimal totalAmount;
 @Column(name = "reservation_expires_at") private Instant reservationExpiresAt;
 @Column(name = "created_at", nullable = false) private Instant createdAt;
 @Column(name = "updated_at", nullable = false) private Instant updatedAt;
 @OneToMany(mappedBy = "order", cascade = CascadeType.PERSIST)
 @OrderBy("id ASC") private List<OrderItem> items = new ArrayList<>();
 /** Hàm khởi tạo cho JPA. */
 protected Order() {}
 /** Tạo đơn chờ thanh toán với thời hạn giữ hàng 15 phút. */
 public Order(Long customerId, Long warehouseId, Instant now) {
  this.orderCode = "SF-" + UUID.randomUUID();
  this.customerId = customerId; this.warehouseId = warehouseId; this.status = OrderStatus.PENDING;
  this.totalAmount = BigDecimal.ZERO; this.createdAt = now; this.updatedAt = now;
  this.reservationExpiresAt = now.plusSeconds(900);
 }
 /** Thêm mặt hàng và cộng thành tiền theo giá đã chụp khi đặt hàng. */
 public void addItem(Long productId, int quantity, BigDecimal price) {
  OrderItem item = new OrderItem(this, productId, quantity, price);
  items.add(item); totalAmount = totalAmount.add(item.getLineTotal());
 }
 /** Dịch vụ chỉ gọi sau khi kiểm tra chuyển trạng thái và hoàn tất biến động tồn kho. */
 public void changeStatus(OrderStatus status) { this.status = status; this.updatedAt = Instant.now(); }
 /** Lấy mã định danh đơn. */
 public Long getId() { return id; }
 /** Lấy mã đơn dùng cho khách hàng. */
 public String getOrderCode() { return orderCode; }
 /** Lấy chủ sở hữu đơn. */
 public Long getCustomerId() { return customerId; }
 /** Lấy kho xử lý đơn. */
 public Long getWarehouseId() { return warehouseId; }
 /** Lấy trạng thái hiện tại. */
 public OrderStatus getStatus() { return status; }
 /** Lấy tổng tiền đã chụp. */
 public BigDecimal getTotalAmount() { return totalAmount; }
 /** Lấy thời điểm hết hạn giữ hàng. */
 public Instant getReservationExpiresAt() { return reservationExpiresAt; }
 /** Lấy thời điểm tạo đơn. */
 public Instant getCreatedAt() { return createdAt; }
 /** Lấy thời điểm thay đổi đơn. */
 public Instant getUpdatedAt() { return updatedAt; }
 /** Trả danh sách chỉ đọc để bảo vệ các mặt hàng đã chụp. */
 public List<OrderItem> getItems() { return Collections.unmodifiableList(items); }
}
