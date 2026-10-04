package com.stockflow.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stockflow.catalog.domain.ProductVersion;
import java.util.List;

/** Cấu hình cho bộ chọn phiên bản; khách chỉ chọn các màu có version_id tương ứng. */
public record ProductVersionResponse(
        Long id,
        String name,
        List<ProductSpecificationDto> specifications,
        @JsonProperty("effective_specifications") List<ProductSpecificationDto> effectiveSpecifications,
        boolean archived) {

    /** Trả cả bảng riêng để ADMIN sửa và bảng đã ghép để cửa hàng hiển thị đúng cấu hình. */
    public static ProductVersionResponse from(ProductVersion version) {
        return new ProductVersionResponse(
                version.getId(), version.getName(),
                version.getSpecifications().stream().map(ProductSpecificationDto::from).toList(),
                version.getEffectiveSpecifications().stream().map(ProductSpecificationDto::from).toList(),
                version.isArchived());
    }
}
