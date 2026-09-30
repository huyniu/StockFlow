package com.stockflow.inventory.dto;
import com.stockflow.inventory.domain.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
/** Ảnh chụp một biến động bất biến để phục vụ kiểm toán. */
public record InventoryMovementResponse(Long id,
 @JsonProperty("inventory_id") Long inventoryId, @JsonProperty("performed_by") Long performedBy,
 MovementType type, int quantity, @JsonProperty("balance_before") int balanceBefore,
 @JsonProperty("balance_after") int balanceAfter, @JsonProperty("reference_type") String referenceType,
 @JsonProperty("reference_id") Long referenceId, String note, @JsonProperty("created_at") Instant createdAt) {
 /** Chuyển bản ghi sổ cái thành phản hồi API. */
 public static InventoryMovementResponse from(InventoryMovement m) {
  return new InventoryMovementResponse(m.getId(), m.getInventoryId(), m.getPerformedBy(), m.getType(),
   m.getQuantity(), m.getBalanceBefore(), m.getBalanceAfter(), m.getReferenceType(), m.getReferenceId(), m.getNote(), m.getCreatedAt());
 }
}
