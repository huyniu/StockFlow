package com.stockflow.warehouse.dto;

import com.stockflow.warehouse.domain.Warehouse;

/**
 * DTO response cho kho hàng, chỉ trả các trường cần thiết cho client.
 */
public record WarehouseResponse(Long id, String code, String name, String address, String status) {

    /**
     * Chuyển entity Warehouse sang response DTO.
     */
    public static WarehouseResponse from(Warehouse warehouse) {
        return new WarehouseResponse(
                warehouse.getId(),
                warehouse.getCode(),
                warehouse.getName(),
                warehouse.getAddress(),
                warehouse.getStatus().name());
    }
}
