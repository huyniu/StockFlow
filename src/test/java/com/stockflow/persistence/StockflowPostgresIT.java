package com.stockflow.persistence;

import static com.stockflow.user.support.UserTestFixtures.verifiedUser;

import static com.stockflow.order.support.CheckoutTestData.orderRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.auth.security.JwtTokenProvider;
import com.stockflow.catalog.domain.Category;
import com.stockflow.catalog.domain.Product;
import com.stockflow.catalog.domain.ProductStatus;
import com.stockflow.catalog.repository.CategoryRepository;
import com.stockflow.catalog.repository.ProductRepository;
import com.stockflow.common.exception.ConflictException;
import com.stockflow.inventory.dto.StockInRequest;
import com.stockflow.inventory.repository.InventoryRepository;
import com.stockflow.inventory.service.InventoryService;
import com.stockflow.order.dto.CreateOrderRequest;
import com.stockflow.order.dto.OrderResponse;
import com.stockflow.order.service.OrderPlacementService;
import com.stockflow.order.service.OrderService;
import com.stockflow.user.domain.User;
import com.stockflow.user.repository.RoleRepository;
import com.stockflow.user.repository.UserRepository;
import com.stockflow.warehouse.domain.Warehouse;
import com.stockflow.warehouse.domain.WarehouseStatus;
import com.stockflow.warehouse.repository.WarehouseRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/** Dùng migration PostgreSQL thật để kiểm chứng trigger, khóa UNIQUE và atomic reserve trên nhiều connection. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("postgres-test")
class StockflowPostgresIT {

    private static final String EXTERNAL_URL = System.getProperty("stockflow.pg-test.url");
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("stockflow_test")
            .withUsername("stockflow_test")
            .withPassword("stockflow_test");

    @Autowired JdbcTemplate jdbc;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired WarehouseRepository warehouses;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired InventoryRepository inventories;
    @Autowired InventoryService stock;
    @Autowired OrderService orders;
    @Autowired OrderPlacementService placement;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenProvider jwt;
    Product product;
    Product second;
    Warehouse warehouse;
    User customer;
    User admin;

    /** CI luôn tạo container; chế độ chẩn đoán chỉ nhận database QA riêng trên loopback. */
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        if (EXTERNAL_URL == null) {
            POSTGRES.start();
            properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            properties.add("spring.datasource.username", POSTGRES::getUsername);
            properties.add("spring.datasource.password", POSTGRES::getPassword);
        } else {
            if (!EXTERNAL_URL.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/stockflow_qa")) {
                throw new IllegalArgumentException("Chỉ cho dùng database stockflow_qa riêng trên 127.0.0.1.");
            }
            properties.add("spring.datasource.url", () -> EXTERNAL_URL);
            properties.add("spring.datasource.username", () -> "stockflow_qa");
            properties.add("spring.datasource.password", () -> "");
        }
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    /** Chỉ dừng container do bộ test tạo, không dừng database QA ngoài hoặc server đang dùng của cửa hàng. */
    @AfterAll
    static void stopContainer() {
        if (EXTERNAL_URL == null) POSTGRES.stop();
    }

    /** Dữ liệu riêng từng ca, ledger không bị xóa để chuẩn bị fixture cho ca tiếp theo. */
    @BeforeEach
    void fixture() {
        String key = UUID.randomUUID().toString();
        var category = categories.save(new Category("Danh mục PostgreSQL " + key, key));
        product = products.save(new Product(category, key + "-1", "Sản phẩm PostgreSQL",
                new BigDecimal("10.00"), ProductStatus.ACTIVE));
        second = products.save(new Product(category, key + "-2", "Sản phẩm PostgreSQL khác",
                new BigDecimal("20.00"), ProductStatus.ACTIVE));
        warehouse = warehouses.save(new Warehouse(key, "Kho PostgreSQL QA", "Địa chỉ kiểm thử",
                WarehouseStatus.ACTIVE));
        customer = user("CUSTOMER");
        admin = user("ADMIN");
        stock.stockIn(new StockInRequest(product.getId(), warehouse.getId(), 5, "Tồn PostgreSQL QA"), admin.getId());
        stock.stockIn(new StockInRequest(second.getId(), warehouse.getId(), 1, "Tồn rollback QA"), admin.getId());
    }

    /** Chạy đúng V17 và hàm PL/pgSQL production, không dùng bản thay thế H2. */
    @Test
    void productionMigrationsAndTriggerAreLoaded() {
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version = '17'
                  AND success = TRUE
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM pg_trigger
                WHERE tgname = 'immutable_inventory_movements'
                  AND NOT tgisinternal
                """, Integer.class)).isEqualTo(1);
    }

    /** UPDATE trực tiếp không vượt qua được trigger bất biến, kể cả khi dùng JDBC bỏ qua entity. */
    @Test
    void ledgerRejectsDirectUpdate() {
        assertThatThrownBy(() -> jdbc.update("""
                UPDATE inventory_movements
                SET note = 'Sửa lịch sử trái phép'
                WHERE inventory_id = ?
                """, inventoryId(product)))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("bất biến");
        assertThat(movementCount(product, "GOODS_RECEIPT")).isEqualTo(1);
    }

    /** DELETE trực tiếp bị chặn; test không tắt trigger hoặc xóa ledger khi dọn dữ liệu. */
    @Test
    void ledgerRejectsDirectDelete() {
        assertThatThrownBy(() -> jdbc.update("""
                DELETE FROM inventory_movements
                WHERE inventory_id = ?
                """, inventoryId(product)))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("bất biến");
        assertThat(movementCount(product, "GOODS_RECEIPT")).isEqualTo(1);
    }

    /** CHECK và UNIQUE thật bảo vệ số lượng âm và một dòng tồn duy nhất cho mỗi sản phẩm/kho. */
    @Test
    void databaseRejectsNegativeStockAndDuplicateInventory() {
        assertThatThrownBy(() -> jdbc.update("""
                UPDATE inventories
                SET available_quantity = -1
                WHERE id = ?
                """, inventoryId(product))).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO inventories (product_id, warehouse_id)
                VALUES (?, ?)
                """, product.getId(), warehouse.getId())).isInstanceOf(DataAccessException.class);
        assertStock(product, 5, 0);
    }

    /** UNIQUE khóa gửi lại tuần tự hóa 12 connection về một đơn và đúng một movement giữ hàng. */
    @Test
    void concurrentIdenticalKeysCreateOneOrder() throws Exception {
        List<OrderResponse> results = concurrent(12, index -> request(product, 2), index -> "same-key");
        assertThat(results).doesNotContainNull();
        assertThat(results.stream().map(OrderResponse::id).distinct()).hasSize(1);
        assertThat(movementCount(product, "RESERVATION_HOLD")).isEqualTo(1);
        assertStock(product, 3, 2);
    }

    /** Các khóa khác nhau vẫn cạnh tranh bằng atomic conditional update; không bán vượt năm đơn vị tồn. */
    @Test
    void competingOrdersNeverOversell() throws Exception {
        var results = concurrent(8, index -> request(product, 2), index -> "compete-" + index);
        assertThat(results.stream().filter(Objects::nonNull)).hasSize(2);
        assertThat(movementCount(product, "RESERVATION_HOLD")).isEqualTo(2);
        assertStock(product, 1, 4);
    }

    /** Mặt hàng thứ hai thiếu tồn phải rollback cả lượt reserve đầu và khóa đặt hàng. */
    @Test
    void multiItemFailureRollsBackInventoryLedgerAndKey() {
        var request = orderRequest(warehouse.getId(), List.of(
                new CreateOrderRequest.Item(product.getId(), 2),
                new CreateOrderRequest.Item(second.getId(), 2)));
        assertThatThrownBy(() -> placement.place(request, customer.getId(), "rollback"))
                .isInstanceOf(ConflictException.class);
        assertStock(product, 5, 0);
        assertStock(second, 1, 0);
        assertThat(movementCount(product, "RESERVATION_HOLD")).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM order_creation_requests
                WHERE customer_id = ?
                """, Integer.class, customer.getId())).isZero();
    }

    /** Hai giỏ đảo thứ tự SKU hoàn thành trong timeout; inventoryId luôn được cập nhật cùng thứ tự. */
    @Test
    void reversedItemOrderDoesNotDeadlock() throws Exception {
        stock.stockIn(new StockInRequest(second.getId(), warehouse.getId(), 4, "Bổ sung test nhiều SKU"),
                admin.getId());
        var results = concurrent(2, index -> orderRequest(warehouse.getId(), index == 0
                ? List.of(new CreateOrderRequest.Item(product.getId(), 3),
                        new CreateOrderRequest.Item(second.getId(), 3))
                : List.of(new CreateOrderRequest.Item(second.getId(), 3),
                        new CreateOrderRequest.Item(product.getId(), 3))),
                index -> "reverse-" + index);
        assertThat(results.stream().filter(Objects::nonNull)).hasSize(1);
        assertStock(product, 2, 3);
        assertStock(second, 2, 3);
    }

    /** HTTP dùng JWT và khóa retry thật; API bán chạy công khai chạy cùng SQL trên PostgreSQL. */
    @Test
    void httpRetryAndPublicBestsellersWorkOnPostgres() throws Exception {
        var request = request(product, 1);
        String first = httpOrder(request);
        String again = httpOrder(request);
        long orderId = json.readTree(first).get("id").asLong();
        assertThat(json.readTree(again).get("id").asLong()).isEqualTo(orderId);
        orders.confirmPaymentSimulation(orderId, customer.getId());
        String result = mvc.perform(get("/api/v1/storefront/bestsellers").param("limit", "12"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Long> ids = new ArrayList<>();
        json.readTree(result).forEach(value -> ids.add(value.get("id").asLong()));
        assertThat(ids).contains(product.getId());
        assertThat(movementCount(product, "DISPATCH")).isEqualTo(1);
        assertStock(product, 4, 0);
    }

    /** Gọi POST thực qua filter bảo mật, không giả lập authentication bằng annotation test. */
    private String httpOrder(CreateOrderRequest request) throws Exception {
        return mvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + jwt.generateToken(customer))
                        .header("Idempotency-Key", "http-retry")
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    /** Khởi phát cùng lúc các service transaction; chỉ lỗi thiếu tồn được tính là không thành công. */
    private List<OrderResponse> concurrent(int count, IntFunction<CreateOrderRequest> request,
            IntFunction<String> key) throws Exception {
        var pool = Executors.newFixedThreadPool(count);
        var ready = new CountDownLatch(count);
        var start = new CountDownLatch(1);
        var futures = new ArrayList<java.util.concurrent.Future<OrderResponse>>();
        try {
            for (int index = 0; index < count; index++) {
                int attempt = index;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Quá hạn hàng rào test.");
                    try {
                        return placement.place(request.apply(attempt), customer.getId(), key.apply(attempt));
                    } catch (ConflictException insufficientStock) {
                        return null;
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<OrderResponse> results = new ArrayList<>();
            for (var future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            return results;
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    /** Đọc kho qua repository sau khi mọi transaction kết thúc để tránh snapshot cũ của entity. */
    private void assertStock(Product sku, int available, int reserved) {
        var inventory = inventories.findByProductIdAndWarehouseId(sku.getId(), warehouse.getId()).orElseThrow();
        assertThat(inventory.getAvailableQuantity()).isEqualTo(available);
        assertThat(inventory.getReservedQuantity()).isEqualTo(reserved);
        assertThat(inventory.getPhysicalQuantity()).isEqualTo(available + reserved);
    }

    /** Tra ID tồn kho đúng cặp SKU/kho của fixture. */
    private Long inventoryId(Product sku) {
        return inventories.findByProductIdAndWarehouseId(sku.getId(), warehouse.getId()).orElseThrow().getId();
    }

    /** Đếm ledger của riêng fixture bằng SQL bind, không phụ thuộc dữ liệu các ca khác. */
    private int movementCount(Product sku, String type) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM inventory_movements
                WHERE inventory_id = ?
                  AND type = ?
                """, Integer.class, inventoryId(sku), type);
    }

    /** Dùng thông tin giao hàng chuẩn của bộ test hiện có. */
    private CreateOrderRequest request(Product sku, int quantity) {
        return orderRequest(warehouse.getId(), List.of(new CreateOrderRequest.Item(sku.getId(), quantity)));
    }

    /** Actor có role thật trong database; tài khoản không được dùng cho cửa hàng đang chạy. */
    private User user(String role) {
        return users.save(verifiedUser(UUID.randomUUID() + "@postgres.test", "test-hash", "Actor PostgreSQL",
                roles.findByName(role).orElseThrow()));
    }
}
