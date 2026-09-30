package com.stockflow.warehouse.domain;

/**
 * Trạng thái kho hàng, dùng để đánh dấu kho còn hoạt động hay đã ngừng dùng.
 */
public enum WarehouseStatus {
    /**
     * Kho đang hoạt động và có thể tham gia các nghiệp vụ tồn kho sau này.
     */
    ACTIVE,

    /**
     * Kho đã ngừng hoạt động nhưng vẫn giữ dữ liệu để phục vụ lịch sử và báo cáo.
     */
    INACTIVE
}
