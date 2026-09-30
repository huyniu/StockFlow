package com.stockflow.order.domain;
import jakarta.persistence.*;
import java.time.Instant;
/** Vận đơn của một đơn hàng; trạng thái này dùng để ngăn hủy khi hàng đã giao đi. */
@Entity @Table(name = "shipments")
public class Shipment {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
 @Column(name = "order_id", nullable = false, unique = true) private Long orderId;
 @Column(name = "tracking_code", nullable = false, unique = true, length = 100) private String trackingCode;
 @Enumerated(EnumType.STRING) @Column(nullable = false, length = 50) private ShipmentStatus status;
 @Column(name = "shipped_at") private Instant shippedAt;
 @Column(name = "delivered_at") private Instant deliveredAt;
 /** Hàm khởi tạo cho JPA. */
 protected Shipment() {}
 /** Khởi tạo vận đơn chuẩn bị giao cho giai đoạn đóng gói tiếp theo. */
 public Shipment(Long orderId, String trackingCode) {
  this.orderId = orderId; this.trackingCode = trackingCode; this.status = ShipmentStatus.PREPARING;
 }
 /** Kiểm tra hàng đã rời kho hoặc đã giao để dịch vụ chặn hoàn kho không hợp lệ. */
 public boolean hasShipped() { return status != ShipmentStatus.PREPARING || shippedAt != null; }
 /** Lấy trạng thái vận chuyển. */
 public ShipmentStatus getStatus() { return status; }
}
