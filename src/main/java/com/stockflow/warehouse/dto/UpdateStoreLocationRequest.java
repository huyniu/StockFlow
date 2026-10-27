package com.stockflow.warehouse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO request đổi tên và địa chỉ cửa hàng công khai đã liên kết với một kho; chỉ ADMIN được gọi.
 */
public record UpdateStoreLocationRequest(
        @NotBlank(message = "Tên cửa hàng không được để trống.")
        @Size(max = 150, message = "Tên cửa hàng không được vượt quá 150 ký tự.")
        String name,

        @NotBlank(message = "Địa chỉ cửa hàng không được để trống.")
        @Size(max = 255, message = "Địa chỉ cửa hàng không được vượt quá 255 ký tự.")
        String address) {
}
