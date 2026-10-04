package com.stockflow.order.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Yêu cầu tạo đơn tại một kho; thông tin nhận hàng bắt buộc được chụp cùng giá và mặt hàng. */
public record CreateOrderRequest(
        @JsonProperty("warehouse_id") @NotNull @Positive Long warehouseId,
        @NotEmpty @Size(max = 100, message = "Một đơn tối đa 100 mặt hàng.")
        List<@NotNull @Valid Item> items,
        @NotNull(message = "Vui lòng nhập thông tin nhận hàng.") @Valid DeliveryDetailsRequest delivery) {

    /** Dữ liệu một mặt hàng; giá lấy từ catalog trên server, không nhận từ client. */
    public record Item(
            @JsonProperty("product_id") @NotNull @Positive Long productId,
            @NotNull @Min(value = 1, message = "Số lượng đặt phải ít nhất là 1.") Integer quantity) {}
}
