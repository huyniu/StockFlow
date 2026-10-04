package com.stockflow.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stockflow.catalog.validation.ImageUrl;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cập nhật logo của hãng đã có; chuỗi rỗng xóa ảnh, thiếu trường/null trả 400 để tránh xóa ngoài ý muốn. */
public record UpdateBrandLogoRequest(
        @JsonProperty("logo_url")
        @NotNull(message = "Vui lòng gửi logo_url; dùng chuỗi rỗng nếu muốn xóa logo.")
        @Size(max = 2048, message = "Đường dẫn logo không được vượt quá 2048 ký tự.")
        @ImageUrl
        String logoUrl) {
}
