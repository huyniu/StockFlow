package com.stockflow.order.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Mã vận đơn tùy chọn khi xuất giao; bỏ trống trường này để dùng mã StockFlow đã cấp lúc pack. */
public record ShipOrderRequest(
        @JsonProperty("tracking_code")
        @JsonAlias("trackingCode")
        @Size(max = 100, message = "Mã vận đơn tối đa 100 ký tự.")
        @Pattern(
                regexp = "[A-Za-z0-9][A-Za-z0-9._-]*",
                message = "Mã vận đơn chỉ gồm chữ, số, dấu chấm, gạch ngang hoặc gạch dưới.")
        String trackingCode) {
}
