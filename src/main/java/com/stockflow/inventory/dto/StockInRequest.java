package com.stockflow.inventory.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
/** Dữ liệu nhập kho; số lượng bắt buộc dương và ghi chú tối đa 255 ký tự. */
public record StockInRequest(
 @JsonProperty("product_id") @NotNull @Positive Long productId,
 @JsonProperty("warehouse_id") @NotNull @Positive Long warehouseId,
 @NotNull @Min(value = 1, message = "Số lượng nhập phải ít nhất là 1.") Integer quantity,
 @Size(max = 255, message = "Ghi chú không được vượt quá 255 ký tự.") String note) {}
