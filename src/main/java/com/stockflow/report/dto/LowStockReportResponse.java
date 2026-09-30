package com.stockflow.report.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Hàng có tồn khả dụng thấp, kèm phần đã giữ và tổng tồn vật lý để quản lý quyết định nhập hàng. */
public record LowStockReportResponse(
        @JsonProperty("product_id") Long productId,
        @JsonProperty("product_sku") String productSku,
        @JsonProperty("product_name") String productName,
        @JsonProperty("warehouse_id") Long warehouseId,
        @JsonProperty("warehouse_name") String warehouseName,
        @JsonProperty("available_quantity") int availableQuantity,
        @JsonProperty("reserved_quantity") int reservedQuantity,
        @JsonProperty("physical_quantity") long physicalQuantity) {
}
