package com.stockflow.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO request tạo danh mục mới, chỉ cho phép client gửi tên và slug.
 */
public record CreateCategoryRequest(
        @NotBlank(message = "Tên danh mục không được để trống.")
        @Size(max = 150, message = "Tên danh mục không được vượt quá 150 ký tự.")
        String name,

        @NotBlank(message = "Slug danh mục không được để trống.")
        @Size(max = 180, message = "Slug danh mục không được vượt quá 180 ký tự.")
        String slug) {
}
