package com.stockflow.warehouse.service;

import com.stockflow.warehouse.dto.StorefrontMapLocationResponse;
import java.util.HashMap;
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
}
