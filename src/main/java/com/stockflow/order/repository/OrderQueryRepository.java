package com.stockflow.order.repository;

import com.stockflow.order.domain.OrderStatus;
import com.stockflow.order.dto.OrderListResponse;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Truy vấn danh sách đơn trực tiếp tại database, áp dụng phạm vi kho trước cả COUNT và LIMIT.
 * Chỉ JOIN kho, tránh nhân bản đơn hoặc tải collection order_items trong truy vấn phân trang.
 */
@Repository
public class OrderQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** Nhận JDBC template để bind tham số, không ghép dữ liệu người dùng vào SQL. */
    public OrderQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * staffId khác null bắt buộc lọc mọi đơn theo phân công kho hiện tại của nhân viên.
     * Thứ tự created_at rồi id giảm dần giữ phân trang ổn định khi thời điểm tạo trùng nhau.
     */
    public Page<OrderListResponse> findOrders(
            OrderStatus status, Long warehouseId, Long staffId, Pageable pageable) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        StringBuilder filter = new StringBuilder("""
                FROM orders o
                JOIN warehouses w ON w.id = o.warehouse_id
                WHERE 1 = 1
                """);
        if (status != null) {
            filter.append("""
                    AND o.status = :status
                    """);
            parameters.addValue("status", status.name());
        }
        if (warehouseId != null) {
            filter.append("""
                    AND o.warehouse_id = :warehouseId
                    """);
            parameters.addValue("warehouseId", warehouseId);
        }
        if (staffId != null) {
            // EXISTS bảo vệ cả trang dữ liệu lẫn tổng số đơn; nhiều phân công không nhân bản kết quả.
            filter.append("""
                    AND EXISTS (
                        SELECT 1
                        FROM warehouse_staff_assignments a
                        WHERE a.warehouse_id = o.warehouse_id
                          AND a.user_id = :staffId
                    )
                    """);
            parameters.addValue("staffId", staffId);
        }

        String countSql = """
                SELECT COUNT(*)
                %s
                """.formatted(filter);
        long total = jdbc.queryForObject(countSql, parameters, Long.class);
        String dataSql = """
                SELECT o.id,
                       o.order_code,
                       o.customer_id,
                       o.warehouse_id,
                       w.name AS warehouse_name,
                       o.status,
                       o.total_amount,
                       o.reservation_expires_at,
                       o.created_at,
                       o.updated_at
                %s
                ORDER BY o.created_at DESC,
                         o.id DESC
                LIMIT :size
                OFFSET :offset
                """.formatted(filter);
        parameters.addValue("size", pageable.getPageSize());
        parameters.addValue("offset", pageable.getOffset());
        List<OrderListResponse> content = jdbc.query(dataSql, parameters, (row, index) -> {
            Timestamp expiresAt = row.getTimestamp("reservation_expires_at");
            return new OrderListResponse(
                    row.getLong("id"),
                    row.getString("order_code"),
                    row.getLong("customer_id"),
                    row.getLong("warehouse_id"),
                    row.getString("warehouse_name"),
                    OrderStatus.valueOf(row.getString("status")),
                    row.getBigDecimal("total_amount"),
                    expiresAt == null ? null : expiresAt.toInstant(),
                    row.getTimestamp("created_at").toInstant(),
                    row.getTimestamp("updated_at").toInstant());
        });
        return new PageImpl<>(content, pageable, total);
    }

    /** Kiểm tra kho lọc tường minh để trả 403 khi nhân viên cố truy cập ngoài phạm vi được giao. */
    public boolean isAssigned(Long staffId, Long warehouseId) {
        String sql = """
                SELECT COUNT(*)
                FROM warehouse_staff_assignments
                WHERE user_id = :staffId
                  AND warehouse_id = :warehouseId
                """;
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("staffId", staffId)
                .addValue("warehouseId", warehouseId);
        return jdbc.queryForObject(sql, parameters, Long.class) > 0;
    }
}
