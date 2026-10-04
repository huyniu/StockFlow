package com.stockflow.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stockflow.catalog.domain.ProductStatus;
import com.stockflow.catalog.domain.ProductVariant;
import java.math.BigDecimal;
import java.util.List;

/** Một SKU phiên bản/màu; sku_product_id là product_id cần gửi khi đặt hàng/nhập kho. */
public record ProductVariantResponse(
        Long id,
        @JsonProperty("sku_product_id") Long skuProductId,
        String sku,
        @JsonProperty("color_name") String colorName,
        @JsonProperty("color_hex") String colorHex,
        @JsonProperty("unit_price") BigDecimal unitPrice,
        String status,
        @JsonProperty("image_url") String imageUrl,
        @JsonProperty("image_urls") List<String> imageUrls,
        @JsonProperty("version_id") Long versionId,
        @JsonProperty("version_name") String versionName,
        boolean archived) {

    /** Không công khai số tồn chi tiết từng kho hoặc dữ liệu nội bộ của ledger. */
    public static ProductVariantResponse from(ProductVariant variant) {
        var sku = variant.getSkuProduct();
        boolean active = variant.isEnabled()
                && !variant.isArchived()
                && !variant.getVersion().isArchived()
                && sku.getStatus() == ProductStatus.ACTIVE
                && variant.getProduct().getStatus() == ProductStatus.ACTIVE;
        return new ProductVariantResponse(
                variant.getId(), sku.getId(), sku.getSku(), variant.getColorName(), variant.getColorHex(),
                sku.getUnitPrice(), active ? "ACTIVE" : "INACTIVE", sku.getImageUrl(), sku.getImageUrls(),
                variant.getVersion().getId(), variant.getVersion().getName(), variant.isArchived());
    }
}
