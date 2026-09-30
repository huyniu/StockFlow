package com.stockflow.warehouse.repository;

import com.stockflow.warehouse.domain.Warehouse;
import com.stockflow.warehouse.domain.WarehouseStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository truy vấn bảng warehouses, phục vụ quản lý kho hàng trong Milestone 2.
 */
public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    /** Chỉ lấy kho hoạt động theo ID ổn định để khách chọn nơi lấy hàng, không dùng ID hard-code. */
    List<Warehouse> findByStatusOrderByIdAsc(WarehouseStatus status);

    /** Tìm mã kho ổn định để seed chỉ tạo kho chưa tồn tại. */
    java.util.Optional<Warehouse> findByCode(String code);

    /**
     * Kiểm tra mã kho đã tồn tại hay chưa vì code là định danh duy nhất của kho.
     */
    boolean existsByCode(String code);
}
