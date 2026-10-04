package com.stockflow.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** PATCH tên/thông số phiên bản; null giữ nguyên, mảng rỗng xóa thông số riêng. */
public record UpdateProductVersionRequest(
        @Pattern(regexp = "(?s).*\\S.*", message = "Tên phiên bản không được để trống.")
        @Size(max = 160) String name,

        @Size(max = 60)
        List<@NotNull @Valid ProductSpecificationDto> specifications) {
}
