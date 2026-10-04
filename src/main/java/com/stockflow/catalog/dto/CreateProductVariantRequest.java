package com.stockflow.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stockflow.catalog.validation.ImageUrl;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/** Thêm SKU màu vào phiên bản; contract V11 bỏ version_id dùng phiên bản của SKU gốc. */
public record CreateProductVariantRequest(
        @NotBlank(message = "SKU màu mới không được để trống.")
        @Size(max = 80, message = "SKU tối đa 80 ký tự.") String sku,

        @JsonProperty("color_name")
        @NotBlank(message = "Tên màu mới không được để trống.")
        @Size(max = 80, message = "Tên màu tối đa 80 ký tự.") String colorName,

        @JsonProperty("color_hex")
        @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "Mã màu phải có dạng #RRGGBB.") String colorHex,

        @JsonProperty("unit_price")
        @DecimalMin(value = "0.01", message = "Giá màu mới phải lớn hơn 0.")
        @Digits(integer = 10, fraction = 2, message = "Giá tối đa 10 chữ số nguyên và 2 chữ số thập phân.")
        BigDecimal unitPrice,

        @JsonProperty("image_url") @Size(max = 2048) @ImageUrl String imageUrl,

        @JsonProperty("image_urls") @Size(max = 8, message = "Tối đa 8 ảnh bổ sung.")
        List<@NotBlank @Size(max = 2048) @ImageUrl String> imageUrls,

        @JsonProperty("default_color_name") @Size(max = 80) String defaultColorName,

        @JsonProperty("default_color_hex")
        @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "Mã màu gốc phải có dạng #RRGGBB.")
        String defaultColorHex,

        @JsonProperty("version_id")
        @jakarta.validation.constraints.Positive(message = "ID phiên bản phải lớn hơn 0.") Long versionId) {
}
