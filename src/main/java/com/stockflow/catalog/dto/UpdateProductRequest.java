package com.stockflow.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stockflow.catalog.domain.ProductStatus;
import com.stockflow.catalog.validation.ImageUrl;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;

/**
 * DTO PATCH: null/bỏ qua giữ nguyên; mảng ảnh bổ sung rỗng xóa bộ ảnh, chuỗi trống xóa ảnh bìa/mô tả.
 */
public record UpdateProductRequest(
        @Size(max = 200, message = "Tên sản phẩm không được vượt quá 200 ký tự.")
        String name,

        @JsonProperty("unit_price")
        @DecimalMin(value = "0.01", message = "Giá sản phẩm phải lớn hơn 0.")
        BigDecimal unitPrice,

        ProductStatus status,

        @JsonProperty("image_url")
        @Size(max = 2048, message = "Đường dẫn ảnh không được vượt quá 2048 ký tự.")
        @ImageUrl
        String imageUrl,

        @Size(max = 5000, message = "Mô tả sản phẩm không được vượt quá 5.000 ký tự.")
        String description,

        @JsonProperty("image_urls")
        @Size(max = 8, message = "Chỉ được nhập tối đa 8 ảnh bổ sung.")
        List<
                @NotBlank(message = "Link ảnh bổ sung không được để trống.")
                @Size(max = 2048, message = "Link ảnh bổ sung không được vượt quá 2048 ký tự.")
                @ImageUrl String> imageUrls,

        // Bỏ qua hoặc null giữ hãng cũ; xóa hãng phải gửi clear_brand=true rõ ràng.
        @JsonProperty("brand_id")
        @Positive(message = "ID thương hiệu phải lớn hơn 0.")
        Long brandId,

        @JsonProperty("clear_brand")
        Boolean clearBrand,

        // Null/bỏ qua giữ bảng thông số; [] xóa toàn bộ dưới khóa sản phẩm hiện có.
        @Size(max = 60, message = "Chỉ được nhập tối đa 60 thông số.")
        List<@NotNull @Valid ProductSpecificationDto> specifications) {
}
