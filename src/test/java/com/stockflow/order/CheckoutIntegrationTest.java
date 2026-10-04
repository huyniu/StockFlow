package com.stockflow.order;

import static com.stockflow.order.support.CheckoutTestData.deliveryPayload;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.auth.security.JwtTokenProvider;
import com.stockflow.catalog.domain.Category;
import com.stockflow.catalog.domain.Product;
import com.stockflow.catalog.domain.ProductStatus;
import com.stockflow.catalog.repository.CategoryRepository;
import com.stockflow.catalog.repository.ProductRepository;
import com.stockflow.common.exception.BadRequestException;
import com.stockflow.inventory.dto.StockInRequest;
import com.stockflow.inventory.repository.InventoryRepository;
import com.stockflow.inventory.service.InventoryService;
import com.stockflow.order.dto.CreateOrderRequest;
import com.stockflow.order.service.OrderService;
import com.stockflow.user.domain.User;
import com.stockflow.user.repository.RoleRepository;
import com.stockflow.user.repository.UserRepository;
import com.stockflow.warehouse.domain.Warehouse;
import com.stockflow.warehouse.domain.WarehouseStatus;
import com.stockflow.warehouse.repository.WarehouseRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Kiểm chứng checkout qua HTTP thật, bản chụp người nhận, quyền riêng tư và transaction tồn kho. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:stockflow_checkout;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CheckoutIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JwtTokenProvider jwt;
    @Autowired private CategoryRepository categories;
    @Autowired private ProductRepository products;
    @Autowired private WarehouseRepository warehouses;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private InventoryRepository inventories;
    @Autowired private InventoryService inventoryService;
    @Autowired private OrderService orders;
    @Autowired private JdbcTemplate jdbc;

    private Product first;
    private Product second;
    private Warehouse warehouse;
    private User customer;
    private User admin;

    /** Fixture riêng từng bài, giữ ledger đã commit; không dùng transaction test che rollback của service. */
    @BeforeEach
    void setup() {
        String key = UUID.randomUUID().toString();
        Category category = categories.save(new Category("Danh mục checkout " + key, key));
        first = products.save(new Product(category, key + "-1", "Sản phẩm checkout", new BigDecimal("12.50"),
                ProductStatus.ACTIVE));
        second = products.save(new Product(category, key + "-2", "Mặt hàng thứ hai", new BigDecimal("20.00"),
                ProductStatus.ACTIVE));
        warehouse = warehouses.save(new Warehouse(key, "Kho checkout", "Hà Nội", WarehouseStatus.ACTIVE));
        customer = user("CUSTOMER");
        admin = user("ADMIN");
        inventoryService.stockIn(new StockInRequest(first.getId(), warehouse.getId(), 5, "Hàng kiểm thử"), admin.getId());
        inventoryService.stockIn(new StockInRequest(second.getId(), warehouse.getId(), 5, "Hàng kiểm thử"), admin.getId());
    }

    /** Giá/kho/chủ đơn vẫn do server quyết định; bản chụp chuẩn hóa xuất hiện ở tạo, chi tiết và lịch sử. */
    @Test
    void createStoresDeliverySnapshotAndKeepsReservationRules() throws Exception {
        var delivery = new LinkedHashMap<String, Object>(deliveryPayload());
        delivery.put("recipient_name", "  Nguyễn Minh An  ");
        delivery.put("recipient_phone", " +84 (90) 123-4567 ");
        delivery.put("address", "  12 Phố Mới\nPhường Cầu Giấy, Hà Nội  ");
        delivery.put("note", "  Gọi trước khi giao  ");
        var body = payload(delivery);
        body.put("customer_id", admin.getId());
        body.put("total_amount", 1);
        body.put("shipping_fee", 999999);
        JsonNode order = create(body);
        long id = order.path("id").asLong();
        JsonNode snapshot = order.path("delivery");
        assertThat(order.path("customer_id").asLong()).isEqualTo(customer.getId());
        assertThat(order.path("total_amount").decimalValue()).isEqualByComparingTo("25.00");
        assertThat(snapshot.path("recipient_name").asText()).isEqualTo("Nguyễn Minh An");
        assertThat(snapshot.path("recipient_phone").asText()).isEqualTo("+84901234567");
        assertThat(snapshot.path("address").asText()).isEqualTo("12 Phố Mới\nPhường Cầu Giấy, Hà Nội");
        assertThat(snapshot.path("note").asText()).isEqualTo("Gọi trước khi giao");
        assertThat(Duration.between(Instant.parse(order.path("created_at").asText()),
                Instant.parse(order.path("reservation_expires_at").asText()))).isEqualTo(Duration.ofMinutes(15));
        assertStock(first, 3, 2);
        assertThat(jdbc.queryForMap("""
                SELECT recipient_name,
                       recipient_phone,
                       delivery_address,
                       delivery_note
                FROM orders
                WHERE id = ?
                """, id)).containsEntry("recipient_name", "Nguyễn Minh An")
                .containsEntry("recipient_phone", "+84901234567")
                .containsEntry("delivery_address", snapshot.path("address").asText())
                .containsEntry("delivery_note", "Gọi trước khi giao");
        assertThat(detail(id, customer).path("delivery")).isEqualTo(snapshot);
        mvc.perform(get("/api/v1/orders/my").header("Authorization", bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].delivery.recipient_phone").value("+84901234567"));
    }

    /** Ghi chú bỏ qua, null, rỗng hoặc toàn khoảng trắng đều được lưu là null. */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void optionalNoteIsNormalized(String note) throws Exception {
        var delivery = new LinkedHashMap<String, Object>(deliveryPayload());
        delivery.put("note", note);
        JsonNode order = create(payload(delivery));
        assertThat(order.path("delivery").path("note").isNull()).isTrue();
        assertThat(detail(order.path("id").asLong(), customer).path("delivery").path("note").isNull()).isTrue();
    }

    /** Từng trường sai trả 400 và không tạo order/items/reservation/movement, dù mặt hàng hợp lệ. */
    @ParameterizedTest
    @MethodSource("invalidDelivery")
    void invalidDeliveryNeverChangesStock(Object delivery) throws Exception {
        mvc.perform(post("/api/v1/orders").header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload(delivery))))
                .andExpect(status().isBadRequest());
        assertNoOrderWrites();
        assertStock(first, 5, 0);
        assertStock(second, 5, 0);
    }

    /** Giới hạn DTO khớp schema, gồm số quốc tế có 15 chữ số và ghi chú 1.000 ký tự. */
    @Test
    void maximumLengthsAreAccepted() throws Exception {
        var delivery = Map.of("recipient_name", "N".repeat(150), "recipient_phone", "+123456789012345",
                "address", "A".repeat(500), "note", "G".repeat(1000));
        JsonNode order = create(payload(delivery));
        assertThat(detail(order.path("id").asLong(), customer).path("delivery")).isEqualTo(order.path("delivery"));
    }

    /** Gọi trực tiếp service cũng phải kiểm tra người nhận, không phụ thuộc riêng @Valid tại controller. */
    @Test
    void serviceRejectsMissingDelivery() {
        var request = new CreateOrderRequest(warehouse.getId(), List.of(new CreateOrderRequest.Item(first.getId(), 1)), null);
        assertThatThrownBy(() -> orders.createOrder(request, customer.getId())).isInstanceOf(BadRequestException.class);
        assertNoOrderWrites();
        assertStock(first, 5, 0);
    }

    /** Địa chỉ vẫn được bảo vệ bởi quyền chủ đơn và phạm vi kho, không lộ qua khách khác hoặc staff ngoài kho. */
    @Test
    void deliveryIsVisibleOnlyToOwnerAndAuthorizedOperators() throws Exception {
        JsonNode order = create(payload(deliveryPayload()));
        long id = order.path("id").asLong();
        User other = user("CUSTOMER");
        User staff = user("WAREHOUSE_STAFF");
        User manager = user("MANAGER");
        mvc.perform(get("/api/v1/orders/{id}", id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/orders/{id}", id).header("Authorization", bearer(other)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/orders/my").header("Authorization", bearer(other)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total_elements").value(0));
        mvc.perform(get("/api/v1/orders/{id}", id).header("Authorization", bearer(staff)))
                .andExpect(status().isForbidden());
        jdbc.update("""
                INSERT INTO warehouse_staff_assignments (user_id, warehouse_id)
                VALUES (?, ?)
                """, staff.getId(), warehouse.getId());
        for (User actor : List.of(customer, staff, manager, admin)) {
            assertThat(detail(id, actor).path("delivery")).isEqualTo(order.path("delivery"));
        }
        // Danh sách vận hành chỉ trả thông tin tối thiểu; địa chỉ lấy qua chi tiết có kiểm tra quyền.
        mvc.perform(get("/api/v1/orders").header("Authorization", bearer(staff)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].delivery").doesNotExist());
    }

    /** Đổi hồ sơ và đi hết fulfillment không làm đổi người nhận hoặc trừ kho lần hai. */
    @Test
    void snapshotSurvivesProfileChangeAndFulfillment() throws Exception {
        JsonNode order = create(payload(deliveryPayload()));
        long id = order.path("id").asLong();
        var snapshot = orders.getOrder(id, customer.getId()).delivery();
        jdbc.update("""
                UPDATE users
                SET full_name = ?
                WHERE id = ?
                """, "Tên hồ sơ đã đổi", customer.getId());
        assertThat(orders.confirmPaymentSimulation(id, customer.getId()).delivery()).isEqualTo(snapshot);
        assertThat(orders.packOrder(id, admin.getId()).delivery()).isEqualTo(snapshot);
        assertThat(orders.shipOrder(id, null, admin.getId()).delivery()).isEqualTo(snapshot);
        assertThat(orders.deliverOrder(id, admin.getId()).delivery()).isEqualTo(snapshot);
        assertStock(first, 3, 0);
        assertThat(orders.returnOrder(id, admin.getId()).delivery()).isEqualTo(snapshot);
        assertStock(first, 5, 0);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM inventory_movements
                WHERE reference_type = 'ORDER'
                  AND reference_id = ?
                  AND type = 'RETURN_RESTOCK'
                """, Long.class, id)).isEqualTo(1);
    }

    /** Reserve mặt hàng thứ hai thất bại phải xóa cả đơn có địa chỉ, mặt hàng và ledger của lần thử. */
    @Test
    void insufficientStockRollsBackDeliveryAndAllItems() throws Exception {
        var body = payload(deliveryPayload());
        body.put("items", List.of(Map.of("product_id", first.getId(), "quantity", 2),
                Map.of("product_id", second.getId(), "quantity", 6)));
        mvc.perform(post("/api/v1/orders").header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isConflict());
        assertNoOrderWrites();
        assertStock(first, 5, 0);
        assertStock(second, 5, 0);
    }

    /** Hàng lịch sử không có bản chụp vẫn đọc, thanh toán và hủy đúng như trước V15. */
    @Test
    void legacyOrderWithoutDeliveryStillWorks() throws Exception {
        JsonNode order = create(payload(deliveryPayload()));
        long id = order.path("id").asLong();
        jdbc.update("""
                UPDATE orders
                SET recipient_name = NULL,
                    recipient_phone = NULL,
                    delivery_address = NULL,
                    delivery_note = NULL
                WHERE id = ?
                """, id);
        assertThat(detail(id, customer).path("delivery").isNull()).isTrue();
        assertThat(orders.confirmPaymentSimulation(id, customer.getId()).delivery()).isNull();
        orders.cancelOrder(id, admin.getId());
        assertStock(first, 5, 0);
    }

    /** Các ca độc lập gồm thiếu đối tượng, trường trống, quá dài và định dạng điện thoại sai. */
    private static Stream<Arguments> invalidDelivery() {
        return Stream.of(
                Arguments.of((Object) null), Arguments.of(Map.of()),
                bad("recipient_name", null), bad("recipient_name", "   "), bad("recipient_name", "N".repeat(151)),
                bad("recipient_phone", null), bad("recipient_phone", ""), bad("recipient_phone", "abc0901234567"),
                bad("recipient_phone", "1234567"), bad("recipient_phone", "1234567890123456"),
                bad("recipient_phone", "++84901234567"), bad("recipient_phone", "０９０１２３４５６７"),
                bad("address", null), bad("address", "  \n "), bad("address", "A".repeat(501)),
                bad("note", "G".repeat(1001)));
    }

    /** Chỉ làm sai một trường để tránh lỗi khác che mất điều kiện cần kiểm thử. */
    private static Arguments bad(String field, Object value) {
        var delivery = new LinkedHashMap<String, Object>(deliveryPayload());
        delivery.put(field, value);
        return Arguments.of(delivery);
    }

    /** Payload có mặt hàng hợp lệ; cho phép delivery=null để kiểm chứng validation lồng nhau. */
    private LinkedHashMap<String, Object> payload(Object delivery) {
        var body = new LinkedHashMap<String, Object>();
        body.put("warehouse_id", warehouse.getId());
        body.put("items", List.of(Map.of("product_id", first.getId(), "quantity", 2)));
        body.put("delivery", delivery);
        return body;
    }

    /** Tạo qua API/JWT và đọc UTF-8 để kiểm chứng nguyên vẹn dấu tiếng Việt. */
    private JsonNode create(Map<String, Object> body) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/orders").header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** Đọc chi tiết qua cùng cơ chế kiểm tra quyền như trình duyệt. */
    private JsonNode detail(long id, User actor) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/orders/{id}", id).header("Authorization", bearer(actor)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** Tạo tài khoản riêng với role trong DB; không dựa vào role tự khai báo trong request. */
    private User user(String role) {
        return users.save(new User(UUID.randomUUID() + "@checkout.test", "unused", "Khách kiểm thử",
                roles.findByName(role).orElseThrow()));
    }

    /** Token chỉ dùng trong request test, không ghi token ra log. */
    private String bearer(User actor) {
        return "Bearer " + jwt.generateToken(actor);
    }

    /** Đối chiếu trạng thái tồn đã commit sau khi transaction API hoàn tất. */
    private void assertStock(Product product, int available, int reserved) {
        var inventory = inventories.findByProductIdAndWarehouseId(product.getId(), warehouse.getId()).orElseThrow();
        assertThat(inventory.getAvailableQuantity()).isEqualTo(available);
        assertThat(inventory.getReservedQuantity()).isEqualTo(reserved);
        assertThat(inventory.getPhysicalQuantity()).isEqualTo(available + reserved);
    }

    /** Kiểm tra không còn bản ghi của checkout lỗi, không xóa ledger để làm test xanh. */
    private void assertNoOrderWrites() {
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM orders
                WHERE customer_id = ?
                """, Long.class, customer.getId())).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM order_items oi
                JOIN orders o ON o.id = oi.order_id
                WHERE o.customer_id = ?
                """, Long.class, customer.getId())).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM inventory_movements
                WHERE performed_by = ?
                  AND type = 'RESERVATION_HOLD'
                """, Long.class, customer.getId())).isZero();
    }
}
