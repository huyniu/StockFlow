package com.stockflow.demo;

import static com.stockflow.order.support.CheckoutTestData.orderRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.catalog.repository.ProductRepository;
import com.stockflow.order.dto.CreateOrderRequest;
import com.stockflow.order.service.OrderService;
import com.stockflow.user.repository.UserRepository;
import com.stockflow.warehouse.repository.WarehouseRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Khởi động fixture StockFlow Tech trên database H2 riêng, kiểm tra đăng nhập, ledger và nạp lại an toàn.
 * Mỗi test rollback thay đổi nghiệp vụ để dữ liệu seed ban đầu luôn ổn định, không xóa ledger.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:stockflow_demo;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        // Suite fixture bật catalog đầy đủ; chế độ nhập tay vẫn có thể tắt flag.
        "app.demo.seed-catalog=true"
})
@AutoConfigureMockMvc
@ActiveProfiles({"test", "demo"})
@Transactional
class DemoDataIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired DemoDataSeeder seeder;
    @Autowired UserRepository users;
    @Autowired ProductRepository products;
    @Autowired WarehouseRepository warehouses;
    @Autowired PasswordEncoder passwords;
    @Autowired OrderService orders;
    @Autowired EntityManager entities;

    /** Bốn mật khẩu demo phải đăng nhập thật được và trả đúng role, không chỉ tồn tại trong database. */
    @ParameterizedTest
    @CsvSource({
        "admin@stockflow.com, Admin@123, ADMIN",
        "manager@stockflow.com, Manager@123, MANAGER",
        "staff.hn@stockflow.com, Staff@123, WAREHOUSE_STAFF",
        "customer@stockflow.com, Customer@123, CUSTOMER"
    })
    void demoAccountsCanLogIn(String email, String password, String role) throws Exception {
        JsonNode response = login(email, password);
        assertThat(response.path("user").path("role").asText()).isEqualTo(role);
        String hash = users.findByEmail(email).orElseThrow().getPasswordHash();
        assertThat(hash).isNotEqualTo(password);
        assertThat(passwords.matches(password, hash)).isTrue();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/v1/users/me").header("Authorization", "Bearer " + response.path("access_token").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
    }

    /** Mỗi tồn kho ban đầu có đúng một GOODS_RECEIPT với actor ADMIN và snapshot vật lý chính xác. */
    @Test
    void seedCreatesCatalogAndValidReceiptLedger() {
        assertSeedCounts();
        // Năm nhóm hàng đều có sản phẩm; không còn fixture gia dụng hoặc thời trang trên database mới.
        List<Map<String, Object>> categoryCounts = jdbc.queryForList("""
                SELECT c.slug, COUNT(p.id) AS product_count
                FROM categories c
                JOIN products p ON p.category_id = c.id
                GROUP BY c.slug
                ORDER BY c.slug
                """);
        assertThat(categoryCounts).hasSize(5);
        assertThat(categoryCounts).extracting(row -> row.get("slug"))
                .containsExactly(
                        "ban-phim-chuot",
                        "hub-cap-sac",
                        "man-hinh-ban-lam-viec",
                        "tai-nghe-loa",
                        "webcam-micro");
        assertThat(categoryCounts).extracting(row -> ((Number) row.get("product_count")).longValue())
                .containsExactly(6L, 5L, 4L, 5L, 4L);
        assertThat(products.findBySku("TECH-KBD-01").orElseThrow().getImageUrl())
                .startsWith("https://images.unsplash.com/");
        Long adminId = users.findByEmail("admin@stockflow.com").orElseThrow().getId();
        List<Map<String, Object>> ledger = jdbc.queryForList("""
                SELECT i.available_quantity, i.reserved_quantity, m.quantity,
                       m.balance_before, m.balance_after, m.type, m.performed_by
                FROM inventories i
                JOIN inventory_movements m ON m.inventory_id = i.id
                ORDER BY i.id
                """);
        assertThat(ledger).hasSize(72);
        for (Map<String, Object> row : ledger) {
            int quantity = ((Number) row.get("available_quantity")).intValue();
            assertThat(quantity).isBetween(20, 50);
            assertThat(((Number) row.get("reserved_quantity")).intValue()).isZero();
            assertThat(((Number) row.get("quantity")).intValue()).isEqualTo(quantity);
            assertThat(((Number) row.get("balance_before")).intValue()).isZero();
            assertThat(((Number) row.get("balance_after")).intValue()).isEqualTo(quantity);
            assertThat(row.get("type")).isEqualTo("GOODS_RECEIPT");
            assertThat(((Number) row.get("performed_by")).longValue()).isEqualTo(adminId);
        }
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM inventories
                WHERE available_quantity <= 10
                """, Long.class)).isZero();
    }

    /** Gọi seed nhiều lần không nhân đôi catalog, tài khoản, phân công hay movement. */
    @Test
    void repeatedSeedDoesNotDuplicateRecords() {
        Long userCount = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM users
                """, Long.class);
        seeder.seed();
        seeder.seed();
        assertSeedCounts();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM users
                """, Long.class)).isEqualTo(userCount);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM warehouse_staff_assignments
                """, Long.class)).isEqualTo(1);
    }

    /** Seed không nạp lại hàng đã bán hết và không ghi đè giá hay mật khẩu đã thay đổi. */
    @Test
    void restartPreservesSoldOutStockAndExistingAccountData() {
        Long productId = products.findBySku("TECH-KBD-01").orElseThrow().getId();
        Long warehouseId = warehouses.findByCode("WH-HAN-01").orElseThrow().getId();
        Long customerId = users.findByEmail("customer@stockflow.com").orElseThrow().getId();
        Integer quantity = available(productId, warehouseId);
        var order = orders.createOrder(orderRequest(warehouseId,
                List.of(new CreateOrderRequest.Item(productId, quantity))), customerId);
        orders.confirmPaymentSimulation(order.id(), customerId);

        String changedHash = passwords.encode("Changed@123");
        jdbc.update("""
                UPDATE products
                SET unit_price = 123.45
                WHERE id = ?
                """, productId);
        jdbc.update("""
                UPDATE users
                SET password_hash = ?
                WHERE id = ?
                """, changedHash, customerId);
        // Mô phỏng persistence context mới ở lần khởi động tiếp theo, tránh đọc entity đã cache.
        entities.clear();
        seeder.seed();

        assertThat(available(productId, warehouseId)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT unit_price
                FROM products
                WHERE id = ?
                """, BigDecimal.class, productId)).isEqualByComparingTo("123.45");
        assertThat(jdbc.queryForObject("""
                SELECT password_hash
                FROM users
                WHERE id = ?
                """, String.class, customerId)).isEqualTo(changedHash);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM inventory_movements
                """, Long.class)).isEqualTo(74);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM payments
                WHERE order_id = ?
                  AND status = 'PAID'
                """, Long.class, order.id())).isEqualTo(1);
    }

    /** JWT của nhân viên demo chỉ nhập được kho Hà Nội đã phân công, kho Đà Nẵng phải trả 403. */
    @Test
    void demoStaffIsRestrictedToHanoiWarehouse() throws Exception {
        Long productId = products.findBySku("TECH-KBD-01").orElseThrow().getId();
        Long hanoiId = warehouses.findByCode("WH-HAN-01").orElseThrow().getId();
        Long danangId = warehouses.findByCode("WH-DAD-01").orElseThrow().getId();
        int hanoiBefore = available(productId, hanoiId);
        int danangBefore = available(productId, danangId);
        String bearer = "Bearer " + login("staff.hn@stockflow.com", "Staff@123").path("access_token").asText();

        mvc.perform(post("/api/v1/inventories/stock-in").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "product_id", productId, "warehouse_id", hanoiId, "quantity", 2))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.available_quantity").value(hanoiBefore + 2));
        mvc.perform(post("/api/v1/inventories/stock-in").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "product_id", productId, "warehouse_id", danangId, "quantity", 2))))
                .andExpect(status().isForbidden());
        assertThat(available(productId, danangId)).isEqualTo(danangBefore);
    }

    /** Dòng tồn rỗng chưa có ledger và SKU nhập tay đều được nạp một lần tại ba kho demo. */
    @Test
    void seedsExistingEmptyInventoryAndManualProductOnce() {
        var category = products.findBySku("TECH-KBD-01").orElseThrow().getCategory();
        var product = products.save(new com.stockflow.catalog.domain.Product(category, "MANUAL-SEED-01",
                "Sản phẩm chưa nạp tồn", new BigDecimal("250000"), com.stockflow.catalog.domain.ProductStatus.ACTIVE));
        var warehouse = warehouses.findByCode("WH-HAN-01").orElseThrow();
        jdbc.update("""
                INSERT INTO inventories(product_id, warehouse_id, available_quantity, reserved_quantity, version, updated_at)
                VALUES (?, ?, 0, 0, 0, CURRENT_TIMESTAMP)
                """, product.getId(), warehouse.getId());
        seeder.seed();
        seeder.seed();
        var receipts = jdbc.queryForList("""
                SELECT i.available_quantity, m.type, m.balance_before, m.balance_after, m.quantity
                FROM inventories i JOIN inventory_movements m ON m.inventory_id = i.id
                WHERE i.product_id = ?
                """, product.getId());
        assertThat(receipts).hasSize(3);
        for (var row : receipts) {
            int quantity = ((Number) row.get("available_quantity")).intValue();
            assertThat(quantity).isBetween(20, 50);
            assertThat(row.get("type")).isEqualTo("GOODS_RECEIPT");
            assertThat(((Number) row.get("balance_before")).intValue()).isZero();
            assertThat(((Number) row.get("balance_after")).intValue()).isEqualTo(quantity);
            assertThat(((Number) row.get("quantity")).intValue()).isEqualTo(quantity);
        }
    }

    /** Đăng nhập qua endpoint public và kiểm tra response có token thật trước khi dùng gọi API. */
    private JsonNode login(String email, String password) throws Exception {
        JsonNode response = json.readTree(mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(response.path("access_token").asText()).isNotBlank();
        return response;
    }

    /** Đếm dữ liệu cốt lõi của database riêng để phát hiện nạp thiếu hoặc trùng dữ liệu. */
    private void assertSeedCounts() {
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM warehouses
                """, Long.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                -- V13 có 125 nhóm tham chiếu, cộng năm nhóm Tech của runner demo.
                FROM categories
                """, Long.class)).isEqualTo(130);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM products
                """, Long.class)).isEqualTo(24);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM inventories
                """, Long.class)).isEqualTo(72);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM inventory_movements
                """, Long.class)).isEqualTo(72);
    }

    /** Đọc số tồn từ database để không dùng snapshot entity cũ sau atomic update. */
    private Integer available(Long productId, Long warehouseId) {
        return jdbc.queryForObject("""
                SELECT available_quantity
                FROM inventories
                WHERE product_id = ?
                  AND warehouse_id = ?
                """, Integer.class, productId, warehouseId);
    }
}
