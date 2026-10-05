package com.stockflow.order;

import static com.stockflow.order.support.CheckoutTestData.orderRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.auth.security.JwtTokenProvider;
import com.stockflow.catalog.domain.Category;
import com.stockflow.catalog.domain.Product;
import com.stockflow.catalog.domain.ProductStatus;
import com.stockflow.catalog.repository.CategoryRepository;
import com.stockflow.catalog.repository.ProductRepository;
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
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Kiểm chứng retry/khóa cùng nội dung, phân quyền, rollback và nhiều thread gửi cùng lần đặt hàng. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderIdempotencyIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenProvider jwt;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired WarehouseRepository warehouses;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired InventoryRepository inventories;
    @Autowired InventoryService stock;
    @Autowired OrderService orders;
    @Autowired OrderPlacementService placement;
    @Autowired JdbcTemplate jdbc;
    Product product;
    Product second;
    Warehouse warehouse;
    User customer;
    User other;
    User admin;

    /** Mỗi ca dùng ID riêng, không xóa ledger hoặc dữ liệu của các bài test khác. */
    @BeforeEach
    void setUp() {
        String token = UUID.randomUUID().toString();
        var category = categories.save(new Category("Danh mục retry " + token, token));
        product = products.save(new Product(category, token + "-1", "Sản phẩm retry",
                new BigDecimal("10.00"), ProductStatus.ACTIVE));
        second = products.save(new Product(category, token + "-2", "Sản phẩm retry khác",
                new BigDecimal("20.00"), ProductStatus.ACTIVE));
        warehouse = warehouses.save(new Warehouse(token, "Kho retry", "Địa chỉ test", WarehouseStatus.ACTIVE));
        customer = user("CUSTOMER");
        other = user("CUSTOMER");
        admin = user("ADMIN");
        stock.stockIn(new StockInRequest(product.getId(), warehouse.getId(), 5, "Tồn kiểm chứng retry"), admin.getId());
        stock.stockIn(new StockInRequest(second.getId(), warehouse.getId(), 5, "Tồn kiểm chứng retry"), admin.getId());
    }

    /** Hai POST cùng khóa trả cùng ID và chỉ giữ hàng/ghi movement một lần. */
    @Test
    void retriesReturnOneOrder() throws Exception {
        JsonNode first = postOrder(customer, "retry-1", request(2), 201);
        JsonNode again = postOrder(customer, "retry-1", request(2), 201);
        assertThat(again.get("id")).isEqualTo(first.get("id"));
        assertStock(3, 2);
        assertThat(count("orders", "customer_id", customer.getId())).isEqualTo(1);
        assertThat(holds(first.get("id").asLong())).isEqualTo(1);
        assertThat(count("order_creation_requests", "customer_id", customer.getId())).isEqualTo(1);
    }

    /** Đổi số lượng dưới khóa cũ bị chặn 409, không thay đơn trước hoặc giữ thêm tồn. */
    @Test
    void keyCannotBeReusedForAnotherPayload() throws Exception {
        postOrder(customer, "retry-2", request(2), 201);
        JsonNode error = postOrder(customer, "retry-2", request(3), 409);
        assertThat(error.get("message").asText()).contains("nội dung khác");
        assertStock(3, 2);
        assertThat(count("orders", "customer_id", customer.getId())).isEqualTo(1);
    }

    /** Địa chỉ khác cũng là nội dung khác, không chỉ kiểm tra danh sách SKU. */
    @Test
    void changedAddressIsRejected() throws Exception {
        postOrder(customer, "retry-address", request(1), 201);
        var body = json.valueToTree(request(1));
        ((com.fasterxml.jackson.databind.node.ObjectNode) body.get("delivery")).put("address", "Địa chỉ mới");
        mvc.perform(post("/api/v1/orders").header("Authorization", bearer(customer))
                        .header("Idempotency-Key", "retry-address").contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()))
                .andExpect(status().isConflict());
        assertStock(4, 1);
    }

    /** Đổi thứ tự mặt hàng không tạo đơn mới; ledger vẫn chỉ có một hold mỗi SKU. */
    @Test
    void itemOrderDoesNotChangeFingerprint() throws Exception {
        var lines = List.of(new CreateOrderRequest.Item(product.getId(), 1),
                new CreateOrderRequest.Item(second.getId(), 2));
        var first = orderRequest(warehouse.getId(), lines);
        var reversed = orderRequest(warehouse.getId(), List.of(lines.get(1), lines.get(0)));
        JsonNode initial = postOrder(customer, "retry-items", first, 201);
        JsonNode again = postOrder(customer, "retry-items", reversed, 201);
        assertThat(again.get("id")).isEqualTo(initial.get("id"));
        assertThat(holds(initial.get("id").asLong())).isEqualTo(2);
    }

    /** Khóa thuộc tài khoản; hai khách dùng cùng chuỗi vẫn có hai đơn độc lập. */
    @Test
    void keysAreScopedToCustomer() throws Exception {
        var first = postOrder(customer, "shared-key", request(1), 201);
        var secondOrder = postOrder(other, "shared-key", request(1), 201);
        assertThat(first.get("id")).isNotEqualTo(secondOrder.get("id"));
        assertStock(3, 2);
    }

    /** Client chưa có header giữ contract cũ và không tạo bản ghi khóa. */
    @Test
    void headerIsOptionalForExistingClients() throws Exception {
        var first = postOrder(customer, null, request(1), 201);
        var again = postOrder(customer, null, request(1), 201);
        assertThat(first.get("id")).isNotEqualTo(again.get("id"));
        assertThat(count("order_creation_requests", "customer_id", customer.getId())).isZero();
    }

    /** Retry sau thanh toán đọc trạng thái hiện tại và giá snapshot, không tạo thêm dispatch hoặc reserve. */
    @Test
    void replayReturnsCurrentOrderWithOriginalPrice() throws Exception {
        long id = postOrder(customer, "paid-key", request(1), 201).get("id").asLong();
        orders.confirmPaymentSimulation(id, customer.getId());
        jdbc.update("""
                UPDATE products
                SET unit_price = 99.00
                WHERE id = ?
                """, product.getId());
        JsonNode replay = postOrder(customer, "paid-key", request(1), 201);
        assertThat(replay.get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(replay.get("total_amount").decimalValue()).isEqualByComparingTo("10.00");
        assertStock(4, 0);
        assertThat(holds(id)).isEqualTo(1);
    }

    /** Đơn hủy không bị tái tạo khi retry cùng lần gửi; khách đặt lần mới cần khóa mới. */
    @Test
    void replayDoesNotRecreateCancelledOrder() throws Exception {
        long id = postOrder(customer, "cancelled-key", request(1), 201).get("id").asLong();
        orders.cancelOrder(id, customer.getId());
        var replay = postOrder(customer, "cancelled-key", request(1), 201);
        assertThat(replay.get("status").asText()).isEqualTo("CANCELLED");
        assertStock(5, 0);
    }

    /** Thiếu tồn làm rollback cả khóa và đơn; bổ sung hàng rồi retry cùng khóa có thể thành công. */
    @Test
    void failedReservationLeavesNoKeyAndCanRetry() throws Exception {
        postOrder(customer, "failed-key", request(6), 409);
        assertThat(count("order_creation_requests", "customer_id", customer.getId())).isZero();
        assertThat(count("orders", "customer_id", customer.getId())).isZero();
        assertStock(5, 0);
        stock.stockIn(new StockInRequest(product.getId(), warehouse.getId(), 2, "Bổ sung để retry"), admin.getId());
        postOrder(customer, "failed-key", request(6), 201);
        assertStock(1, 6);
    }

    /** Header sai bị chặn trước reserve; không lưu khoảng trắng hoặc khóa vượt giới hạn. */
    @ParameterizedTest
    @ValueSource(strings = {"", " ", "bad key", "khóa-có-dấu"})
    void invalidKeyIsBadRequest(String key) throws Exception {
        postOrder(customer, key, request(1), 400);
        assertThat(count("order_creation_requests", "customer_id", customer.getId())).isZero();
        assertStock(5, 0);
    }

    /** Khóa dài hơn giới hạn không ghi vào database. */
    @Test
    void oversizedKeyIsRejected() throws Exception {
        postOrder(customer, "a".repeat(129), request(1), 400);
        assertStock(5, 0);
    }

    /** ADMIN không được tạo đơn bằng header mới, quyền CUSTOMER hiện có vẫn được bảo vệ. */
    @Test
    void adminCannotPlaceOrder() throws Exception {
        postOrder(admin, "admin-key", request(1), 403);
        assertThat(count("order_creation_requests", "customer_id", admin.getId())).isZero();
        assertStock(5, 0);
    }

    /** Mười hai thread cùng khóa chỉ commit một đơn; đây là cạnh tranh transaction thật, không mock repository. */
    @Test
    void concurrentRetriesCreateExactlyOneOrder() throws Exception {
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(12);
        try {
            var results = new ArrayList<java.util.concurrent.Future<OrderResponse>>();
            for (int index = 0; index < 12; index++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return placement.place(request(2), customer.getId(), "concurrent-key");
                }));
            }
            start.countDown();
            var ids = new ArrayList<Long>();
            for (var result : results) ids.add(result.get(20, TimeUnit.SECONDS).id());
            assertThat(ids.stream().distinct().count()).isEqualTo(1);
            assertThat(count("orders", "customer_id", customer.getId())).isEqualTo(1);
            assertThat(holds(ids.get(0))).isEqualTo(1);
            assertStock(3, 2);
        } finally {
            pool.shutdownNow();
        }
    }

    /** Tạo actor ACTIVE với role có sẵn từ migration; không dùng tài khoản demo hoặc dữ liệu thật. */
    private User user(String role) {
        return users.save(new User(UUID.randomUUID() + "@retry.test", "test-hash", "Khách kiểm thử",
                roles.findByName(role).orElseThrow()));
    }

    /** Payload hợp lệ theo checkout hiện có, không bổ sung giá do client tính. */
    private CreateOrderRequest request(int quantity) {
        return orderRequest(warehouse.getId(), List.of(new CreateOrderRequest.Item(product.getId(), quantity)));
    }

    /** Gửi HTTP bằng JWT thật, tùy chọn header để kiểm tra tương thích client cũ. */
    private JsonNode postOrder(User actor, String key, CreateOrderRequest body, int expected) throws Exception {
        var request = post("/api/v1/orders").header("Authorization", bearer(actor))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if (key != null) request.header("Idempotency-Key", key);
        String response = mvc.perform(request).andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    /** Tạo token cho actor fixture, không giả Role bằng header hoặc request body. */
    private String bearer(User actor) {
        return "Bearer " + jwt.generateToken(actor);
    }

    /** Xác nhận hai ngăn tồn để phát hiện reserve lần hai. */
    private void assertStock(int available, int reserved) {
        var inventory = inventories.findByProductIdAndWarehouseId(product.getId(), warehouse.getId()).orElseThrow();
        assertThat(inventory.getAvailableQuantity()).isEqualTo(available);
        assertThat(inventory.getReservedQuantity()).isEqualTo(reserved);
    }

    /** Tên bảng/cột chỉ từ các hằng test nội bộ; giá trị luôn bind tham số. */
    private long count(String table, String column, Long id) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM %s
                WHERE %s = ?
                """.formatted(table, column), Long.class, id);
    }

    /** Đếm movement theo liên kết đơn để tránh nhầm với các lần nhập kho chuẩn bị fixture. */
    private long holds(Long orderId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM inventory_movements
                WHERE reference_type = 'ORDER'
                  AND reference_id = ?
                  AND type = 'RESERVATION_HOLD'
                """, Long.class, orderId);
    }
}
