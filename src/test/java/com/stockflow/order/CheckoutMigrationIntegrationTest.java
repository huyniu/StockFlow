package com.stockflow.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Nâng V14 lên V15 trên database H2 riêng, đối chiếu dữ liệu cũ và ràng buộc người nhận. */
class CheckoutMigrationIntegrationTest {

    /** Migration không bịa địa chỉ cho đơn cũ hoặc thay giá, tồn, ledger, payment và shipment đã lưu. */
    @Test
    void upgradePreservesLegacyOrderAndBusinessHistory() {
        var source = source();
        var schema = schema(source, "15");
        try {
            schema(source, "14").migrate();
            var jdbc = new JdbcTemplate(source);
            seedLegacyOrder(jdbc);
            var tables = List.of("users", "roles", "categories", "products", "warehouses", "inventories",
                    "inventory_movements", "order_items", "payments", "shipments");
            var before = tables.stream().map(table -> snapshot(jdbc, table)).toList();
            var orderBefore = legacySnapshot(jdbc);
            assertThat(schema.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(legacySnapshot(jdbc)).isEqualTo(orderBefore);
            for (int i = 0; i < tables.size(); i++) {
                assertThat(snapshot(jdbc, tables.get(i))).isEqualTo(before.get(i));
            }
            assertThat(jdbc.queryForMap("""
                    SELECT recipient_name,
                           recipient_phone,
                           delivery_address,
                           delivery_note
                    FROM orders
                    WHERE id = 90001
                    """)).containsEntry("recipient_name", null).containsEntry("recipient_phone", null)
                    .containsEntry("delivery_address", null).containsEntry("delivery_note", null);
            assertThat(schema.migrate().migrationsExecuted).isZero();
        } finally {
            schema.clean();
        }
    }

    /** DB chặn bản chụp thiếu trường/trống/sai số, cho bản chụp hợp lệ và giữ trường hợp legacy toàn null. */
    @Test
    void databaseRejectsPartialOrInvalidDelivery() {
        var source = source();
        var schema = schema(source, "15");
        try {
            schema(source, "14").migrate();
            var jdbc = new JdbcTemplate(source);
            seedLegacyOrder(jdbc);
            schema.migrate();
            assertThatThrownBy(() -> jdbc.update("""
                    UPDATE orders
                    SET recipient_name = 'Người nhận'
                    WHERE id = 90001
                    """)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("""
                    UPDATE orders
                    SET recipient_name = 'Người nhận',
                        recipient_phone = 'không-hợp-lệ',
                        delivery_address = 'Hà Nội'
                    WHERE id = 90001
                    """)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("""
                    UPDATE orders
                    SET recipient_name = '   ',
                        recipient_phone = '0901234567',
                        delivery_address = 'Hà Nội'
                    WHERE id = 90001
                    """)).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(jdbc.update("""
                    UPDATE orders
                    SET recipient_name = 'Người nhận',
                        recipient_phone = '+84901234567',
                        delivery_address = '12 Phố Mới, Hà Nội'
                    WHERE id = 90001
                    """)).isEqualTo(1);
        } finally {
            schema.clean();
        }
    }

    /** Dữ liệu V14 đã giao thành công, có thanh toán, vận đơn và ledger xuất kho để kiểm tra bảo toàn. */
    private void seedLegacyOrder(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO users (id, email, password_hash, full_name, role_id)
                SELECT 90001, 'checkout-migration@example.test', 'unused', 'Khách cũ', id
                FROM roles
                WHERE name = 'CUSTOMER'
                """);
        jdbc.update("""
                INSERT INTO products (id, category_id, sku, name, unit_price, status)
                SELECT 90001, id, 'CHECKOUT-LEGACY', 'Sản phẩm cũ', 125000, 'ACTIVE'
                FROM categories
                WHERE slug = 'dien-thoai'
                """);
        jdbc.update("""
                INSERT INTO warehouses (id, code, name, address, status)
                VALUES (90001, 'CHECKOUT-LEGACY', 'Kho cũ', 'Hà Nội', 'ACTIVE')
                """);
        jdbc.update("""
                INSERT INTO inventories (id, product_id, warehouse_id, available_quantity, reserved_quantity, version)
                VALUES (90001, 90001, 90001, 3, 0, 4)
                """);
        jdbc.update("""
                INSERT INTO orders (id, order_code, customer_id, warehouse_id, status, total_amount)
                VALUES (90001, 'CHECKOUT-OLD-ORDER', 90001, 90001, 'DELIVERED', 250000)
                """);
        jdbc.update("""
                INSERT INTO order_items (order_id, product_id, quantity, unit_price, line_total)
                VALUES (90001, 90001, 2, 125000, 250000)
                """);
        jdbc.update("""
                INSERT INTO payments (order_id, status, amount, method, paid_at)
                VALUES (90001, 'PAID', 250000, 'SIMULATED_BANKING', CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO shipments (order_id, tracking_code, status, shipped_at, delivered_at)
                VALUES (90001, 'SF-TRACK-LEGACY', 'DELIVERED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO inventory_movements (
                    inventory_id, performed_by, type, quantity, balance_before, balance_after, reference_type, reference_id
                )
                VALUES (90001, 90001, 'DISPATCH', 2, 5, 3, 'ORDER', 90001)
                """);
    }

    /** Chỉ đọc các bảng cố định trong danh sách kiểm thử, giữ thứ tự để đối chiếu snapshot. */
    private List<Map<String, Object>> snapshot(JdbcTemplate jdbc, String table) {
        return jdbc.queryForList("""
                SELECT *
                FROM %s
                ORDER BY id
                """.formatted(table));
    }

    /** Chỉ đọc các cột trước V15 để không nhầm việc thêm cột với thay đổi dữ liệu nghiệp vụ. */
    private List<Map<String, Object>> legacySnapshot(JdbcTemplate jdbc) {
        return jdbc.queryForList("""
                SELECT id,
                       order_code,
                       customer_id,
                       warehouse_id,
                       status,
                       total_amount,
                       reservation_expires_at,
                       created_at,
                       updated_at
                FROM orders
                ORDER BY id
                """);
    }

    /** Database tên ngẫu nhiên chỉ tồn tại trong JVM kiểm thử; clean không chạm PostgreSQL người dùng. */
    private DriverManagerDataSource source() {
        return new DriverManagerDataSource("jdbc:h2:mem:checkout-migration-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
    }

    /** Chạy đúng mốc Flyway để kiểm chứng nâng cấp thật, không dùng Hibernate tạo schema. */
    private Flyway schema(DriverManagerDataSource source, String version) {
        return Flyway.configure().dataSource(source).target(version)
                .locations("filesystem:src/test/resources/db/migration").cleanDisabled(false).load();
    }
}
