package com.stockflow.warehouse.service;

import com.stockflow.common.exception.ResourceNotFoundException;
import com.stockflow.warehouse.dto.StorefrontMapLocationResponse;
import com.stockflow.warehouse.dto.UpdateStoreLocationRequest;
import com.stockflow.warehouse.dto.WarehouseDirectoryResponse;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StorefrontMapService {
    private final JdbcTemplate jdbc;

    public StorefrontMapService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<StorefrontMapLocationResponse> publicLocations() {
        return jdbc.query("""
                SELECT l.id, l.code, l.name, l.address, l.latitude, l.longitude, l.demo, w.id AS warehouse_id
                FROM storefront_map_locations l
                LEFT JOIN warehouses w ON w.code = l.warehouse_code AND w.status = 'ACTIVE'
                WHERE l.visible = TRUE ORDER BY l.id
                """, (row, index) -> new StorefrontMapLocationResponse(
                row.getLong("id"), row.getString("code"), row.getString("name"),
                row.getString("address"), row.getDouble("latitude"),
                row.getDouble("longitude"), row.getBoolean("demo"),
                row.getObject("warehouse_id", Long.class)));
    }

    /** Tên chi nhánh khách nhìn thấy (theo điểm bản đồ đang hiển thị), khóa theo ID kho ACTIVE đã liên kết. */
    @Transactional(readOnly = true)
    public Map<Long, String> publicNamesByWarehouse() {
        Map<Long, String> names = new HashMap<>();
        for (StorefrontMapLocationResponse location : publicLocations()) {
            if (location.warehouseId() != null) names.putIfAbsent(location.warehouseId(), location.name());
        }
        return names;
    }

    /**
     * Mọi kho kèm địa chỉ kho nội bộ và cửa hàng đã liên kết (điểm có ID nhỏ nhất nếu trùng mã kho).
     * Chỉ dành cho role vận hành; storefront tiếp tục dùng publicLocations().
     */
    @Transactional(readOnly = true)
    public List<WarehouseDirectoryResponse> directory() {
        Map<Long, WarehouseDirectoryResponse> rows = new LinkedHashMap<>();
        jdbc.query("""
                SELECT w.id, w.code, w.name, w.address, w.status, l.name AS store_name, l.address AS store_address
                FROM warehouses w
                LEFT JOIN storefront_map_locations l ON l.warehouse_code = w.code
                ORDER BY w.id, l.id
                """, row -> {
            rows.putIfAbsent(row.getLong("id"), new WarehouseDirectoryResponse(
                    row.getLong("id"), row.getString("code"), row.getString("name"),
                    row.getString("address"), row.getString("status"),
                    row.getString("store_name"), row.getString("store_address")));
        });
        return List.copyOf(rows.values());
    }

    /** ADMIN đổi tên/địa chỉ cửa hàng công khai của kho; tọa độ bản đồ và trạng thái hiển thị giữ nguyên. */
    @Transactional
    public void updateStoreLocation(Long warehouseId, UpdateStoreLocationRequest request) {
        int updated = jdbc.update("""
                UPDATE storefront_map_locations SET name = ?, address = ?
                WHERE id = (
                    SELECT MIN(l.id) FROM storefront_map_locations l
                    JOIN warehouses w ON w.code = l.warehouse_code
                    WHERE w.id = ?)
                """, request.name().trim(), request.address().trim(), warehouseId);
        if (updated == 0) {
            throw new ResourceNotFoundException("Kho này chưa có cửa hàng trên bản đồ.");
        }
    }
}
