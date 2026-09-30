package com.stockflow.inventory.dto;
import com.stockflow.inventory.domain.Inventory;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
/** Dữ liệu tồn kho trả về cho người vận hành, không để lộ entity JPA. */
public record InventoryResponse(Long id,
 @JsonProperty("product_id") Long productId, @JsonProperty("product_name") String productName,
 @JsonProperty("warehouse_id") Long warehouseId, @JsonProperty("warehouse_name") String warehouseName,
 @JsonProperty("available_quantity") int availableQuantity, @JsonProperty("reserved_quantity") int reservedQuantity,
 @JsonProperty("physical_quantity") int physicalQuantity, @JsonProperty("updated_at") Instant updatedAt) {
 /** Chuyển dòng tồn kho sang dữ liệu phản hồi trong transaction đọc. */
 public static InventoryResponse from(Inventory i) {
  return new InventoryResponse(i.getId(), i.getProduct().getId(), i.getProduct().getName(),
   i.getWarehouse().getId(), i.getWarehouse().getName(), i.getAvailableQuantity(), i.getReservedQuantity(),
   i.getPhysicalQuantity(), i.getUpdatedAt());
 }
}
