package com.stockflow.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Nâng cấp H2 riêng từ V8 lên V9 để kiểm chứng chuyển dữ liệu thật, không chỉ schema của database mới. */
class BrandMigrationIntegrationTest {

    /** Danh mục iphone được đổi hoặc gộp; danh mục ghép đang có hàng được giữ để không đoán sai loại hàng. */
    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void upgradePreservesProductsStockLedgerAndOrders(boolean existingPhone, boolean combinedHasProducts) {
        DriverManagerDataSource datasource = datasource();
        Flyway oldSchema = flyway(datasource, "8");
        oldSchema.migrate();
        JdbcTemplate jdbc = new JdbcTemplate(datasource);
        try {
            seedV8(jdbc, existingPhone, combinedHasProducts);
            List<Map<String, Object>> beforeStock = snapshot(jdbc, "inventories");
            List<Map<String, Object>> beforeLedger = snapshot(jdbc, "inventory_movements");
            List<Map<String, Object>> beforeItems = snapshot(jdbc, "order_items");
            List<Map<String, Object>> beforeOrders = snapshot(jdbc, "orders");
            List<Map<String, Object>> beforeImages = jdbc.queryForList("""
                    SELECT product_id, position, image_url
                    FROM product_images
                    ORDER BY product_id, position
                    """);

            flyway(datasource, null).migrate();

            long expectedPhoneId = existingPhone ? 20 : 6;
            assertThat(jdbc.queryForObject("""
                    SELECT id
                    FROM categories
                    WHERE slug = 'dien-thoai'
                    """, Long.class)).isEqualTo(expectedPhoneId);
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM categories
                    WHERE slug = 'iphone'
                    """, Long.class)).isZero();
            assertThat(jdbc.queryForObject("""
                    SELECT category_id
                    FROM products
                    WHERE id = 101
                    """, Long.class)).isEqualTo(expectedPhoneId);
            assertThat(jdbc.queryForObject("""
                    SELECT brands.slug
                    FROM products
                    JOIN brands ON brands.id = products.brand_id
                    WHERE products.id = 101
                    """, String.class)).isEqualTo("apple");
            assertThat(jdbc.queryForObject("""
                    SELECT unit_price
                    FROM products
                    WHERE id = 101
                    """, java.math.BigDecimal.class)).isEqualByComparingTo("31990000.00");
            assertThat(jdbc.queryForObject("""
                    SELECT description
                    FROM products
                    WHERE id = 101
                    """, String.class)).isEqualTo("Mô tả iPhone đã nhập");
            assertThat(jdbc.queryForObject("""
                    SELECT brand_id
                    FROM products
                    WHERE id = 102
                    """, Long.class)).isNull();
            assertThat(jdbc.queryForObject("""
                    SELECT slug
                    FROM categories
                    WHERE id = 8
                    """, String.class)).isEqualTo(combinedHasProducts ? "dien-thoai-tablet" : "may-tinh-bang");
            assertThat(snapshot(jdbc, "inventories")).isEqualTo(beforeStock);
            assertThat(snapshot(jdbc, "inventory_movements")).isEqualTo(beforeLedger);
            assertThat(snapshot(jdbc, "orders")).isEqualTo(beforeOrders);
            assertThat(snapshot(jdbc, "order_items")).isEqualTo(beforeItems);
            assertThat(jdbc.queryForList("""
                    SELECT product_id, position, image_url
                    FROM product_images
                    ORDER BY product_id, position
                    """)).isEqualTo(beforeImages);
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM brand_categories
                    WHERE category_id = ?
                    """, Long.class, expectedPhoneId)).isEqualTo(16);
            assertThat(flyway(datasource, null).migrate().migrationsExecuted).isZero();
        } finally {
            flyway(datasource, null).clean();
        }
    }

    /** Database trống có 16 hãng/Điện thoại nhưng không tự sinh sản phẩm, kho hoặc movement mẫu. */
    @Test
    void freshSchemaProvidesReferencesWithoutFakeStockOrProducts() {
        DriverManagerDataSource datasource = datasource();
        Flyway schema = flyway(datasource, null);
        try {
            schema.migrate();
            JdbcTemplate jdbc = new JdbcTemplate(datasource);
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM brands
                    """, Long.class)).isEqualTo(16);
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM categories
                    WHERE slug = 'dien-thoai'
                    """, Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM products
                    """, Long.class)).isZero();
            assertThat(snapshot(jdbc, "inventories")).isEmpty();
            assertThat(snapshot(jdbc, "inventory_movements")).isEmpty();
        } finally {
            schema.clean();
        }
    }

    /** Fixture có đủ khóa ngoại và ledger nhập kho, giữ ID để phát hiện xóa/tạo lại hàng khi nâng cấp. */
    private void seedV8(JdbcTemplate jdbc, boolean existingPhone, boolean combinedHasProducts) {
        jdbc.update("""
                INSERT INTO categories (id, name, slug)
                VALUES
                    (6, 'iphone', 'iphone'),
                    (7, 'Laptop', 'laptop'),
                    (8, 'Điện thoại, Tablet', 'dien-thoai-tablet')
                """);
        if (existingPhone) {
            jdbc.update("""
                    INSERT INTO categories (id, name, slug)
                    VALUES (20, 'Điện thoại', 'dien-thoai')
                    """);
        }
        jdbc.update("""
                INSERT INTO products (id, category_id, sku, name, unit_price, status, image_url, description)
                VALUES
                    (101, 6, 'IP17-MIGRATION', 'iPhone đã nhập', 31990000, 'ACTIVE', '/assets/iphone.jpg', 'Mô tả iPhone đã nhập'),
                    (102, 7, 'HP-MIGRATION', 'Laptop đã nhập', 25990000, 'ACTIVE', '/assets/laptop.jpg', NULL)
                """);
        if (combinedHasProducts) {
            jdbc.update("""
                    INSERT INTO products (id, category_id, sku, name, unit_price, status)
                    VALUES (103, 8, 'TABLET-MIGRATION', 'Thiết bị chưa phân loại lại', 1000000, 'ACTIVE')
                    """);
        }
        jdbc.update("""
                INSERT INTO product_images (product_id, position, image_url)
                VALUES (101, 0, '/assets/iphone-back.jpg')
                """);
        jdbc.update("""
                INSERT INTO users (id, email, password_hash, full_name, role_id)
                SELECT 100, 'migration@example.com', 'unused-hash', 'Người dùng cũ', id
                FROM roles
                WHERE name = 'ADMIN'
                """);
        jdbc.update("""
                INSERT INTO warehouses (id, code, name, address, status)
                VALUES (1, 'MIGRATION-WH', 'Kho Hà Nội cũ', 'Hà Nội', 'ACTIVE')
                """);
        jdbc.update("""
                INSERT INTO inventories (id, product_id, warehouse_id, available_quantity, reserved_quantity, version)
                VALUES (1, 101, 1, 9, 0, 3)
                """);
        jdbc.update("""
                INSERT INTO inventory_movements (
                    id, inventory_id, performed_by, type, quantity,
                    balance_before, balance_after, reference_type, note
                )
                VALUES (1, 1, 100, 'GOODS_RECEIPT', 9, 0, 9, 'MANUAL_ADJUSTMENT', 'Sổ cái cũ bất biến')
                """);
        jdbc.update("""
                INSERT INTO orders (id, order_code, customer_id, warehouse_id, status, total_amount)
                VALUES (1, 'SF-MIGRATION', 100, 1, 'CANCELLED', 31990000)
                """);
        jdbc.update("""
                INSERT INTO order_items (id, order_id, product_id, quantity, unit_price, line_total)
                VALUES (1, 1, 101, 1, 31990000, 31990000)
                """);
    }

    /** Mỗi ca dùng database H2 in-memory riêng; việc clean chỉ áp dụng cho fixture trong test. */
    private DriverManagerDataSource datasource() {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:brands-" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
    }

    /** Cô lập bước V8→V9; migration V10 có bộ kiểm chứng nâng cấp riêng. */
    private Flyway flyway(DriverManagerDataSource datasource, String target) {
        var configuration = Flyway.configure()
                .dataSource(datasource)
                .locations("filesystem:src/test/resources/db/migration")
                .cleanDisabled(false);
        configuration.target(target == null ? "9" : target);
        return configuration.load();
    }

    /** Chỉ cho phép tên bảng cố định của fixture, không nối input bên ngoài vào SQL. */
    private List<Map<String, Object>> snapshot(JdbcTemplate jdbc, String table) {
        String query = switch (table) {
            case "inventories" -> """
                    SELECT *
                    FROM inventories
                    ORDER BY id
                    """;
            case "inventory_movements" -> """
                    SELECT *
                    FROM inventory_movements
                    ORDER BY id
                    """;
            case "orders" -> """
                    SELECT *
                    FROM orders
                    ORDER BY id
                    """;
            case "order_items" -> """
                    SELECT *
                    FROM order_items
                    ORDER BY id
                    """;
            default -> throw new IllegalArgumentException("Bảng snapshot không được hỗ trợ.");
        };
        return jdbc.queryForList(query);
    }
}
