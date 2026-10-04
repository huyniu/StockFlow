package com.stockflow.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Migration V11 chỉ bổ sung bảng: giữ nguyên SKU, giá, ảnh, tồn kho và lịch sử đã được ghi. */
class ProductOptionsMigrationIntegrationTest {

    /** Cài mới không có thông số/màu suy đoán hoặc hàng mẫu ngoài yêu cầu của người dùng. */
    @Test
    void freshSchemaContainsEmptySpecificationAndVariantTables() {
        var source = source();
        var schema = schema(source, "11");
        try {
            schema.migrate();
            var jdbc = new JdbcTemplate(source);
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM product_specifications
                    """, Long.class)).isZero();
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM product_variants
                    """, Long.class)).isZero();
            assertThat(schema.migrate().migrationsExecuted).isZero();
        } finally {
            schema.clean();
        }
    }

    /** Nâng V10→V11 giữ nguyên cả liên kết nghiệp vụ và giá/ảnh do người dùng đã nhập. */
    @Test
    void upgradePreservesProductsInventoriesAndAuditLedger() {
        var source = source();
        var schema = schema(source, "11");
        try {
            schema(source, "10").migrate();
            var jdbc = new JdbcTemplate(source);
            seed(jdbc);
            var products = jdbc.queryForList("""
                    SELECT *
                    FROM products
                    ORDER BY id
                    """);
            var stock = jdbc.queryForList("""
                    SELECT *
                    FROM inventories
                    ORDER BY id
                    """);
            var ledger = jdbc.queryForList("""
                    SELECT *
                    FROM inventory_movements
                    ORDER BY id
                    """);
            var images = jdbc.queryForList("""
                    SELECT *
                    FROM product_images
                    ORDER BY product_id, position
                    """);
            schema.migrate();
            assertThat(jdbc.queryForList("""
                    SELECT *
                    FROM products
                    ORDER BY id
                    """)).isEqualTo(products);
            assertThat(jdbc.queryForList("""
                    SELECT *
                    FROM inventories
                    ORDER BY id
                    """)).isEqualTo(stock);
            assertThat(jdbc.queryForList("""
                    SELECT *
                    FROM inventory_movements
                    ORDER BY id
                    """)).isEqualTo(ledger);
            assertThat(jdbc.queryForList("""
                    SELECT *
                    FROM product_images
                    ORDER BY product_id, position
                    """)).isEqualTo(images);
        } finally {
            schema.clean();
        }
    }

    /** UNIQUE và CHECK tại database bảo vệ cả ghi SQL ngoài ứng dụng. */
    @Test
    void databaseRejectsDuplicateColorAndInvalidSpecification() {
        var source = source();
        var schema = schema(source, "11");
        try {
            schema.migrate();
            var jdbc = new JdbcTemplate(source);
            seed(jdbc);
            jdbc.update("""
                    INSERT INTO product_variants (product_id, sku_product_id, color_name, color_key)
                    VALUES (100, 100, 'Cam', 'cam')
                    """);
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO product_variants (product_id, sku_product_id, color_name, color_key)
                    VALUES (100, 101, 'CAM', 'cam')
                    """)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO product_specifications (
                        product_id, position, specification_name, specification_value
                    )
                    VALUES (100, 60, 'RAM', '8 GB')
                    """)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO product_specifications (
                        product_id, position, specification_name, specification_value
                    )
                    VALUES (100, 0, '   ', '8 GB')
                    """)).isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            schema.clean();
        }
    }

    /** Fixture độc lập có SKU cũ, ảnh và ledger hợp lệ; không mô phỏng schema bằng Hibernate. */
    private void seed(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO products (id, category_id, sku, name, unit_price, status, image_url, description)
                SELECT 100, id, 'EXISTING-ORANGE', 'Thiết bị cũ', 25000000, 'ACTIVE',
                       '/assets/orange.jpg', 'Nội dung đã nhập'
                FROM categories
                WHERE slug = 'dien-thoai'
                """);
        jdbc.update("""
                INSERT INTO products (id, category_id, sku, name, unit_price, status)
                SELECT 101, category_id, 'EXISTING-BLUE', 'Thiết bị khác', 26000000, 'ACTIVE'
                FROM products
                WHERE id = 100
                """);
        jdbc.update("""
                INSERT INTO product_images (product_id, position, image_url)
                VALUES (100, 0, '/assets/orange-side.jpg')
                """);
        jdbc.update("""
                INSERT INTO users (id, email, password_hash, full_name, role_id)
                SELECT 100, 'color-upgrade@example.com', 'unused', 'Người nhập hàng cũ', id
                FROM roles
                WHERE name = 'ADMIN'
                """);
        jdbc.update("""
                INSERT INTO warehouses (id, code, name, address, status)
                VALUES (100, 'COLOR-UPGRADE', 'Kho cũ', 'Hà Nội', 'ACTIVE')
                """);
        jdbc.update("""
                INSERT INTO inventories (id, product_id, warehouse_id, available_quantity, reserved_quantity, version)
                VALUES (100, 100, 100, 9, 0, 3)
                """);
        jdbc.update("""
                INSERT INTO inventory_movements (
                    inventory_id, performed_by, type, quantity, balance_before, balance_after
                )
                VALUES (100, 100, 'GOODS_RECEIPT', 9, 0, 9)
                """);
    }

    /** Database H2 riêng cho từng ca; clean chỉ chạm database test có tên ngẫu nhiên này. */
    private DriverManagerDataSource source() {
        return new DriverManagerDataSource("jdbc:h2:mem:product-options-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
    }

    /** Ghim checkpoint để kiểm tra chính xác nâng cấp, không sửa migration đã áp dụng. */
    private Flyway schema(DriverManagerDataSource source, String version) {
        return Flyway.configure().dataSource(source).target(version)
                .locations("filesystem:src/test/resources/db/migration").cleanDisabled(false).load();
    }
}
