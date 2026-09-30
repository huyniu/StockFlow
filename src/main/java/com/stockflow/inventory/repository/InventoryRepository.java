package com.stockflow.inventory.repository;
import com.stockflow.inventory.domain.Inventory;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
/** Truy vấn tồn kho theo cặp sản phẩm/kho và hỗ trợ phân trang bộ lọc. */
public interface InventoryRepository extends JpaRepository<Inventory, Long> {
 /** Giữ hàng bằng một UPDATE SQL có điều kiện; CURRENT_TIMESTAMP của database tránh xung đột kiểu JPQL Timestamp/Instant. */
 @Modifying(flushAutomatically = true)
 @Query(value = "update inventories i set available_quantity = i.available_quantity - :qty, reserved_quantity = i.reserved_quantity + :qty, version = i.version + 1, updated_at = CURRENT_TIMESTAMP where i.id = :inventoryId and i.available_quantity >= :qty and :qty > 0", nativeQuery = true)
 int reserveStock(@Param("inventoryId") Long inventoryId, @Param("qty") int qty);
 /** Nhả hàng đang giữ, không cho phép giảm reserved xuống âm. */
 @Modifying(flushAutomatically = true)
 @Query(value = "update inventories i set available_quantity = i.available_quantity + :qty, reserved_quantity = i.reserved_quantity - :qty, version = i.version + 1, updated_at = CURRENT_TIMESTAMP where i.id = :inventoryId and i.reserved_quantity >= :qty and :qty > 0", nativeQuery = true)
 int releaseStock(@Param("inventoryId") Long inventoryId, @Param("qty") int qty);
 /** Xuất hẳn hàng đã giữ sau khi thanh toán mô phỏng thành công. */
 @Modifying(flushAutomatically = true)
 @Query(value = "update inventories i set reserved_quantity = i.reserved_quantity - :qty, version = i.version + 1, updated_at = CURRENT_TIMESTAMP where i.id = :inventoryId and i.reserved_quantity >= :qty and :qty > 0", nativeQuery = true)
 int dispatchStock(@Param("inventoryId") Long inventoryId, @Param("qty") int qty);
 /** Hoàn hàng đã xuất, kiểm tra giới hạn số nguyên cho tổng tồn vật lý. */
 @Modifying(flushAutomatically = true)
 @Query(value = "update inventories i set available_quantity = i.available_quantity + :qty, version = i.version + 1, updated_at = CURRENT_TIMESTAMP where i.id = :inventoryId and :qty > 0 and i.available_quantity <= 2147483647 - :qty - i.reserved_quantity", nativeQuery = true)
 int restock(@Param("inventoryId") Long inventoryId, @Param("qty") int qty);
 /** Tìm duy nhất một dòng tồn kho cho sản phẩm tại kho cụ thể. */
 Optional<Inventory> findByProductIdAndWarehouseId(Long productId, Long warehouseId);
 /** Nạp tên sản phẩm và kho cùng truy vấn để tránh phát sinh truy vấn riêng cho mỗi dòng. */
 @EntityGraph(attributePaths = {"product", "warehouse"})
 @Query("select i from Inventory i where (:productId is null or i.product.id = :productId) and (:warehouseId is null or i.warehouse.id = :warehouseId)")
 Page<Inventory> search(@Param("productId") Long productId, @Param("warehouseId") Long warehouseId, Pageable pageable);
}
