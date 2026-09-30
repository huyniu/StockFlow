package com.stockflow.inventory.domain;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/** Bản ghi kiểm toán chỉ được thêm mới, không có phương thức chỉnh sửa hay xóa. */
@Entity @Immutable @Table(name = "inventory_movements")
public class InventoryMovement {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
 @Column(name = "inventory_id", nullable = false, updatable = false) private Long inventoryId;
 @Column(name = "performed_by", nullable = false, updatable = false) private Long performedBy;
 @Enumerated(EnumType.STRING) @Column(nullable = false, length = 50, updatable = false) private MovementType type;
 @Column(nullable = false, updatable = false) private int quantity;
 @Column(name = "balance_before", nullable = false, updatable = false) private int balanceBefore;
 @Column(name = "balance_after", nullable = false, updatable = false) private int balanceAfter;
 @Column(name = "reference_type", length = 50, updatable = false) private String referenceType;
 @Column(name = "reference_id", updatable = false) private Long referenceId;
 @Column(length = 255, updatable = false) private String note;
 @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
 /** Hàm khởi tạo dành riêng cho JPA. */
 protected InventoryMovement() {}
 /** Chụp số tồn vật lý và người thực hiện tại thời điểm phát sinh nghiệp vụ. */
 public InventoryMovement(Long inventoryId, Long performedBy, MovementType type, int quantity,
   int balanceBefore, int balanceAfter, String referenceType, Long referenceId, String note) {
  this.inventoryId = inventoryId; this.performedBy = performedBy; this.type = type;
  this.quantity = quantity; this.balanceBefore = balanceBefore; this.balanceAfter = balanceAfter;
  this.referenceType = referenceType; this.referenceId = referenceId; this.note = note;
  this.createdAt = Instant.now();
 }
 /** Ngăn xóa bản ghi qua JPA; database cũng chặn thao tác SQL trực tiếp. */
 @PreRemove private void preventDelete() { throw new IllegalStateException("Không được xóa lịch sử kiểm toán kho."); }
 /** Lấy mã biến động. */
 public Long getId() { return id; }
 /** Lấy mã dòng tồn kho. */
 public Long getInventoryId() { return inventoryId; }
 /** Lấy mã người thực hiện. */
 public Long getPerformedBy() { return performedBy; }
 /** Lấy loại biến động. */
 public MovementType getType() { return type; }
 /** Lấy số lượng của nghiệp vụ. */
 public int getQuantity() { return quantity; }
 /** Lấy tồn vật lý trước biến động. */
 public int getBalanceBefore() { return balanceBefore; }
 /** Lấy tồn vật lý sau biến động. */
 public int getBalanceAfter() { return balanceAfter; }
 /** Lấy loại chứng từ tham chiếu. */
 public String getReferenceType() { return referenceType; }
 /** Lấy mã chứng từ tham chiếu. */
 public Long getReferenceId() { return referenceId; }
 /** Lấy ghi chú nghiệp vụ. */
 public String getNote() { return note; }
 /** Lấy thời điểm ghi sổ. */
 public Instant getCreatedAt() { return createdAt; }
}
