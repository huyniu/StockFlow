package com.stockflow.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stockflow.catalog.domain.Product;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * DTO catalog công khai; ảnh bổ sung giữ thứ tự đã lưu, không chứa dữ liệu tồn kho nội bộ.
 */
public record ProductResponse(
        Long id,
        String sku,
        String name,
        @JsonProperty("category_name") String categoryName,
        @JsonProperty("unit_price") BigDecimal unitPrice,
        String status,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("image_url") String imageUrl,
        String description,
        @JsonProperty("image_urls") List<String> imageUrls,
        @JsonProperty("category_id") Long categoryId,
        @JsonProperty("brand_id") Long brandId,
        @JsonProperty("brand_name") String brandName,
        List<ProductSpecificationDto> specifications,
        List<ProductVariantResponse> variants,
        @JsonProperty("parent_product_id") Long parentProductId,
        List<ProductVersionResponse> versions,
        @JsonProperty("min_price") BigDecimal minPrice,
        @JsonProperty("max_price") BigDecimal maxPrice) {

    /**
     * Chuyển entity Product sang response DTO an toàn cho API.
     */
    public static ProductResponse from(Product product) {
        return from(product, null);
    }

    /** SKU con khai báo trang gốc để storefront điều hướng về cùng một trang chọn màu. */
    public static ProductResponse from(Product product, Long parentProductId) {
        List<ProductVariantResponse> variants = product.getVariants().stream()
                .map(ProductVariantResponse::from).toList();
        List<BigDecimal> prices = variants.stream()
                .filter(variant -> "ACTIVE".equals(variant.status()))
                .map(ProductVariantResponse::unitPrice).toList();
        // Giá từ là giá SKU còn bán, không tạo mức giảm giá hoặc thay giá snapshot đơn hàng.
        BigDecimal min = prices.stream().min(BigDecimal::compareTo).orElse(product.getUnitPrice());
        BigDecimal max = prices.stream().max(BigDecimal::compareTo).orElse(product.getUnitPrice());
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getCategory().getName(),
                product.getUnitPrice(),
                product.getStatus().name(),
                product.getCreatedAt(),
                product.getImageUrl(),
                product.getDescription(),
                product.getImageUrls(),
                product.getCategory().getId(),
                product.getBrand() == null ? null : product.getBrand().getId(),
                product.getBrand() == null ? null : product.getBrand().getName(),
                product.getSpecifications().stream().map(ProductSpecificationDto::from).toList(),
                variants,
                parentProductId,
                product.getVersions().stream().map(ProductVersionResponse::from).toList(),
                min, max);
    }
}
