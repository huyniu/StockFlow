package com.stockflow.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stockflow.catalog.domain.ProductStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * DTO request tạo sản phẩm mới, bao gồm SKU, danh mục, tên, giá và trạng thái.
 */
public record CreateProductRequest(
        @NotBlank(message = "SKU không được để trống.")
        @Size(max = 80, message = "SKU không được vượt quá 80 ký tự.")
        String sku,

        @NotBlank(message = "Tên sản phẩm không được để trống.")
        @Size(max = 200, message = "Tên sản phẩm không được vượt quá 200 ký tự.")
        String name,

        @JsonProperty("category_id")
        @NotNull(message = "Danh mục sản phẩm không được để trống.")
        Long categoryId,

        @JsonProperty("unit_price")
        @NotNull(message = "Giá sản phẩm không được để trống.")
        @DecimalMin(value = "0.01", message = "Giá sản phẩm phải lớn hơn 0.")
        BigDecimal unitPrice,

        @NotNull(message = "Trạng thái sản phẩm không được để trống.")
        ProductStatus status) {
}
