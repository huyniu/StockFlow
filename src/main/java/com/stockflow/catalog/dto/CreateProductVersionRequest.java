package com.stockflow.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Khai báo phiên bản; lần đầu cần tên màu của SKU gốc để giữ nguyên hàng đã nhập. */
public record CreateProductVersionRequest(
        @NotBlank(message = "Tên phiên bản không được để trống.")
        @Size(max = 160, message = "Tên phiên bản tối đa 160 ký tự.") String name,

        @JsonProperty("default_color_name") @Size(max = 80) String defaultColorName,

        @JsonProperty("default_color_hex")
        @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "Mã màu phải có dạng #RRGGBB.") String defaultColorHex,

        @Size(max = 60, message = "Tối đa 60 thông số riêng.")
        List<@NotNull @Valid ProductSpecificationDto> specifications) {
}
