package com.stockflow.order.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
/** Yêu cầu tạo đơn tại một kho với ít nhất một mặt hàng có số lượng dương. */
public record CreateOrderRequest(
 @JsonProperty("warehouse_id") @NotNull @Positive Long warehouseId,
 @NotEmpty @Size(max = 100, message = "Một đơn tối đa 100 mặt hàng.") List<@NotNull @Valid Item> items) {
 /** Dữ liệu một mặt hàng; giá lấy từ catalog trên server, không nhận từ client. */
 public record Item(@JsonProperty("product_id") @NotNull @Positive Long productId,
   @NotNull @Min(value = 1, message = "Số lượng đặt phải ít nhất là 1.") Integer quantity) {}
}
