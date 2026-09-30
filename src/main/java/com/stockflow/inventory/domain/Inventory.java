package com.stockflow.inventory.domain;

import com.stockflow.catalog.domain.Product;
import com.stockflow.warehouse.domain.Warehouse;
import com.stockflow.common.exception.BadRequestException;
import jakarta.persistence.*;
import java.time.Instant;

/** Tồn kho của một sản phẩm tại một kho; phiên bản ngăn ghi đè khi cập nhật đồng thời. */
@Entity
@Table(name = "inventories", uniqueConstraints = @UniqueConstraint(name = "uq_product_warehouse", columnNames = {"product_id", "warehouse_id"}))
public class Inventory {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
 private Long id;
 @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id", nullable = false)
 private Product product;
 @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "warehouse_id", nullable = false)
 private Warehouse warehouse;
 @Column(name = "available_quantity", nullable = false)
 private int availableQuantity;
 @Column(name = "reserved_quantity", nullable = false)
 private int reservedQuantity;
 @Version private Long version;
 @Column(name = "updated_at", nullable = false)
 private Instant updatedAt;
 /** Hàm khởi tạo dành cho JPA. */
 protected Inventory() {}
 /** Khởi tạo số tồn bằng không cho cặp sản phẩm và kho đã được xác minh. */
 public Inventory(Product product, Warehouse warehouse) { this.product = product; this.warehouse = warehouse; }
 /** Tăng hàng khả dụng, kiểm tra tràn số cho cả tổng tồn vật lý. */
 public void increaseAvailable(int quantity) {
  if (quantity <= 0 || (long)getPhysicalQuantity() + quantity > Integer.MAX_VALUE)
   throw new BadRequestException("Số lượng nhập không hợp lệ hoặc vượt giới hạn tồn kho.");
  availableQuantity += quantity;
  updatedAt = Instant.now();
 }
 /** Giảm hàng khả dụng, không cho phép xuất vượt số tồn. */
 public void decreaseAvailable(int quantity) {
  if (quantity <= 0 || quantity > availableQuantity)
   throw new BadRequestException("Số lượng giảm phải dương và không vượt tồn khả dụng.");
  availableQuantity -= quantity;
  updatedAt = Instant.now();
 }
 /** Đồng bộ thời điểm khi ghi dữ liệu tồn kho. */
 @PrePersist @PreUpdate
 protected void timestamp() { updatedAt = Instant.now(); }
 /** Tổng tồn vật lý bao gồm hàng khả dụng và hàng đã giữ. */
 public int getPhysicalQuantity() { return Math.addExact(availableQuantity, reservedQuantity); }
 /** Lấy mã dòng tồn kho. */
 public Long getId() { return id; }
 /** Lấy sản phẩm của dòng tồn kho. */
 public Product getProduct() { return product; }
 /** Lấy kho chứa sản phẩm. */
 public Warehouse getWarehouse() { return warehouse; }
 /** Lấy số hàng khả dụng. */
 public int getAvailableQuantity() { return availableQuantity; }
 /** Lấy số hàng đã được giữ. */
 public int getReservedQuantity() { return reservedQuantity; }
 /** Lấy phiên bản để kiểm soát cập nhật đồng thời. */
 public Long getVersion() { return version; }
 /** Lấy thời điểm cập nhật gần nhất. */
 public Instant getUpdatedAt() { return updatedAt; }
}
