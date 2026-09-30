package com.stockflow.catalog.domain;

/**
 * Trạng thái sản phẩm trong catalog. Giá trị enum này ánh xạ trực tiếp với constraint trong bảng products.
 */
public enum ProductStatus {
    /**
     * Sản phẩm đang được bán hoặc hiển thị trong danh sách sản phẩm.
     */
    ACTIVE,

    /**
     * Sản phẩm tạm ngưng bán nhưng vẫn được giữ lại để bảo toàn lịch sử dữ liệu.
     */
    INACTIVE
}
