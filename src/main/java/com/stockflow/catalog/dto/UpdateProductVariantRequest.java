package com.stockflow.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stockflow.catalog.domain.ProductStatus;
import com.stockflow.catalog.validation.ImageUrl;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.constraints.NotBlank;

/** Sửa tên/mã màu và dữ liệu bán hàng; giữ nguyên SKU và liên kết lịch sử. */
public record UpdateProductVariantRequest(
        @JsonProperty("unit_price")
        @DecimalMin(value = "0.01", message = "Giá màu phải lớn hơn 0.")
        @Digits(integer = 10, fraction = 2, message = "Giá tối đa 10 chữ số nguyên và 2 chữ số thập phân.")
        BigDecimal unitPrice,
        ProductStatus status,
        @JsonProperty("image_url") @Size(max = 2048) @ImageUrl String imageUrl,
        @JsonProperty("image_urls") @Size(max = 8)
        List<@NotBlank @Size(max = 2048) @ImageUrl String> imageUrls,
        @JsonProperty("color_name") @Size(max = 80)
        @jakarta.validation.constraints.Pattern(regexp = "(?s).*\\S.*", message = "Tên màu không được để trống.")
        String colorName,
        @JsonProperty("color_hex")
        @jakarta.validation.constraints.Pattern(regexp = "#[0-9a-fA-F]{6}", message = "Mã màu phải có dạng #000000.")
        String colorHex) {
    public UpdateProductVariantRequest(BigDecimal unitPrice, ProductStatus status, String imageUrl, List<String> imageUrls) {
        this(unitPrice, status, imageUrl, imageUrls, null, null);
    }
}
