package com.stockflow.order.domain;
import jakarta.persistence.*;
import java.math.BigDecimal;
/** Mặt hàng của đơn, lưu snapshot giá và số lượng tại thời điểm đặt hàng. */
@Entity @Table(name = "order_items", uniqueConstraints = @UniqueConstraint(columnNames = {"order_id", "product_id"}))
public class OrderItem {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_id", nullable = false) private Order order;
 @Column(name = "product_id", nullable = false) private Long productId;
 @Column(nullable = false) private int quantity;
 @Column(name = "unit_price", nullable = false, precision = 12, scale = 2) private BigDecimal unitPrice;
 @Column(name = "line_total", nullable = false, precision = 12, scale = 2) private BigDecimal lineTotal;
 /** Hàm khởi tạo cho JPA. */
 protected OrderItem() {}
 /** Chụp giá và tính thành tiền bằng BigDecimal để không mất độ chính xác. */
 public OrderItem(Order order, Long productId, int quantity, BigDecimal unitPrice) {
  this.order = order; this.productId = productId; this.quantity = quantity; this.unitPrice = unitPrice;
  this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
 }
 /** Lấy mã dòng mặt hàng. */
 public Long getId() { return id; }
 /** Lấy sản phẩm được đặt. */
 public Long getProductId() { return productId; }
 /** Lấy số lượng đặt. */
 public int getQuantity() { return quantity; }
 /** Lấy giá đã chụp. */
 public BigDecimal getUnitPrice() { return unitPrice; }
 /** Lấy thành tiền của dòng. */
 public BigDecimal getLineTotal() { return lineTotal; }
}
