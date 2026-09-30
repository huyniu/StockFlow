package com.stockflow.warehouse.repository;

import com.stockflow.warehouse.domain.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository truy vấn bảng warehouses, phục vụ quản lý kho hàng trong Milestone 2.
 */
public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    /**
     * Kiểm tra mã kho đã tồn tại hay chưa vì code là định danh duy nhất của kho.
     */
    boolean existsByCode(String code);
}
