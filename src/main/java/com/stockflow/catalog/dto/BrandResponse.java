package com.stockflow.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Tên/logo công khai của hãng và ID các danh mục có gợi ý hoặc sản phẩm thuộc hãng đó.
 * Frontend dùng ID để lọc, không dò chuỗi tên/SKU để đoán nhà sản xuất.
 */
public record BrandResponse(
        Long id,
        String name,
        String slug,
        @JsonProperty("logo_url") String logoUrl,
        @JsonProperty("category_ids") List<Long> categoryIds) {
}
