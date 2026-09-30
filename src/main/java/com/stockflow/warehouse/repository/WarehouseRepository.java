package com.stockflow.warehouse.repository;

import com.stockflow.warehouse.domain.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository truy vấn bảng warehouses, phục vụ quản lý kho hàng trong Milestone 2.
 */
public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    /** Tìm mã kho ổn định để seed chỉ tạo kho chưa tồn tại. */
    java.util.Optional<Warehouse> findByCode(String code);

    /**
     * Kiểm tra mã kho đã tồn tại hay chưa vì code là định danh duy nhất của kho.
     */
    boolean existsByCode(String code);
}
