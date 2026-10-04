package com.stockflow.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** V12 nâng nhóm màu cũ thành phiên bản trung tính, bảo toàn liên kết và ràng buộc database. */
class ProductVersionsMigrationIntegrationTest {

    /** Schema mới không tự tạo model/phiên bản/màu giả khi chưa có dữ liệu do ADMIN nhập. */
    @Test
    void freshInstallContainsEmptyVersionTables() {
        var source = source();
        var schema = schema(source, "12");
        try {
            schema.migrate();
            var jdbc = new JdbcTemplate(source);
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM product_versions
                    """, Long.class)).isZero();
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM product_version_specifications
                    """, Long.class)).isZero();
            assertThat(schema.migrate().migrationsExecuted).isZero();
        } finally {
            schema.clean();
        }
    }

    /** Nâng cấp chỉ thêm version_id, giữ cả ID mapping màu, SKU, stock, đơn và ledger đã ghi. */
    @Test
    void upgradePreservesExistingSkuGroupsAndBusinessHistory() {
        var source = source();
        var schema = schema(source, "12");
        try {
            schema(source, "11").migrate();
            var jdbc = new JdbcTemplate(source);
            seed(jdbc);
            var tables = List.of("products", "inventories", "inventory_movements", "orders", "order_items",
                    "product_specifications", "product_images");
            var snapshots = tables.stream().map(table -> snapshot(jdbc, table)).toList();
            var colors = jdbc.queryForList("""
                    SELECT id, product_id, sku_product_id, color_name, color_key, color_hex, enabled
                    FROM product_variants
                    ORDER BY id
                    """);
            schema.migrate();
            for (int i = 0; i < tables.size(); i++) {
                assertThat(snapshot(jdbc, tables.get(i))).isEqualTo(snapshots.get(i));
            }
            assertThat(jdbc.queryForList("""
                    SELECT id, product_id, sku_product_id, color_name, color_key, color_hex, enabled
                    FROM product_variants
                    ORDER BY id
                    """)).isEqualTo(colors);
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(DISTINCT version_id)
                    FROM product_variants
                    """, Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("""
                    SELECT name
                    FROM product_versions
                    WHERE product_id = 100
                    """, String.class)).isEqualTo("Phiên bản hiện tại");
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM product_versions
                    WHERE product_id = 102
                    """, Long.class)).isZero();
        } finally {
            schema.clean();
        }
    }

    /** DB cho cùng màu ở phiên bản khác, chặn trùng cùng phiên bản và chặn phiên bản sai model. */
    @Test
    void databaseEnforcesVersionColorUniquenessAndCompositeParentForeignKey() {
        var source = source();
        var schema = schema(source, "12");
        try {
            schema(source, "11").migrate();
            var jdbc = new JdbcTemplate(source);
            seed(jdbc);
            schema.migrate();
            jdbc.update("""
                    INSERT INTO product_versions (id, product_id, name, name_key)
                    VALUES (200, 100, '44mm GPS', '44mm gps'), (201, 103, '40mm GPS', '40mm gps')
                    """);
            jdbc.update("""
                    INSERT INTO product_variants (product_id, sku_product_id, version_id, color_name, color_key)
                    VALUES (100, 102, 200, 'Ánh sao', 'ánh sao')
                    """);
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO product_variants (product_id, sku_product_id, version_id, color_name, color_key)
                    VALUES (100, 103, 200, 'ÁNH SAO', 'ánh sao')
                    """)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO product_variants (product_id, sku_product_id, version_id, color_name, color_key)
                    VALUES (100, 103, 201, 'Đen', 'đen')
                    """)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO product_versions (product_id, name, name_key)
                    VALUES (100, '44MM GPS', '44mm gps')
                    """)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO product_version_specifications (
                        version_id, position, specification_name, specification_value
                    )
                    VALUES (200, 60, 'Kích thước', '44mm')
                    """)).isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            schema.clean();
        }
    }

    /** Seed schema V11 thật với nhóm màu, dữ liệu kỹ thuật và đơn đang giữ hàng. */
    private void seed(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO products (id, category_id, sku, name, unit_price, status, image_url)
                SELECT 100, id, 'V12-STAR', 'Đồng hồ đã nhập', 7000000, 'ACTIVE', '/assets/star.jpg'
                FROM categories
                WHERE slug = 'dien-thoai'
                """);
        jdbc.update("""
                INSERT INTO products (id, category_id, sku, name, unit_price, status)
                SELECT 101, category_id, 'V12-BLACK', 'Đồng hồ màu khác', 8000000, 'ACTIVE'
                FROM products
                WHERE id = 100
                """);
        jdbc.update("""
                INSERT INTO products (id, category_id, sku, name, unit_price, status)
                SELECT 102, category_id, 'V12-BARE', 'Sản phẩm chưa cấu hình', 9000000, 'ACTIVE'
                FROM products
                WHERE id = 100
                """);
        jdbc.update("""
                INSERT INTO products (id, category_id, sku, name, unit_price, status)
                SELECT 103, category_id, 'V12-OTHER', 'Model khác', 9000000, 'ACTIVE'
                FROM products
                WHERE id = 100
                """);
        jdbc.update("""
                INSERT INTO product_variants (product_id, sku_product_id, color_name, color_key, enabled)
                VALUES (100, 100, 'Ánh sao', 'ánh sao', TRUE), (100, 101, 'Đen', 'đen', FALSE)
                """);
        jdbc.update("""
                INSERT INTO product_specifications (product_id, position, specification_name, specification_value)
                VALUES (100, 0, 'Kích thước', '40mm')
                """);
        jdbc.update("""
                INSERT INTO product_images (product_id, position, image_url)
                VALUES (100, 0, '/assets/star-side.jpg')
                """);
        jdbc.update("""
                INSERT INTO users (id, email, password_hash, full_name, role_id)
                SELECT 100, 'version-migration@example.com', 'unused', 'Khách cũ', id
                FROM roles
                WHERE name = 'CUSTOMER'
                """);
        jdbc.update("""
                INSERT INTO warehouses (id, code, name, address, status)
                VALUES (100, 'V12-UPGRADE', 'Kho cũ', 'Hà Nội', 'ACTIVE')
                """);
        jdbc.update("""
                INSERT INTO inventories (id, product_id, warehouse_id, available_quantity, reserved_quantity, version)
                VALUES (100, 100, 100, 5, 2, 4)
                """);
        jdbc.update("""
                INSERT INTO orders (id, order_code, customer_id, warehouse_id, status, total_amount)
                VALUES (100, 'V12-OLD-ORDER', 100, 100, 'PENDING', 14000000)
                """);
        jdbc.update("""
                INSERT INTO order_items (order_id, product_id, quantity, unit_price, line_total)
                VALUES (100, 100, 2, 7000000, 14000000)
                """);
        jdbc.update("""
                INSERT INTO inventory_movements (
                    inventory_id, performed_by, type, quantity, balance_before, balance_after, reference_type, reference_id
                )
                VALUES (100, 100, 'GOODS_RECEIPT', 7, 0, 7, NULL, NULL),
                       (100, 100, 'RESERVATION_HOLD', 2, 7, 7, 'ORDER', 100)
                """);
    }

    /** Database test độc lập có tên ngẫu nhiên; clean không chạm PostgreSQL của người dùng. */
    private DriverManagerDataSource source() {
        return new DriverManagerDataSource("jdbc:h2:mem:product-versions-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
    }

    /** Chỉ nhận tên bảng cố định phía trên, đọc snapshot có thứ tự để đối chiếu trước/sau nâng cấp. */
    private List<java.util.Map<String, Object>> snapshot(JdbcTemplate jdbc, String table) {
        return jdbc.queryForList("""
                SELECT *
                FROM %s
                ORDER BY 1, 2
                """.formatted(table));
    }

    /** Ghim mốc V11/V12 để kiểm tra nâng cấp thực tế qua Flyway. */
    private Flyway schema(DriverManagerDataSource source, String version) {
        return Flyway.configure().dataSource(source).target(version)
                .locations("filesystem:src/test/resources/db/migration").cleanDisabled(false).load();
    }
}
