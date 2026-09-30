package com.stockflow.warehouse.dto;

import com.stockflow.warehouse.domain.WarehouseStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO request tạo kho mới, bao gồm mã kho, tên, địa chỉ và trạng thái.
 */
public record CreateWarehouseRequest(
        @NotBlank(message = "Mã kho không được để trống.")
        @Size(max = 80, message = "Mã kho không được vượt quá 80 ký tự.")
        String code,

        @NotBlank(message = "Tên kho không được để trống.")
        @Size(max = 150, message = "Tên kho không được vượt quá 150 ký tự.")
        String name,

        @NotBlank(message = "Địa chỉ kho không được để trống.")
        @Size(max = 255, message = "Địa chỉ kho không được vượt quá 255 ký tự.")
        String address,

        @NotNull(message = "Trạng thái kho không được để trống.")
        WarehouseStatus status) {
}
