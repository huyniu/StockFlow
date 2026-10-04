package com.stockflow.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stockflow.catalog.validation.ImageUrl;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/** DTO tạo hãng; danh mục gợi ý là tùy chọn và phải tham chiếu danh mục đã tồn tại. */
public record CreateBrandRequest(
        @NotBlank(message = "Tên thương hiệu không được để trống.")
        @Size(max = 150, message = "Tên thương hiệu không được vượt quá 150 ký tự.")
        String name,

        @NotBlank(message = "Slug thương hiệu không được để trống.")
        @Size(max = 180, message = "Slug thương hiệu không được vượt quá 180 ký tự.")
        String slug,

        @JsonProperty("category_ids")
        @Size(max = 100, message = "Chỉ được chọn tối đa 100 danh mục gợi ý.")
        List<
                @NotNull(message = "ID danh mục gợi ý không được rỗng.")
                @Positive(message = "ID danh mục gợi ý phải lớn hơn 0.") Long> categoryIds,

        // Logo tùy chọn để request tạo hãng trước đây vẫn tương thích.
        @JsonProperty("logo_url")
        @Size(max = 2048, message = "Đường dẫn logo không được vượt quá 2048 ký tự.")
        @ImageUrl
        String logoUrl) {
}
