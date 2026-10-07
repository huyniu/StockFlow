package com.stockflow.report.repository;

import com.stockflow.order.domain.OrderStatus;
import com.stockflow.report.dto.*;
import java.sql.Types;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

/**
 * Truy vấn báo cáo trực tiếp bằng SQL tổng hợp để không nạp từng entity vào bộ nhớ.
 * Mệnh đề lọc tùy chọn chỉ được thêm khi có tham số, giữ điều kiện ngày có khả năng dùng index.
 */
@Repository
public class ReportRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** Nhận JDBC template để bind tham số an toàn và ánh xạ kết quả tổng hợp thành DTO. */
    public ReportRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Nhóm đơn theo ngày hoặc tháng UTC và kho; COUNT phân trang đếm số nhóm, không đếm số đơn. */
    public Page<RevenueReportResponse> revenue(
            LocalDate fromDate, LocalDate toDate, Long warehouseId, ReportPeriod period, Pageable pageable) {
        Filter filter = orderFilter(fromDate, toDate, warehouseId);
        // Chỉ enum nội bộ được dùng để định dạng đơn vị DATE_TRUNC, không ghép giá trị tùy ý của client vào SQL.
        String unit = period == ReportPeriod.MONTH ? "month" : "day";
        String grouped = """
                SELECT CAST(DATE_TRUNC('%s', o.created_at AT TIME ZONE 'UTC') AS DATE) AS period,
                       o.warehouse_id,
                       w.name AS warehouse_name,
                       COUNT(*) AS total_orders,
                       SUM(o.total_amount) AS total_revenue
                FROM orders o
                JOIN warehouses w ON w.id = o.warehouse_id
                %s
                GROUP BY period,
                         o.warehouse_id,
                         w.name
                """.formatted(unit, filter.sql());
        String countSql = """
                SELECT COUNT(*)
                FROM (
                    %s
                ) report_groups
                """.formatted(grouped);
        String dataSql = grouped + """
                ORDER BY period ASC,
                         o.warehouse_id ASC
                LIMIT :size
                OFFSET :offset
                """;
        long total = jdbc.queryForObject(countSql, filter.parameters(), Long.class);
        List<RevenueReportResponse> content = jdbc.query(dataSql, pageParameters(filter.parameters(), pageable),
                (row, index) -> new RevenueReportResponse(
                        row.getDate("period").toLocalDate(),
                        row.getLong("warehouse_id"),
                        row.getString("warehouse_name"),
                        row.getLong("total_orders"),
                        row.getBigDecimal("total_revenue")));
        return new PageImpl<>(content, pageable, total);
    }

    /** Xếp hạng theo doanh thu giảm dần, sau đó số lượng và productId để thứ tự trang luôn xác định. */
    public Page<TopProductReportResponse> topProducts(
            LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        Filter filter = orderFilter(fromDate, toDate, null);
        String joins = """
                FROM order_items oi
                JOIN orders o ON o.id = oi.order_id
                JOIN products p ON p.id = oi.product_id
                JOIN categories c ON c.id = p.category_id
                """;
        String countSql = """
                SELECT COUNT(DISTINCT p.id)
                %s
                %s
                """.formatted(joins, filter.sql());
        String dataSql = """
                SELECT p.id AS product_id,
                       p.sku AS product_sku,
                       p.name AS product_name,
                       c.name AS category_name,
                       SUM(CAST(oi.quantity AS BIGINT)) AS total_quantity_sold,
                       SUM(oi.line_total) AS total_revenue
                %s
                %s
                GROUP BY p.id,
                         p.sku,
                         p.name,
                         c.name
                ORDER BY total_revenue DESC,
                         total_quantity_sold DESC,
                         p.id ASC
                LIMIT :size
                OFFSET :offset
                """.formatted(joins, filter.sql());
        long total = jdbc.queryForObject(countSql, filter.parameters(), Long.class);
        List<TopProductReportResponse> content = jdbc.query(dataSql, pageParameters(filter.parameters(), pageable),
                (row, index) -> new TopProductReportResponse(
                        row.getLong("product_id"),
                        row.getString("product_sku"),
                        row.getString("product_name"),
                        row.getString("category_name"),
                        row.getLong("total_quantity_sold"),
                        row.getBigDecimal("total_revenue")));
        return new PageImpl<>(content, pageable, total);
    }

    /** Lọc theo tồn khả dụng, không dùng tồn vật lý vì hàng đã giữ chưa thể bán cho khách mới. */
    public Page<LowStockReportResponse> lowStock(int threshold, Long warehouseId, Pageable pageable) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("threshold", threshold);
        String where = """
                WHERE i.available_quantity <= :threshold
                """;
        if (warehouseId != null) {
            where += """
                    AND i.warehouse_id = :warehouseId
                    """;
            parameters.addValue("warehouseId", warehouseId);
        }
        String countSql = """
                SELECT COUNT(*)
                FROM inventories i
                %s
                """.formatted(where);
        String dataSql = """
                SELECT p.id AS product_id,
                       p.sku AS product_sku,
                       p.name AS product_name,
                       w.id AS warehouse_id,
                       w.name AS warehouse_name,
                       i.available_quantity,
                       i.reserved_quantity,
                       CAST(i.available_quantity AS BIGINT) + i.reserved_quantity AS physical_quantity
                FROM inventories i
                JOIN products p ON p.id = i.product_id
                JOIN warehouses w ON w.id = i.warehouse_id
                %s
                ORDER BY i.available_quantity ASC,
                         i.id ASC
                LIMIT :size
                OFFSET :offset
                """.formatted(where);
        long total = jdbc.queryForObject(countSql, parameters, Long.class);
        List<LowStockReportResponse> content = jdbc.query(dataSql, pageParameters(parameters, pageable),
                (row, index) -> new LowStockReportResponse(
                        row.getLong("product_id"),
                        row.getString("product_sku"),
                        row.getString("product_name"),
                        row.getLong("warehouse_id"),
                        row.getString("warehouse_name"),
                        row.getInt("available_quantity"),
                        row.getInt("reserved_quantity"),
                        row.getLong("physical_quantity")));
        return new PageImpl<>(content, pageable, total);
    }

    /** Tổng hợp mọi trạng thái bằng một GROUP BY, không loại đơn hủy hoặc hết hạn khỏi bảng tổng quan. */
    public List<OrderSummaryResponse> orderSummary() {
        String sql = """
                SELECT status,
                       COUNT(*) AS total_count,
                       SUM(total_amount) AS total_amount
                FROM orders
                GROUP BY status
                ORDER BY status ASC
                """;
        return jdbc.query(sql, new MapSqlParameterSource(),
                (row, index) -> new OrderSummaryResponse(
                        OrderStatus.valueOf(row.getString("status")),
                        row.getLong("total_count"),
                        row.getBigDecimal("total_amount")));
    }

    /** Dùng khoảng nửa mở theo UTC để bao gồm toàn bộ toDate mà không bọc cột created_at trong hàm WHERE. */
    private Filter orderFilter(LocalDate fromDate, LocalDate toDate, Long warehouseId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        String where = """
                WHERE o.status IN ('CONFIRMED', 'PACKED', 'SHIPPED', 'DELIVERED')
                AND NOT EXISTS (SELECT 1 FROM payments p WHERE p.order_id = o.id AND p.method = 'COD' AND p.status <> 'PAID')
                """;
        if (fromDate != null) {
            where += """
                    AND o.created_at >= :fromTimestamp
                    """;
            parameters.addValue("fromTimestamp", fromDate.atStartOfDay().atOffset(ZoneOffset.UTC),
                    Types.TIMESTAMP_WITH_TIMEZONE);
        }
        if (toDate != null) {
            where += """
                    AND o.created_at < :toTimestamp
                    """;
            parameters.addValue("toTimestamp", toDate.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC),
                    Types.TIMESTAMP_WITH_TIMEZONE);
        }
        if (warehouseId != null) {
            where += """
                    AND o.warehouse_id = :warehouseId
                    """;
            parameters.addValue("warehouseId", warehouseId);
        }
        return new Filter(where, parameters);
    }

    /** Bind LIMIT và OFFSET thay vì ghép tham số số trang vào SQL. */
    private MapSqlParameterSource pageParameters(MapSqlParameterSource parameters, Pageable pageable) {
        return parameters.addValue("size", pageable.getPageSize()).addValue("offset", pageable.getOffset());
    }

    /** Cặp mệnh đề lọc đã kiểm soát và tham số bind tương ứng. */
    private record Filter(String sql, MapSqlParameterSource parameters) {
    }
}
