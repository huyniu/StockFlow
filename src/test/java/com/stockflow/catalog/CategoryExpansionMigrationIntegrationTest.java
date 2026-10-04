package com.stockflow.catalog;

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

/** Kiểm chứng V9→V10 trên database riêng: bổ sung tham chiếu, giữ dữ liệu đã nhập và không tạo hàng/tồn mẫu. */
class CategoryExpansionMigrationIntegrationTest {

    /** Cài mới có đủ cây, hãng theo ảnh nhưng không sinh sản phẩm hay nghiệp vụ kho giả. */
    @Test
    void freshDatabaseHasReferencesWithoutProductsOrStock() {
        var source = datasource();
        var schema = flyway(source, "10");
        try {
            schema.migrate();
            var jdbc = new JdbcTemplate(source);
            assertThat(count(jdbc, "categories")).isEqualTo(71);
            assertThat(count(jdbc, "brands")).isEqualTo(76);
            assertThat(count(jdbc, "products")).isZero();
            assertThat(count(jdbc, "inventories")).isZero();
            assertThat(count(jdbc, "inventory_movements")).isZero();
            assertThat(jdbc.queryForObject("""
                    SELECT parent.slug
                    FROM categories child
                    JOIN categories parent ON parent.id = child.parent_id
                    WHERE child.slug = 'noi-com-dien'
                    """, String.class)).isEqualTo("gia-dung-nha-bep");
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM information_schema.tables
                    WHERE table_schema = 'public'
                      AND table_name LIKE 'stockflow_catalog_%seed'
                    """, Long.class)).isZero();
        } finally {
            schema.clean();
        }
    }

    /** Chỉ thêm cha cho danh mục cũ; tên trùng dùng nhóm đã có, giá/ảnh/hãng/tồn/sổ cái/đơn giữ nguyên. */
    @Test
    void upgradePreservesExistingBusinessDataAndAvoidsDuplicateNames() {
        var source = datasource();
        var schema = flyway(source, "10");
        try {
            flyway(source, "9").migrate();
            var jdbc = new JdbcTemplate(source);
            seed(jdbc);
            var products = snapshot(jdbc, "products");
            var stock = snapshot(jdbc, "inventories");
            var ledger = snapshot(jdbc, "inventory_movements");
            var orders = snapshot(jdbc, "orders");
            var images = snapshot(jdbc, "product_images");
            schema.migrate();

            assertThat(snapshot(jdbc, "products")).isEqualTo(products);
            assertThat(snapshot(jdbc, "inventories")).isEqualTo(stock);
            assertThat(snapshot(jdbc, "inventory_movements")).isEqualTo(ledger);
            assertThat(snapshot(jdbc, "orders")).isEqualTo(orders);
            assertThat(snapshot(jdbc, "product_images")).isEqualTo(images);
            assertThat(jdbc.queryForObject("""
                    SELECT parent.slug
                    FROM categories child
                    JOIN categories parent ON parent.id = child.parent_id
                    WHERE child.id = 101
                    """, String.class)).isEqualTo("am-thanh-mic-thu-am");
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM categories
                    WHERE name = 'Nồi cơm điện'
                    """, Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("""
                    SELECT parent.slug
                    FROM categories child
                    JOIN categories parent ON parent.id = child.parent_id
                    WHERE child.slug = 'rice-custom'
                    """, String.class)).isEqualTo("gia-dung-nha-bep");
            assertThat(schema.migrate().migrationsExecuted).isZero();
        } finally {
            schema.clean();
        }
    }

    /** Database chặn tự tham chiếu ngay cả khi SQL được gọi ngoài API ADMIN. */
    @Test
    void databaseRejectsSelfParent() {
        var source = datasource();
        var schema = flyway(source, "10");
        try {
            schema.migrate();
            var jdbc = new JdbcTemplate(source);
            assertThatThrownBy(() -> jdbc.update("""
                    UPDATE categories
                    SET parent_id = id
                    WHERE slug = 'laptop'
                    """)).isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            schema.clean();
        }
    }

    /** Fixture trước V10 gồm nội dung nhập tay, bộ ảnh và movement có khóa ngoại hợp lệ. */
    private void seed(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO categories (id, name, slug)
                VALUES
                    (100, 'Laptop', 'laptop'),
                    (101, 'Tai nghe & Loa', 'tai-nghe-loa'),
                    (102, 'Nhóm riêng của cửa hàng', 'custom'),
                    (103, 'Nồi cơm điện', 'rice-custom')
                """);
        jdbc.update("""
                INSERT INTO products (id, category_id, sku, name, unit_price, status, image_url, description)
                VALUES (100, 101, 'SONY-EXISTING', 'Tai nghe đã nhập', 16990000, 'ACTIVE',
                        '/assets/sony.jpg', 'Nội dung của cửa hàng')
                """);
        jdbc.update("""
                INSERT INTO product_images (product_id, position, image_url)
                VALUES (100, 0, '/assets/sony-side.jpg')
                """);
        jdbc.update("""
                INSERT INTO users (id, email, password_hash, full_name, role_id)
                SELECT 100, 'catalog-upgrade@example.com', 'unused', 'Người nhập kho cũ', id
                FROM roles
                WHERE name = 'ADMIN'
                """);
        jdbc.update("""
                INSERT INTO warehouses (id, code, name, address, status)
                VALUES (100, 'UPGRADE-WH', 'Kho cũ', 'Hà Nội', 'ACTIVE')
                """);
        jdbc.update("""
                INSERT INTO inventories (id, product_id, warehouse_id, available_quantity, reserved_quantity, version)
                VALUES (100, 100, 100, 9, 0, 3)
                """);
        jdbc.update("""
                INSERT INTO inventory_movements (
                    inventory_id, performed_by, type, quantity, balance_before, balance_after, note
                )
                VALUES (100, 100, 'GOODS_RECEIPT', 9, 0, 9, 'Ledger trước khi thêm cây')
                """);
        jdbc.update("""
                INSERT INTO orders (id, order_code, customer_id, warehouse_id, status, total_amount)
                VALUES (100, 'SF-BEFORE-TREE', 100, 100, 'CANCELLED', 16990000)
                """);
    }

    /** Chỉ đọc các bảng fixture cố định; không ghép đầu vào của người dùng vào SQL. */
    private List<Map<String, Object>> snapshot(JdbcTemplate jdbc, String table) {
        String sql = switch (table) {
            case "products" -> """
                    SELECT *
                    FROM products
                    ORDER BY id
                    """;
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
            case "product_images" -> """
                    SELECT *
                    FROM product_images
                    ORDER BY product_id, position
                    """;
            default -> throw new IllegalArgumentException("Bảng kiểm chứng không được hỗ trợ.");
        };
        return jdbc.queryForList(sql);
    }

    /** Đếm bằng danh sách bảng cố định của các kiểm chứng migration. */
    private Long count(JdbcTemplate jdbc, String table) {
        return (long) snapshotForCount(jdbc, table).size();
    }

    /** Dữ liệu tham chiếu nhỏ chỉ dùng trong test; mỗi tên bảng đều được kiểm soát trong mã nguồn. */
    private List<Map<String, Object>> snapshotForCount(JdbcTemplate jdbc, String table) {
        return switch (table) {
            case "categories" -> jdbc.queryForList("""
                    SELECT id
                    FROM categories
                    """);
            case "brands" -> jdbc.queryForList("""
                    SELECT id
                    FROM brands
                    """);
            default -> snapshot(jdbc, table);
        };
    }

    /** Database H2 ngẫu nhiên riêng cho từng ca để clean không chạm tới dữ liệu ứng dụng. */
    private DriverManagerDataSource datasource() {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:category-expansion-" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
    }

    /** Chốt version để mô phỏng chính xác checkpoint V9 và nâng cấp V10. */
    private Flyway flyway(DriverManagerDataSource source, String version) {
        return Flyway.configure().dataSource(source).target(version)
                .locations("filesystem:src/test/resources/db/migration").cleanDisabled(false).load();
    }
}
