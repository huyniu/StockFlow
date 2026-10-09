package com.stockflow.inventory.dto;
import com.stockflow.inventory.domain.*;
import com.stockflow.catalog.domain.Product;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
/** Biến động bất biến kèm thông tin sản phẩm hiện tại để nhận diện trên giao diện. */
public record InventoryMovementResponse(Long id,
 @JsonProperty("inventory_id") Long inventoryId, @JsonProperty("performed_by") Long performedBy,
 MovementType type, int quantity, @JsonProperty("balance_before") int balanceBefore,
 @JsonProperty("balance_after") int balanceAfter, @JsonProperty("reference_type") String referenceType,
 @JsonProperty("reference_id") Long referenceId, String note, @JsonProperty("created_at") Instant createdAt,
 @JsonProperty("product_id") Long productId, @JsonProperty("product_name") String productName,
 @JsonProperty("product_sku") String productSku, @JsonProperty("image_url") String imageUrl) {
 /** Chuyển bản ghi sổ cái thành phản hồi API. */
 public static InventoryMovementResponse from(InventoryMovement m) {
  return from(m, null);
 }
 /** Thông tin catalog chỉ phục vụ hiển thị, không thay đổi snapshot số lượng của ledger. */
 public static InventoryMovementResponse from(InventoryMovement m, Product product) {
  return new InventoryMovementResponse(m.getId(), m.getInventoryId(), m.getPerformedBy(), m.getType(),
   m.getQuantity(), m.getBalanceBefore(), m.getBalanceAfter(), m.getReferenceType(), m.getReferenceId(), m.getNote(), m.getCreatedAt(),
   product == null ? null : product.getId(), product == null ? null : product.getName(),
   product == null ? null : product.getSku(), product == null ? null : product.getImageUrl());
 }
}
