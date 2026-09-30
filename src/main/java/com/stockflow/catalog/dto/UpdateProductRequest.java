package com.stockflow.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stockflow.catalog.domain.ProductStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * DTO request cập nhật sản phẩm. Các field đều optional để hỗ trợ PATCH từng phần.
 */
public record UpdateProductRequest(
        @Size(max = 200, message = "Tên sản phẩm không được vượt quá 200 ký tự.")
        String name,

        @JsonProperty("unit_price")
        @DecimalMin(value = "0.01", message = "Giá sản phẩm phải lớn hơn 0.")
        BigDecimal unitPrice,

        ProductStatus status) {
}
