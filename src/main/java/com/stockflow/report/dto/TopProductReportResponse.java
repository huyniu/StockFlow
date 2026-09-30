package com.stockflow.report.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/** Sản phẩm bán chạy, dùng giá snapshot trong order_items để cộng doanh thu chính xác. */
public record TopProductReportResponse(
        @JsonProperty("product_id") Long productId,
        @JsonProperty("product_sku") String productSku,
        @JsonProperty("product_name") String productName,
        @JsonProperty("category_name") String categoryName,
        @JsonProperty("total_quantity_sold") long totalQuantitySold,
        @JsonProperty("total_revenue") BigDecimal totalRevenue) {
}
