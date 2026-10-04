package com.stockflow.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Kiểm chứng V12→V13 giữ dữ liệu nghiệp vụ, bổ sung tham chiếu không trùng và không tạo model/tồn giả. */
class CatalogCompletionMigrationIntegrationTest {

    /** Database mới có đủ loại hàng và hãng, logo tùy chọn; staging được dọn và chạy lại không thêm dòng. */
    @Test
    void freshCatalogHasAllReferencesWithoutBusinessFixtures() {
        var source = datasource();
        var schema = flyway(source, "13");
        try {
            schema.migrate();
            var jdbc = new JdbcTemplate(source);
            assertThat(count(jdbc, "categories")).isEqualTo(125);
            assertThat(count(jdbc, "brands")).isEqualTo(84);
            for (String table : List.of("products", "inventories", "inventory_movements", "orders", "order_items",
                    "payments", "shipments")) {
                assertThat(count(jdbc, table)).as(table).isZero();
            }
            assertThat(parentSlug(jdbc, "may-lanh")).isEqualTo("tivi-dien-may");
            assertThat(parentSlug(jdbc, "usb-wifi")).isEqualTo("thiet-bi-mang");
            assertThat(parentSlug(jdbc, "camera-giam-sat-trung-bay")).isEqualTo("hang-cu");
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM brands
                    WHERE logo_url IS NOT NULL
                    """, Long.class)).isZero();
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM information_schema.tables
                    WHERE table_schema = 'public'
                      AND table_name LIKE 'stockflow_catalog_v13_%'
                    """, Long.class)).isZero();
            assertThat(schema.migrate().migrationsExecuted).isZero();
            assertMaximumDepth(jdbc);
        } finally {
            schema.clean();
        }
    }

    /** Gợi ý hãng đúng từng nhóm trong ảnh; một hãng dùng lại cho hàng cũ, không tạo Samsung cũ riêng. */
    @Test
    void referenceBrandsBelongToCorrectProductGroups() {
        var source = datasource();
        var schema = flyway(source, "13");
        try {
            schema.migrate();
            var jdbc = new JdbcTemplate(source);
            assertThat(brandSlugs(jdbc, "tivi")).containsExactlyInAnyOrder(
                    "samsung", "lg", "xiaomi", "coocaa", "sony", "tcl", "vsp", "aqua");
            assertThat(brandSlugs(jdbc, "may-lanh")).contains("daikin", "casper", "hitachi");
            assertThat(brandSlugs(jdbc, "may-rua-chen-bat")).containsExactly("bosch");
            assertThat(brandSlugs(jdbc, "dien-thoai-cu")).contains("apple", "samsung", "oneplus", "oppo");
            assertThat(brandSlugs(jdbc, "laptop-cu")).contains("dell", "asus", "acer", "hp", "microsoft-surface");
            assertThat(brandSlugs(jdbc, "bao-hanh-mo-rong")).containsExactlyInAnyOrder("apple", "samsung");
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM brands
                    WHERE slug LIKE '%-cu'
                    """, Long.class)).isZero();
        } finally {
            schema.clean();
        }
    }

    /** Hãng/danh mục trùng tên khác slug dùng ID đã có; migration không sửa cây, SKU, giá, kho hay sổ cái. */
    @Test
    void upgradePreservesUserDataAndReusesExistingReferences() {
        var source = datasource();
        var schema = flyway(source, "13");
        try {
            flyway(source, "12").migrate();
            var jdbc = new JdbcTemplate(source);
            seedExistingData(jdbc);
            Map<String, List<Map<String, Object>>> before = new java.util.LinkedHashMap<>();
            for (String table : List.of("users", "products", "inventories", "inventory_movements", "orders",
                    "order_items", "payments", "shipments")) {
                before.put(table, snapshot(jdbc, table));
            }
            schema.migrate();
            before.forEach((table, rows) -> assertThat(snapshot(jdbc, table)).as(table).isEqualTo(rows));
            assertThat(jdbc.queryForObject("""
                    SELECT slug
                    FROM categories
                    WHERE id = 400
                    """, String.class)).isEqualTo("owner-accessories");
            assertThat(jdbc.queryForObject("""
                    SELECT parent_id
                    FROM categories
                    WHERE id = 401
                    """, Long.class)).isEqualTo(402);
            assertThat(jdbc.queryForObject("""
                    SELECT parent_id
                    FROM categories
                    WHERE slug = 'dan-man-hinh'
                    """, Long.class)).isEqualTo(401);
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM brands
                    WHERE LOWER(name) = 'coocaa'
                    """, Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("""
                    SELECT brand.id
                    FROM brands brand
                    JOIN brand_categories relation ON relation.brand_id = brand.id
                    JOIN categories category ON category.id = relation.category_id
                    WHERE category.slug = 'tivi'
                      AND LOWER(brand.name) = 'coocaa'
                    """, Long.class)).isEqualTo(400);
            assertMaximumDepth(jdbc);
        } finally {
            schema.clean();
        }
    }

    /** Nhóm do chủ cửa hàng đặt ở cấp ba được giữ nguyên; seed không tạo cây cấp bốn vượt quy tắc API. */
    @Test
    void customThirdLevelParentIsNotMovedOrExtendedPastLimit() {
        var source = datasource();
        var schema = flyway(source, "13");
        try {
            flyway(source, "12").migrate();
            var jdbc = new JdbcTemplate(source);
            jdbc.update("""
                    INSERT INTO categories (id, name, slug, parent_id)
                    VALUES
                        (600, 'Nhánh riêng', 'private-root', NULL),
                        (601, 'Nhánh riêng cấp hai', 'private-second', 600),
                        (602, 'Phụ kiện di động', 'private-mobile', 601)
                    """);
            schema.migrate();
            assertThat(jdbc.queryForObject("""
                    SELECT parent_id
                    FROM categories
                    WHERE id = 602
                    """, Long.class)).isEqualTo(601);
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM categories
                    WHERE parent_id = 602
                    """, Long.class)).isZero();
            assertMaximumDepth(jdbc);
        } finally {
            schema.clean();
        }
    }

    /** Fixture trước V13 gồm catalog nhập tay, một đơn đang giữ hàng và movement hợp lệ. */
    private void seedExistingData(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO categories (id, name, slug, parent_id)
                VALUES
                    (400, 'Phụ kiện', 'owner-accessories', NULL),
                    (402, 'Nhóm riêng', 'owner-root', NULL),
                    (401, 'Phụ kiện di động', 'owner-mobile', 402)
                """);
        jdbc.update("""
                INSERT INTO brands (id, name, slug)
                VALUES (400, 'COOCAA', 'owner-coocaa')
                """);
        jdbc.update("""
                INSERT INTO users (id, email, password_hash, full_name, role_id)
                SELECT 400, 'migration-v13@example.com', 'hash', 'Người kiểm tra nâng cấp', id
                FROM roles
                WHERE name = 'ADMIN'
                """);
        jdbc.update("""
                INSERT INTO warehouses (id, code, name, address, status)
                VALUES (400, 'V13-QA', 'Kho giữ nguyên', 'Địa chỉ giữ nguyên', 'ACTIVE')
                """);
        jdbc.update("""
                INSERT INTO products (id, category_id, brand_id, sku, name, unit_price, status, image_url, description)
                VALUES (400, 400, 400, 'OWNER-SKU', 'Sản phẩm nhập tay', 120000, 'ACTIVE',
                        '/assets/stockflow.svg', 'Mô tả giữ nguyên')
                """);
        jdbc.update("""
                INSERT INTO inventories (id, product_id, warehouse_id, available_quantity, reserved_quantity, version)
                VALUES (400, 400, 400, 7, 2, 3)
                """);
        jdbc.update("""
                INSERT INTO orders (id, order_code, customer_id, warehouse_id, status, total_amount)
                VALUES (400, 'OWNER-ORDER', 400, 400, 'PENDING', 240000)
                """);
        jdbc.update("""
                INSERT INTO order_items (id, order_id, product_id, quantity, unit_price, line_total)
                VALUES (400, 400, 400, 2, 120000, 240000)
                """);
        jdbc.update("""
                INSERT INTO payments (id, order_id, status, amount, method)
                VALUES (400, 400, 'PENDING', 240000, 'SIMULATED_BANKING')
                """);
        jdbc.update("""
                INSERT INTO inventory_movements
                    (inventory_id, performed_by, type, quantity, balance_before, balance_after, reference_type, reference_id)
                VALUES
                    (400, 400, 'GOODS_RECEIPT', 9, 0, 9, 'MANUAL_ADJUSTMENT', NULL),
                    (400, 400, 'RESERVATION_HOLD', 2, 9, 9, 'ORDER', 400)
                """);
    }

    /** Chuỗi truy vấn chỉ được chọn trong danh sách bảng fixture cố định, không nhận đầu vào HTTP. */
    private List<Map<String, Object>> snapshot(JdbcTemplate jdbc, String table) {
        return jdbc.queryForList("""
                SELECT *
                FROM %s
                ORDER BY id
                """.formatted(table));
    }

    /** Đếm bảng fixture cố định để khẳng định migration chỉ tạo dữ liệu tham chiếu. */
    private long count(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM %s
                """.formatted(table), Long.class);
    }

    /** Đọc hãng qua quan hệ thật thay vì so sánh nhãn được hardcode trên giao diện. */
    private List<String> brandSlugs(JdbcTemplate jdbc, String categorySlug) {
        return jdbc.queryForList("""
                SELECT brand.slug
                FROM brands brand
                JOIN brand_categories relation ON relation.brand_id = brand.id
                JOIN categories category ON category.id = relation.category_id
                WHERE category.slug = ?
                ORDER BY brand.id
                """, String.class, categorySlug);
    }

    /** Cha/con được tra theo khóa ngoại để kiểm chứng cây danh mục. */
    private String parentSlug(JdbcTemplate jdbc, String slug) {
        return jdbc.queryForObject("""
                SELECT parent.slug
                FROM categories child
                JOIN categories parent ON parent.id = child.parent_id
                WHERE child.slug = ?
                """, String.class, slug);
    }

    /** Cây tối đa ba cấp phải giữ hợp lệ sau nâng cấp kể cả có tham chiếu nhập tay. */
    private void assertMaximumDepth(JdbcTemplate jdbc) {
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM categories child
                JOIN categories parent ON parent.id = child.parent_id
                JOIN categories grandparent ON grandparent.id = parent.parent_id
                WHERE grandparent.parent_id IS NOT NULL
                """, Long.class)).isZero();
    }

    /** H2 riêng từng ca; cleanup không thể chạm database PostgreSQL của người dùng. */
    private DriverManagerDataSource datasource() {
        return new DriverManagerDataSource("jdbc:h2:mem:catalog-completion-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
    }

    /** Chốt checkpoint để kiểm chứng riêng V12→V13 và không sửa migration đã nghiệm thu. */
    private Flyway flyway(DriverManagerDataSource source, String version) {
        return Flyway.configure().dataSource(source).target(version)
                .locations("filesystem:src/test/resources/db/migration").cleanDisabled(false).load();
    }
}
