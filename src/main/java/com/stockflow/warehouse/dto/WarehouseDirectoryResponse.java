package com.stockflow.warehouse.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Một dòng trong trang "Kho & chi nhánh": địa chỉ kho nội bộ đi kèm tên/địa chỉ cửa hàng công khai (nếu có).
 * Chỉ trả cho role vận hành; khách chỉ thấy phần cửa hàng qua API storefront.
 */
public record WarehouseDirectoryResponse(
        Long id,
        String code,
        String name,
        String address,
        String status,
        @JsonProperty("store_name") String storeName,
        @JsonProperty("store_address") String storeAddress) {
}
