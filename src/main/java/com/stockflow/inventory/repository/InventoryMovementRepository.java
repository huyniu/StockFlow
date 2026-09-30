package com.stockflow.inventory.repository;
import com.stockflow.inventory.domain.InventoryMovement;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
/** Repository chỉ cung cấp thêm mới và đọc sổ cái, không cung cấp API xóa hoặc sửa. */
public interface InventoryMovementRepository extends Repository<InventoryMovement, Long> {
 /** Lưu biến động mới; service luôn truyền entity chưa có mã định danh. */
 InventoryMovement save(InventoryMovement movement);
 /** Tra cứu lịch sử theo dòng tồn kho hoặc toàn bộ sổ cái có phân trang. */
 @Query("select m from InventoryMovement m where (:inventoryId is null or m.inventoryId = :inventoryId)")
 Page<InventoryMovement> search(@Param("inventoryId") Long inventoryId, Pageable pageable);
}
