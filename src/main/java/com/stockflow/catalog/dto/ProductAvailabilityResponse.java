package com.stockflow.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Tình trạng bán công khai của một SKU tại chi nhánh, không tiết lộ số tồn hoặc sổ cái nội bộ. */
public record ProductAvailabilityResponse(
        @JsonProperty("product_id") Long productId,
        @JsonProperty("warehouse_id") Long warehouseId,
        @JsonProperty("warehouse_code") String warehouseCode,
        @JsonProperty("warehouse_name") String warehouseName,
        @JsonProperty("in_stock") boolean inStock) {
}
