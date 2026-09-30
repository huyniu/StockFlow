package com.stockflow.warehouse.dto;

import com.stockflow.warehouse.domain.Warehouse;

/** Lựa chọn kho cho đơn hàng, chỉ công bố ID/mã/tên, không trả địa chỉ hoặc thông tin vận hành. */
public record WarehouseOrderOptionResponse(Long id, String code, String name) {

    /** Chuyển kho hoạt động sang DTO tối thiểu cho người dùng đã đăng nhập. */
    public static WarehouseOrderOptionResponse from(Warehouse warehouse) {
        return new WarehouseOrderOptionResponse(warehouse.getId(), warehouse.getCode(), warehouse.getName());
    }
}
