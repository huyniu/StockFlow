package com.stockflow.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * DTO tạo danh mục: bỏ parent_id để tạo nhóm gốc, hoặc chọn nhóm cha đã tồn tại.
 */
public record CreateCategoryRequest(
        @NotBlank(message = "Tên danh mục không được để trống.")
        @Size(max = 150, message = "Tên danh mục không được vượt quá 150 ký tự.")
        String name,

        @NotBlank(message = "Slug danh mục không được để trống.")
        @Size(max = 180, message = "Slug danh mục không được vượt quá 180 ký tự.")
        String slug,

        @JsonProperty("parent_id")
        @Positive(message = "ID danh mục cha phải lớn hơn 0.")
        Long parentId) {
}
