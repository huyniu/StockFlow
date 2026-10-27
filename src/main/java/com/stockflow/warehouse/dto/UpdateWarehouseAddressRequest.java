package com.stockflow.warehouse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO request đổi địa chỉ kho nội bộ; chỉ ADMIN được gọi.
 */
public record UpdateWarehouseAddressRequest(
        @NotBlank(message = "Địa chỉ kho không được để trống.")
        @Size(max = 255, message = "Địa chỉ kho không được vượt quá 255 ký tự.")
        String address) {
}
