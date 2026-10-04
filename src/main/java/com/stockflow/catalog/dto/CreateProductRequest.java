package com.stockflow.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stockflow.catalog.domain.ProductStatus;
import com.stockflow.catalog.validation.ImageUrl;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;

/**
 * DTO tạo sản phẩm; mỗi ảnh bổ sung được validate độc lập trước khi ADMIN lưu catalog.
 */
public record CreateProductRequest(
        @NotBlank(message = "SKU không được để trống.")
        @Size(max = 80, message = "SKU không được vượt quá 80 ký tự.")
        String sku,

        @NotBlank(message = "Tên sản phẩm không được để trống.")
        @Size(max = 200, message = "Tên sản phẩm không được vượt quá 200 ký tự.")
        String name,

        @JsonProperty("category_id")
        @NotNull(message = "Danh mục sản phẩm không được để trống.")
        Long categoryId,

        @JsonProperty("unit_price")
        @NotNull(message = "Giá sản phẩm không được để trống.")
        @DecimalMin(value = "0.01", message = "Giá sản phẩm phải lớn hơn 0.")
        BigDecimal unitPrice,

        @NotNull(message = "Trạng thái sản phẩm không được để trống.")
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

        // Hãng tùy chọn để request cũ vẫn tạo được sản phẩm; chỉ tham chiếu ID có trong catalog.
        @JsonProperty("brand_id")
        @Positive(message = "ID thương hiệu phải lớn hơn 0.")
        Long brandId,

        // Request cũ không khai báo thông số vẫn hợp lệ; không tự sinh thông số theo tên model.
        @Size(max = 60, message = "Chỉ được nhập tối đa 60 thông số.")
        List<@NotNull @Valid ProductSpecificationDto> specifications) {
}
