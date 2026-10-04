package com.stockflow.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockflow.catalog.domain.Category;
import com.stockflow.catalog.domain.Product;
import com.stockflow.catalog.domain.ProductStatus;
import com.stockflow.catalog.repository.CategoryRepository;
import com.stockflow.catalog.repository.ProductRepository;
import com.stockflow.catalog.dto.UpdateCategoryRequest;
import com.stockflow.catalog.service.CategoryService;
import com.stockflow.inventory.dto.StockInRequest;
import com.stockflow.inventory.service.InventoryService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kiểm chứng cửa hàng nhập sản phẩm thủ công: khởi động lại không tự đưa catalog/tồn mẫu trở lại.
 * Database H2 riêng giữ fixture của các suite khác độc lập với chế độ catalog trống.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:stockflow_manual_catalog;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "app.demo.seed-catalog=false"
})
@ActiveProfiles({"test", "demo"})
@Transactional
class ManualCatalogSeedIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired DemoDataSeeder seeder;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired CategoryService categoryService;
    @Autowired InventoryService inventoryService;

    /** Tài khoản, kho, danh mục và phân công staff vẫn sẵn sàng dù chưa có sản phẩm hay tồn mẫu. */
    @Test
    void bootstrapKeepsAccountsAndBranchesWithoutCatalogOrStock() {
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM users
                WHERE email IN (
                    'admin@stockflow.com',
                    'manager@stockflow.com',
                    'staff.hn@stockflow.com',
                    'customer@stockflow.com'
                )
                """, Long.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM warehouses
                """, Long.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                -- V13 có 125 nhóm tham chiếu; nhập tay không tự thêm các nhóm Tech của runner.
                FROM categories
                """, Long.class)).isEqualTo(125);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM warehouse_staff_assignments
                """, Long.class)).isEqualTo(1);
        assertEmptyStock();
        assertThat(products.count()).isZero();
    }

    /** Nạp lại nhiều lần vẫn không tự tái tạo 24 sản phẩm sau khi cửa hàng muốn bắt đầu nhập tay. */
    @Test
    void repeatedBootstrapDoesNotRepopulateEmptyCatalog() {
        seeder.seed();
        seeder.seed();
        assertThat(products.count()).isZero();
        assertEmptyStock();
    }

    /** Khởi động lại không hoàn tác thao tác xóa/đổi slug nhóm Tech từng thuộc runner demo cũ. */
    @Test
    void manualBootstrapDoesNotRestoreDeletedOrRenamedCategory() {
        Category removed = categories.save(new Category("Webcam & Micro", "webcam-micro"));
        Category renamed = categories.save(new Category("Hub, Cáp & Bộ sạc", "hub-cap-sac"));
        categories.flush();
        Long renamedId = renamed.getId();
        categoryService.deleteCategory(removed.getId());
        categoryService.updateCategory(renamedId, new UpdateCategoryRequest("Thiết bị kết nối mới", "ket-noi-moi"));
        long count = categories.count();

        seeder.seed();
        seeder.seed();

        assertThat(categories.count()).isEqualTo(count);
        assertThat(categories.findBySlug("webcam-micro")).isEmpty();
        assertThat(categories.findBySlug("hub-cap-sac")).isEmpty();
        assertThat(categories.findBySlug("ket-noi-moi").orElseThrow().getId()).isEqualTo(renamedId);
        assertThat(products.count()).isZero();
        assertEmptyStock();
    }

    /** Sản phẩm và ảnh do chủ cửa hàng nhập được giữ nguyên; khởi động lại không tự bơm tồn kho. */
    @Test
    void bootstrapPreservesManuallyCreatedProductAndImage() {
        var category = categories.findAll().get(0);
        Product product = products.save(new Product(
                category,
                "MANUAL-IMAGE-01",
                "Sản phẩm nhập thủ công",
                new BigDecimal("350000"),
                ProductStatus.ACTIVE,
                "/assets/stockflow.svg"));
        seeder.seed();
        assertThat(products.count()).isEqualTo(1);
        Product saved = products.findById(product.getId()).orElseThrow();
        assertThat(saved.getImageUrl()).isEqualTo("/assets/stockflow.svg");
        assertThat(saved.getUnitPrice()).isEqualByComparingTo("350000");
        assertEmptyStock();
    }

    /** Đổi định hướng Tech không được tự xóa catalog hoặc ledger của dữ liệu cũ khi chưa reset có chủ đích. */
    @Test
    void bootstrapPreservesLegacyCatalogStockAndLedger() {
        Category legacyCategory = categories.save(new Category("Gia dụng", "gia-dung"));
        Product legacyProduct = products.save(new Product(
                legacyCategory,
                "LEGACY-HOME-01",
                "Sản phẩm cũ cần giữ",
                new BigDecimal("650000"),
                ProductStatus.ACTIVE,
                "/assets/stockflow.svg"));
        Long adminId = jdbc.queryForObject("""
                SELECT id
                FROM users
                WHERE email = ?
                """, Long.class, "admin@stockflow.com");
        Long warehouseId = jdbc.queryForObject("""
                SELECT id
                FROM warehouses
                WHERE code = ?
                """, Long.class, "WH-HAN-01");
        var stock = inventoryService.stockIn(
                new StockInRequest(legacyProduct.getId(), warehouseId, 7, "Tồn cũ trước khi đổi cửa hàng"),
                adminId);

        seeder.seed();

        Product saved = products.findById(legacyProduct.getId()).orElseThrow();
        assertThat(products.count()).isEqualTo(1);
        assertThat(saved.getCategory().getSlug()).isEqualTo("gia-dung");
        assertThat(saved.getUnitPrice()).isEqualByComparingTo("650000");
        assertThat(saved.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(saved.getImageUrl()).isEqualTo("/assets/stockflow.svg");
        assertThat(jdbc.queryForObject("""
                SELECT available_quantity
                FROM inventories
                WHERE id = ?
                """, Integer.class, stock.id())).isEqualTo(7);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM inventory_movements
                WHERE inventory_id = ?
                  AND balance_before = 0
                  AND balance_after = 7
                """, Long.class, stock.id())).isEqualTo(1);
    }

    /** Không tạo tồn hoặc ledger ngầm khi chủ cửa hàng chưa thao tác nhập kho. */
    private void assertEmptyStock() {
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM inventories
                """, Long.class)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM inventory_movements
                """, Long.class)).isZero();
    }
}
