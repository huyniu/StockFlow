package com.stockflow.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stockflow.catalog.domain.Product;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO response cho sản phẩm, bao gồm tên danh mục để client không cần gọi thêm category endpoint.
 */
public record ProductResponse(
        Long id,
        String sku,
        String name,
        @JsonProperty("category_name") String categoryName,
        @JsonProperty("unit_price") BigDecimal unitPrice,
        String status,
        @JsonProperty("created_at") Instant createdAt) {

    /**
     * Chuyển entity Product sang response DTO an toàn cho API.
     */
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getCategory().getName(),
                product.getUnitPrice(),
                product.getStatus().name(),
                product.getCreatedAt());
    }
}
