package com.stockflow.catalog.repository;

import com.stockflow.inventory.domain.Inventory;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Chỉ đọc tình trạng còn hàng trong một truy vấn; không mở các thao tác ghi tồn kho cho khách. */
public interface ProductAvailabilityRepository extends Repository<Inventory, Long> {

    /** Model trả mọi SKU; URL SKU riêng chỉ trả SKU đó. Kho ngừng phục vụ không được công khai. */
    @Query(value = """
            SELECT product.id AS productId,
                warehouse.id AS warehouseId,
                warehouse.code AS warehouseCode,
                warehouse.name AS warehouseName,
                CASE
                    WHEN product.status = 'ACTIVE'
                        AND (
                            variant.id IS NULL
                            OR (
                                variant.enabled = TRUE
                                AND variant.archived = FALSE
                                AND version.archived = FALSE
                                AND model.status = 'ACTIVE'
                            )
                        )
                        AND COALESCE(inventory.available_quantity, 0) > 0
                    THEN TRUE
                    ELSE FALSE
                END AS inStock
            FROM products product
            LEFT JOIN product_variants variant
                ON variant.sku_product_id = product.id
            LEFT JOIN products model
                ON model.id = variant.product_id
            LEFT JOIN product_versions version
                ON version.id = variant.version_id
            CROSS JOIN warehouses warehouse
            LEFT JOIN inventories inventory
                ON inventory.product_id = product.id
                AND inventory.warehouse_id = warehouse.id
            WHERE (product.id = :productId OR variant.product_id = :productId)
                AND warehouse.status = 'ACTIVE'
            ORDER BY product.id ASC, warehouse.id ASC
            """, nativeQuery = true)
    List<AvailabilityRow> findAvailability(@Param("productId") Long productId);

    /** Projection giới hạn dữ liệu ngay tại SQL; không nạp quantity, actor, movement hoặc địa chỉ. */
    interface AvailabilityRow {
        /** ID SKU thực tế dùng trong giỏ và đơn hàng. */
        Long getProductId();

        /** ID chi nhánh phục vụ mua sắm. */
        Long getWarehouseId();

        /** Mã chi nhánh đã được công khai trong API branches. */
        String getWarehouseCode();

        /** Tên chi nhánh để khách lựa chọn nơi phục vụ. */
        String getWarehouseName();

        /** Chỉ hàng khả dụng và SKU còn bán được coi là còn hàng; hàng đã giữ không được bán lại. */
        boolean getInStock();
    }
}
