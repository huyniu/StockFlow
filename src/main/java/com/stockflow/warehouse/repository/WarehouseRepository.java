package com.stockflow.warehouse.repository;

import com.stockflow.warehouse.domain.Warehouse;
import com.stockflow.warehouse.domain.WarehouseStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository truy vấn bảng warehouses, phục vụ quản lý kho hàng trong Milestone 2.
 */
public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    /** Chỉ lấy kho hoạt động theo ID ổn định để khách chọn nơi lấy hàng, không dùng ID hard-code. */
    List<Warehouse> findByStatusOrderByIdAsc(WarehouseStatus status);

    /** Lựa chọn vận hành theo phân công hiện tại; giữ cả kho ngừng bán để xử lý các đơn đã tạo. */
    @Query(value = """
            SELECT w.*
            FROM warehouses w
            WHERE :unrestricted = TRUE
                OR EXISTS (
                    SELECT 1
                    FROM warehouse_staff_assignments a
                    WHERE a.warehouse_id = w.id
                        AND a.user_id = :actorId
                )
            ORDER BY w.id ASC
            """, nativeQuery = true)
    List<Warehouse> findOperatingWarehouses(
            @Param("actorId") Long actorId,
            @Param("unrestricted") boolean unrestricted);

    /** Tìm mã kho ổn định để seed chỉ tạo kho chưa tồn tại. */
    java.util.Optional<Warehouse> findByCode(String code);

    /**
     * Kiểm tra mã kho đã tồn tại hay chưa vì code là định danh duy nhất của kho.
     */
    boolean existsByCode(String code);
}
