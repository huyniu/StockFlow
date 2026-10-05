package com.stockflow.catalog.repository;

import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Xếp hạng model từ số lượng bán thật; API công khai không nhận doanh thu hoặc thông tin đơn/khách. */
@Repository
public class StorefrontDiscoveryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** Nhận JDBC template để tổng hợp tại database trước khi giới hạn số thẻ hiển thị. */
    public StorefrontDiscoveryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Gộp SKU về model, loại đơn chưa trả tiền/đã hoàn và model không còn cấu hình bán được. */
    public List<Long> bestsellerIds(int limit) {
        return jdbc.query("""
                WITH model_sales AS (
                    SELECT COALESCE(variant.product_id, item.product_id) AS model_id,
                           SUM(CAST(item.quantity AS BIGINT)) AS sold_quantity
                    FROM order_items item
                    JOIN orders customer_order ON customer_order.id = item.order_id
                    JOIN payments payment ON payment.order_id = customer_order.id
                    LEFT JOIN product_variants variant ON variant.sku_product_id = item.product_id
                    WHERE customer_order.status IN ('CONFIRMED', 'PACKED', 'SHIPPED', 'DELIVERED')
                      AND payment.status = 'PAID'
                    GROUP BY COALESCE(variant.product_id, item.product_id)
                )
                SELECT product.id
                FROM model_sales sales
                JOIN products product ON product.id = sales.model_id
                WHERE product.status = 'ACTIVE'
                  AND (
                    NOT EXISTS (
                        SELECT 1
                        FROM product_variants color
                        WHERE color.product_id = product.id
                    )
                    OR EXISTS (
                        SELECT 1
                        FROM product_variants color
                        JOIN product_versions version ON version.id = color.version_id
                        JOIN products sku ON sku.id = color.sku_product_id
                        WHERE color.product_id = product.id
                          AND color.enabled = TRUE
                          AND color.archived = FALSE
                          AND version.archived = FALSE
                          AND sku.status = 'ACTIVE'
                    )
                  )
                ORDER BY sales.sold_quantity DESC,
                         product.id ASC
                LIMIT :limit
                """, new MapSqlParameterSource("limit", limit), (row, index) -> row.getLong("id"));
    }
}
