package com.stockflow.order;

import static com.stockflow.order.support.CheckoutTestData.orderRequest;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.stockflow.common.exception.ConflictException;
import com.stockflow.inventory.dto.StockInRequest;
import com.stockflow.inventory.service.InventoryService;
import com.stockflow.order.domain.OrderStatus;
import com.stockflow.order.dto.CreateOrderRequest;
import com.stockflow.order.dto.OrderResponse;
import com.stockflow.order.dto.ShipOrderRequest;
import com.stockflow.order.service.OrderService;
import com.stockflow.user.domain.User;
import com.stockflow.user.repository.RoleRepository;
import com.stockflow.user.repository.UserRepository;
import com.stockflow.warehouse.domain.Warehouse;
import com.stockflow.warehouse.domain.WarehouseStatus;
import com.stockflow.warehouse.repository.WarehouseRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Kiểm chứng fulfillment với JWT/Flyway/transaction thật, bao gồm rollback và cạnh tranh nhiều thread.
 * Không bọc test trong transaction để các worker đọc dữ liệu đã commit; mỗi fixture có ID/email riêng.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:stockflow_fulfillment;MODE=PostgreSQL;"
                + "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FulfillmentIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JwtTokenProvider jwt;
    @Autowired private CategoryRepository categories;
    @Autowired private ProductRepository products;
    @Autowired private WarehouseRepository warehouses;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private InventoryService inventoryService;
    @Autowired private OrderService orders;
    @Autowired private JdbcTemplate jdbc;

    private Product firstProduct;
    private Product secondProduct;
    private Warehouse warehouse;
    private User customer;
    private User otherCustomer;
    private User admin;
    private User manager;
    private User staff;
    private User otherWarehouseStaff;
    private User unassignedStaff;
    private OrderResponse order;

    /** Hai mặt hàng được gửi ngược thứ tự inventory ID để kiểm tra hoàn kho luôn theo thứ tự khóa chuẩn. */
    @BeforeEach
    void setUp() {
        String key = UUID.randomUUID().toString();
        Category category = categories.save(new Category("Danh mục giao nhận " + key, key));
        firstProduct = products.save(new Product(
                category, key + "-1", "Mặt hàng thứ nhất", new BigDecimal("12.50"), ProductStatus.ACTIVE));
        secondProduct = products.save(new Product(
                category, key + "-2", "Mặt hàng thứ hai", new BigDecimal("20.00"), ProductStatus.ACTIVE));
        warehouse = warehouses.save(new Warehouse(key, "Kho giao nhận", "Địa chỉ thử", WarehouseStatus.ACTIVE));
        Warehouse otherWarehouse = warehouses.save(new Warehouse(
                key + "-OTHER", "Kho ngoài phân công", "Địa chỉ khác", WarehouseStatus.ACTIVE));
        customer = user("CUSTOMER");
        otherCustomer = user("CUSTOMER");
        admin = user("ADMIN");
        manager = user("MANAGER");
        staff = user("WAREHOUSE_STAFF");
        otherWarehouseStaff = user("WAREHOUSE_STAFF");
        unassignedStaff = user("WAREHOUSE_STAFF");
        assign(staff, warehouse);
        assign(otherWarehouseStaff, otherWarehouse);
        inventoryService.stockIn(new StockInRequest(
                firstProduct.getId(), warehouse.getId(), 5, "Nhập mặt hàng thứ nhất"), admin.getId());
        inventoryService.stockIn(new StockInRequest(
                secondProduct.getId(), warehouse.getId(), 7, "Nhập mặt hàng thứ hai"), admin.getId());
        order = orders.createOrder(orderRequest(warehouse.getId(), List.of(
                new CreateOrderRequest.Item(secondProduct.getId(), 3),
                new CreateOrderRequest.Item(firstProduct.getId(), 2))), customer.getId());
    }

    /** Cả ADMIN/MANAGER/staff đúng kho đi hết vòng đời; pack/ship/deliver không trừ kho lần hai. */
    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "MANAGER", "WAREHOUSE_STAFF"})
    void completeLifecycleRestocksOnceAndExposesShipment(String role) throws Exception {
        User actor = switch (role) {
            case "ADMIN" -> admin;
            case "MANAGER" -> manager;
            default -> staff;
        };
        assertThat(order.shipment()).isNull();
        assertStock(firstProduct, 3, 2);
        assertStock(secondProduct, 4, 3);
        orders.confirmPaymentSimulation(order.id(), customer.getId());
        assertStock(firstProduct, 3, 0);
        assertStock(secondProduct, 4, 0);
        List<Map<String, Object>> dispatchedStock = inventorySnapshot();
        long ledgerCount = orderMovementCount();
        JsonNode packed = action("pack", actor, null);
        assertThat(packed.path("status").asText()).isEqualTo("PACKED");
        assertThat(packed.path("shipment").path("status").asText()).isEqualTo("PREPARING");
        assertThat(packed.path("shipment").path("tracking_code").asText()).startsWith("SF-TRACK-");
        assertThat(packed.path("shipment").path("shipped_at").isNull()).isTrue();

        String code = "SF-TRACK-TEST-" + UUID.randomUUID();
        JsonNode shipped = action("ship", actor, Map.of("tracking_code", code));
        assertThat(shipped.path("status").asText()).isEqualTo("SHIPPED");
        assertThat(shipped.path("shipment").path("status").asText()).isEqualTo("SHIPPED");
        assertThat(shipped.path("shipment").path("tracking_code").asText()).isEqualTo(code);
        String shippedAt = shipped.path("shipment").path("shipped_at").asText();
        assertThat(Instant.parse(shippedAt)).isBeforeOrEqualTo(Instant.now());
        assertThat(shipped.path("shipment").path("delivered_at").isNull()).isTrue();
        JsonNode delivered = action("deliver", actor, null);
        assertThat(delivered.path("status").asText()).isEqualTo("DELIVERED");
        assertThat(delivered.path("shipment").path("status").asText()).isEqualTo("DELIVERED");
        assertThat(delivered.path("shipment").path("shipped_at").asText()).isEqualTo(shippedAt);
        Instant deliveredAt = Instant.parse(delivered.path("shipment").path("delivered_at").asText());
        assertThat(deliveredAt).isAfterOrEqualTo(Instant.parse(shippedAt));
        assertThat(inventorySnapshot()).isEqualTo(dispatchedStock);
        assertThat(orderMovementCount()).isEqualTo(ledgerCount);

        JsonNode returned = action("return", actor, null);
        assertThat(returned.path("status").asText()).isEqualTo("RETURNED");
        assertThat(returned.path("shipment").path("status").asText()).isEqualTo("RETURNED");
        assertThat(returned.path("shipment").path("tracking_code").asText()).isEqualTo(code);
        assertStock(firstProduct, 5, 0);
        assertStock(secondProduct, 7, 0);
        assertThat(paymentStatus()).isEqualTo("REFUNDED");
        assertReturns(actor);
        List<Map<String, Object>> returnedStock = inventorySnapshot();
        action("return", actor, null);
        assertThat(inventorySnapshot()).isEqualTo(returnedStock);
        assertThat(orderMovementCount()).isEqualTo(ledgerCount + 2);
        assertThat(shipmentCount()).isEqualTo(1);

        mvc.perform(get("/api/v1/orders/{id}", order.id()).header("Authorization", bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipment.tracking_code").value(code))
                .andExpect(jsonPath("$.shipment.status").value("RETURNED"));
        mvc.perform(get("/api/v1/orders/my").header("Authorization", bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].shipment.tracking_code").value(code));
        mvc.perform(get("/api/v1/orders/{id}", order.id()).header("Authorization", bearer(otherCustomer)))
                .andExpect(status().isForbidden());
    }

    /** Bỏ request, object rỗng hoặc null tracking đều dùng mã tự cấp và không đổi số tồn. */
    @ParameterizedTest
    @ValueSource(strings = {"NO_BODY", "{}", "{\"tracking_code\":null}"})
    void shipWithoutTrackingCodeUsesGeneratedCode(String body) throws Exception {
        advance(OrderStatus.PACKED);
        String preparedCode = orders.getOrder(order.id(), customer.getId()).shipment().trackingCode();
        JsonNode result = rawAction("ship", staff, body.equals("NO_BODY") ? null : body, 200);
        assertThat(result.path("shipment").path("tracking_code").asText()).isEqualTo(preparedCode);
        assertThat(preparedCode).matches("SF-TRACK-[A-F0-9-]{36}");
        assertStock(firstProduct, 3, 0);
        assertStock(secondProduct, 4, 0);
    }

    /** Mọi bước gọi lặp tại trạng thái đích giữ nguyên timestamp/version/ledger và chỉ có một shipment. */
    @ParameterizedTest
    @ValueSource(strings = {"pack", "ship", "deliver", "return"})
    void repeatedActionDoesNotRepeatWrites(String operation) throws Exception {
        advance(sourceFor(operation));
        action(operation, staff, null);
        List<Map<String, Object>> before = stateSnapshot();
        List<Map<String, Object>> stockBefore = inventorySnapshot();
        long movementsBefore = orderMovementCount();
        action(operation, staff, null);
        assertThat(stateSnapshot()).isEqualTo(before);
        assertThat(inventorySnapshot()).isEqualTo(stockBefore);
        assertThat(orderMovementCount()).isEqualTo(movementsBefore);
        assertThat(shipmentCount()).isEqualTo(1);
    }

    /** Các trạng thái nguồn sai đều nhận 409 và không gây tác dụng phụ lên đơn/kho/ledger. */
    @ParameterizedTest
    @CsvSource({
            "PENDING,pack", "PENDING,ship", "PENDING,deliver", "PENDING,return",
            "CONFIRMED,ship", "CONFIRMED,deliver", "CONFIRMED,return",
            "PACKED,deliver", "PACKED,return", "SHIPPED,pack", "SHIPPED,return",
            "DELIVERED,pack", "DELIVERED,ship", "RETURNED,pack", "RETURNED,ship", "RETURNED,deliver",
            "CANCELLED,pack", "CANCELLED,ship", "CANCELLED,deliver", "CANCELLED,return",
            "EXPIRED,pack", "EXPIRED,ship", "EXPIRED,deliver", "EXPIRED,return"
    })
    void invalidTransitionsAreRejected(OrderStatus state, String operation) throws Exception {
        advance(state);
        List<Map<String, Object>> before = stateSnapshot();
        List<Map<String, Object>> stockBefore = inventorySnapshot();
        long movementsBefore = orderMovementCount();
        JsonNode error = rawAction(operation, admin, null, 409);
        assertThat(error.path("message").asText()).contains("trạng thái");
        assertThat(stateSnapshot()).isEqualTo(before);
        assertThat(inventorySnapshot()).isEqualTo(stockBefore);
        assertThat(orderMovementCount()).isEqualTo(movementsBefore);
    }

    /** Khách, staff kho khác và staff chưa phân công không được gọi bất kỳ API fulfillment nào. */
    @ParameterizedTest
    @CsvSource({
            "pack,CUSTOMER", "ship,CUSTOMER", "deliver,CUSTOMER", "return,CUSTOMER",
            "pack,OTHER_STAFF", "ship,OTHER_STAFF", "deliver,OTHER_STAFF", "return,OTHER_STAFF",
            "pack,UNASSIGNED", "ship,UNASSIGNED", "deliver,UNASSIGNED", "return,UNASSIGNED"
    })
    void roleAndWarehouseScopeCannotBeBypassed(String operation, String role) throws Exception {
        advance(sourceFor(operation));
        User actor = switch (role) {
            case "CUSTOMER" -> customer;
            case "OTHER_STAFF" -> otherWarehouseStaff;
            default -> unassignedStaff;
        };
        List<Map<String, Object>> before = stateSnapshot();
        long movementsBefore = orderMovementCount();
        rawAction(operation, actor, null, 403);
        assertThat(stateSnapshot()).isEqualTo(before);
        assertThat(orderMovementCount()).isEqualTo(movementsBefore);
    }

    /** Không JWT bị chặn 401 trên cả bốn API mới. */
    @ParameterizedTest
    @ValueSource(strings = {"pack", "ship", "deliver", "return"})
    void anonymousCannotOperate(String operation) throws Exception {
        mvc.perform(post("/api/v1/orders/{id}/{operation}", order.id(), operation))
                .andExpect(status().isUnauthorized());
    }

    /** Sau khi ship, kể cả đã giao hoặc trả hàng, cancel không được hoàn kho thêm lần nữa. */
    @ParameterizedTest
    @ValueSource(strings = {"SHIPPED", "DELIVERED", "RETURNED"})
    void cancellationIsBlockedAfterShipment(OrderStatus state) throws Exception {
        advance(state);
        List<Map<String, Object>> before = inventorySnapshot();
        long movementsBefore = orderMovementCount();
        rawAction("cancel", manager, null, 409);
        assertThat(inventorySnapshot()).isEqualTo(before);
        assertThat(orderMovementCount()).isEqualTo(movementsBefore);
    }

    /** PACKED vẫn hủy được trước ship; vận đơn PREPARING lưu lại và không thể xuất giao sau hủy. */
    @Test
    void packedOrderCanBeCancelledAndCannotBeShipped() throws Exception {
        advance(OrderStatus.PACKED);
        JsonNode cancelled = action("cancel", manager, null);
        assertThat(cancelled.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.path("shipment").path("status").asText()).isEqualTo("PREPARING");
        assertStock(firstProduct, 5, 0);
        assertStock(secondProduct, 7, 0);
        assertThat(paymentStatus()).isEqualTo("REFUNDED");
        assertReturns(manager);
        rawAction("ship", staff, null, 409);
    }

    /** Mã rỗng, ký tự không hợp lệ hoặc vượt VARCHAR(100) nhận 400 trước khi thay đổi vận đơn. */
    @Test
    void invalidTrackingCodesReturnBadRequest() throws Exception {
        advance(OrderStatus.PACKED);
        List<Map<String, Object>> before = stateSnapshot();
        for (String code : List.of("", " ", "BAD CODE", "CODE/123", "X".repeat(101))) {
            rawAction("ship", staff, json.writeValueAsString(Map.of("tracking_code", code)), 400);
        }
        assertThat(stateSnapshot()).isEqualTo(before);
    }

    /** Body JSON hỏng hoặc sai kiểu nhận 400, không bị che thành lỗi hệ thống 500. */
    @Test
    void malformedShipBodyReturnsBadRequest() throws Exception {
        advance(OrderStatus.PACKED);
        for (String body : List.of("{\"tracking_code\":", "[]", "{\"tracking_code\":{}}")) {
            rawAction("ship", staff, body, 400);
        }
        assertThat(orders.getOrder(order.id(), customer.getId()).status()).isEqualTo(OrderStatus.PACKED);
    }

    /** Chấp nhận trackingCode như alias, nhưng không cho đổi mã sau khi vận đơn đã SHIPPED. */
    @Test
    void camelCaseTrackingAliasAndImmutableShippedCode() throws Exception {
        advance(OrderStatus.PACKED);
        String code = "CUSTOM-" + UUID.randomUUID();
        action("ship", staff, Map.of("trackingCode", code));
        action("ship", staff, Map.of("tracking_code", code));
        rawAction("ship", staff, json.writeValueAsString(Map.of("tracking_code", "DIFFERENT")), 409);
        assertThat(orders.getOrder(order.id(), customer.getId()).shipment().trackingCode()).isEqualTo(code);
    }

    /** Mã đã thuộc đơn khác nhận 409; trạng thái đơn/vận đơn mới phải giữ PACKED/PREPARING. */
    @Test
    void duplicateTrackingCodeDoesNotPartiallyShip() throws Exception {
        advance(OrderStatus.PACKED);
        OrderResponse second = createSecondPaidPackedOrder();
        String code = "SHARED-" + UUID.randomUUID();
        orders.shipOrder(second.id(), new ShipOrderRequest(code), admin.getId());
        List<Map<String, Object>> before = stateSnapshot();
        rawAction("ship", staff, json.writeValueAsString(Map.of("tracking_code", code)), 409);
        assertThat(stateSnapshot()).isEqualTo(before);
    }

    /** Thiếu shipment không được tự tạo lại khi ship/deliver/return, tránh che dữ liệu không nhất quán. */
    @ParameterizedTest
    @ValueSource(strings = {"ship", "deliver", "return"})
    void missingShipmentIsConflict(String operation) throws Exception {
        advance(sourceFor(operation));
        jdbc.update("""
                DELETE FROM shipments
                WHERE order_id = ?
                """, order.id());
        rawAction(operation, admin, null, 409);
        assertThat(orderMovementCount()).isEqualTo(4);
    }

    /** Trạng thái CONFIRMED không đủ nếu payment thiếu PAID; pack phải kiểm tra bất biến nghiệp vụ. */
    @Test
    void packingRequiresPaidPayment() throws Exception {
        advance(OrderStatus.CONFIRMED);
        jdbc.update("""
                UPDATE payments
                SET status = 'FAILED'
                WHERE order_id = ?
                """, order.id());
        rawAction("pack", admin, null, 409);
        assertThat(shipmentCount()).isZero();
    }

    /** Nếu hoàn mặt hàng thứ hai vượt giới hạn số nguyên, mặt hàng đầu/ledger/trạng thái/tiền đều rollback. */
    @Test
    void failedSecondRestockRollsBackEntireReturn() throws Exception {
        advance(OrderStatus.DELIVERED);
        jdbc.update("""
                UPDATE inventories
                SET available_quantity = 2147483647
                WHERE product_id = ?
                  AND warehouse_id = ?
                """, secondProduct.getId(), warehouse.getId());
        List<Map<String, Object>> before = inventorySnapshot();
        rawAction("return", staff, null, 409);
        assertThat(inventorySnapshot()).isEqualTo(before);
        assertThat(orderMovementCount()).isEqualTo(4);
        assertThat(orders.getOrder(order.id(), customer.getId()).status()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(orders.getOrder(order.id(), customer.getId()).shipment().status().name()).isEqualTo("DELIVERED");
        assertThat(paymentStatus()).isEqualTo("PAID");
    }

    /** Nhiều pack cùng một order tạo đúng một shipment, không ghi thêm biến động tồn kho. */
    @Test
    void concurrentPackCreatesOneShipment() throws Exception {
        advance(OrderStatus.CONFIRMED);
        List<Callable<OrderStatus>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tasks.add(() -> orders.packOrder(order.id(), staff.getId()).status());
        }
        assertThat(runTogether(tasks)).containsOnly(OrderStatus.PACKED);
        assertThat(shipmentCount()).isEqualTo(1);
        assertThat(orderMovementCount()).isEqualTo(4);
    }

    /** Nhiều return cùng một order chỉ hoàn mỗi mặt hàng một lần và một refund mô phỏng. */
    @Test
    void concurrentReturnRestocksOnlyOnce() throws Exception {
        advance(OrderStatus.DELIVERED);
        List<Callable<OrderStatus>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tasks.add(() -> orders.returnOrder(order.id(), staff.getId()).status());
        }
        assertThat(runTogether(tasks)).containsOnly(OrderStatus.RETURNED);
        assertStock(firstProduct, 5, 0);
        assertStock(secondProduct, 7, 0);
        assertReturns(staff);
        assertThat(orderMovementCount()).isEqualTo(6);
        assertThat(paymentStatus()).isEqualTo("REFUNDED");
    }

    /** Ship tranh với cancel được khóa theo order: hoặc giao và không hoàn kho, hoặc hủy và không giao. */
    @Test
    void shipmentAndCancellationRaceRemainConsistent() throws Exception {
        advance(OrderStatus.PACKED);
        List<Boolean> result = runTogether(List.of(
                () -> tryTransition(() -> orders.shipOrder(order.id(), null, staff.getId())),
                () -> tryTransition(() -> orders.cancelOrder(order.id(), manager.getId()))));
        assertThat(result.stream().filter(Boolean::booleanValue).count()).isEqualTo(1);
        OrderResponse current = orders.getOrder(order.id(), customer.getId());
        if (current.status() == OrderStatus.SHIPPED) {
            assertThat(current.shipment().status().name()).isEqualTo("SHIPPED");
            assertStock(firstProduct, 3, 0);
            assertStock(secondProduct, 4, 0);
            assertThat(paymentStatus()).isEqualTo("PAID");
            assertThat(orderMovementCount()).isEqualTo(4);
        } else {
            assertThat(current.status()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(current.shipment().status().name()).isEqualTo("PREPARING");
            assertStock(firstProduct, 5, 0);
            assertStock(secondProduct, 7, 0);
            assertThat(paymentStatus()).isEqualTo("REFUNDED");
            assertThat(orderMovementCount()).isEqualTo(6);
        }
    }

    /** Constraint UNIQUE bảo vệ cả lúc hai đơn kiểm tra mã cùng lúc; đơn thua phải rollback về PACKED. */
    @Test
    void concurrentTrackingCodeCollisionHasOneWinner() throws Exception {
        advance(OrderStatus.PACKED);
        OrderResponse second = createSecondPaidPackedOrder();
        String code = "RACE-" + UUID.randomUUID();
        List<Boolean> result = runTogether(List.of(
                () -> tryTransition(() -> orders.shipOrder(order.id(), new ShipOrderRequest(code), staff.getId())),
                () -> tryTransition(() -> orders.shipOrder(second.id(), new ShipOrderRequest(code), admin.getId()))));
        assertThat(result.stream().filter(Boolean::booleanValue).count()).isEqualTo(1);
        List<OrderStatus> states = List.of(
                orders.getOrder(order.id(), customer.getId()).status(),
                orders.getOrder(second.id(), customer.getId()).status());
        assertThat(states).containsExactlyInAnyOrder(OrderStatus.PACKED, OrderStatus.SHIPPED);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM shipments
                WHERE tracking_code = ?
                """, Long.class, code)).isEqualTo(1);
    }

    /** Tạo actor/role thật cho JWT, không mock authority hoặc quyền kho. */
    private User user(String role) {
        return users.save(new User(
                UUID.randomUUID() + "@example.com", "hash-kiểm-thử", "Người kiểm thử giao nhận",
                roles.findByName(role).orElseThrow()));
    }

    /** Phân công kho bằng bảng nền tảng hiện có. */
    private void assign(User user, Warehouse warehouse) {
        jdbc.update("""
                INSERT INTO warehouse_staff_assignments (user_id, warehouse_id)
                VALUES (?, ?)
                """, user.getId(), warehouse.getId());
    }

    /** Chọn trạng thái nguồn đúng cho từng API vận hành. */
    private OrderStatus sourceFor(String operation) {
        return switch (operation) {
            case "pack" -> OrderStatus.CONFIRMED;
            case "ship" -> OrderStatus.PACKED;
            case "deliver" -> OrderStatus.SHIPPED;
            default -> OrderStatus.DELIVERED;
        };
    }

    /** Đi tới trạng thái bằng service thật; chỉ deadline test được đẩy về quá khứ bằng SQL. */
    private void advance(OrderStatus state) {
        if (state == OrderStatus.PENDING) {
            return;
        }
        if (state == OrderStatus.CANCELLED) {
            orders.cancelOrder(order.id(), manager.getId());
            return;
        }
        if (state == OrderStatus.EXPIRED) {
            jdbc.update("""
                    UPDATE orders
                    SET reservation_expires_at = ?
                    WHERE id = ?
                    """, Timestamp.from(Instant.now().minusSeconds(60)), order.id());
            orders.expireOrder(order.id());
            return;
        }
        orders.confirmPaymentSimulation(order.id(), customer.getId());
        if (state == OrderStatus.CONFIRMED) {
            return;
        }
        orders.packOrder(order.id(), admin.getId());
        if (state == OrderStatus.PACKED) {
            return;
        }
        orders.shipOrder(order.id(), null, admin.getId());
        if (state == OrderStatus.SHIPPED) {
            return;
        }
        orders.deliverOrder(order.id(), admin.getId());
        if (state == OrderStatus.RETURNED) {
            orders.returnOrder(order.id(), admin.getId());
        }
    }

    /** Đơn thứ hai dùng lượng nhỏ hơn stock còn lại, phục vụ kiểm chứng mã vận đơn duy nhất. */
    private OrderResponse createSecondPaidPackedOrder() {
        OrderResponse second = orders.createOrder(orderRequest(warehouse.getId(), List.of(
                new CreateOrderRequest.Item(firstProduct.getId(), 1))), customer.getId());
        orders.confirmPaymentSimulation(second.id(), customer.getId());
        return orders.packOrder(second.id(), admin.getId());
    }

    /** Header đi qua filter chain JWT thật. */
    private String bearer(User user) {
        return "Bearer " + jwt.generateToken(user);
    }

    /** Gửi thao tác thành công với payload tùy chọn. */
    private JsonNode action(String operation, User user, Object body) throws Exception {
        return rawAction(operation, user, body == null ? null : json.writeValueAsString(body), 200);
    }

    /** Giữ HTTP status thật cho các kiểm chứng 400/403/409. */
    private JsonNode rawAction(String operation, User user, String body, int expected) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/orders/{id}/{operation}", order.id(), operation)
                .header("Authorization", bearer(user));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return json.readTree(mvc.perform(request).andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString());
    }

    /** Đọc tồn đã commit, không so sánh entity cũ trong persistence context. */
    private void assertStock(Product product, int available, int reserved) {
        Map<String, Object> stock = jdbc.queryForMap("""
                SELECT available_quantity,
                       reserved_quantity
                FROM inventories
                WHERE product_id = ?
                  AND warehouse_id = ?
                """, product.getId(), warehouse.getId());
        assertThat(stock.get("available_quantity")).isEqualTo(available);
        assertThat(stock.get("reserved_quantity")).isEqualTo(reserved);
    }

    /** Kiểm chứng đúng hai movement hoàn hàng, actor/snapshot/tham chiếu và thứ tự inventory ID. */
    private void assertReturns(User actor) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT m.inventory_id,
                       i.product_id,
                       m.quantity,
                       m.balance_before,
                       m.balance_after,
                       m.performed_by
                FROM inventory_movements m
                JOIN inventories i ON i.id = m.inventory_id
                WHERE m.reference_type = 'ORDER'
                  AND m.reference_id = ?
                  AND m.type = 'RETURN_RESTOCK'
                ORDER BY m.id
                """, order.id());
        assertThat(rows).hasSize(2);
        assertThat(((Number) rows.get(0).get("product_id")).longValue()).isEqualTo(firstProduct.getId());
        assertThat(((Number) rows.get(1).get("product_id")).longValue()).isEqualTo(secondProduct.getId());
        assertThat(rows.get(0)).containsEntry("quantity", 2)
                .containsEntry("balance_before", 3).containsEntry("balance_after", 5);
        assertThat(rows.get(1)).containsEntry("quantity", 3)
                .containsEntry("balance_before", 4).containsEntry("balance_after", 7);
        rows.forEach(row -> assertThat(((Number) row.get("performed_by")).longValue()).isEqualTo(actor.getId()));
    }

    /** Đếm riêng ledger của đơn fixture, không phụ thuộc lịch sử từ các test đã commit trước đó. */
    private long orderMovementCount() {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM inventory_movements
                WHERE reference_type = 'ORDER'
                  AND reference_id = ?
                """, Long.class, order.id());
    }

    /** Đếm shipment duy nhất của đơn fixture. */
    private long shipmentCount() {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM shipments
                WHERE order_id = ?
                """, Long.class, order.id());
    }

    /** Đọc trạng thái hoàn tiền đã commit. */
    private String paymentStatus() {
        return jdbc.queryForObject("""
                SELECT status
                FROM payments
                WHERE order_id = ?
                """, String.class, order.id());
    }

    /** Chụp cả version/timestamp để phát hiện ghi thừa trong các thao tác không ảnh hưởng stock. */
    private List<Map<String, Object>> inventorySnapshot() {
        return jdbc.queryForList("""
                SELECT id,
                       available_quantity,
                       reserved_quantity,
                       version,
                       updated_at
                FROM inventories
                WHERE warehouse_id = ?
                ORDER BY id
                """, warehouse.getId());
    }

    /** Chụp trạng thái đơn/vận đơn/payment cho kiểm chứng rollback và idempotency. */
    private List<Map<String, Object>> stateSnapshot() {
        return jdbc.queryForList("""
                SELECT o.status AS order_status,
                       o.updated_at,
                       s.tracking_code,
                       s.status AS shipment_status,
                       s.shipped_at,
                       s.delivered_at,
                       p.status AS payment_status
                FROM orders o
                LEFT JOIN shipments s ON s.order_id = o.id
                LEFT JOIN payments p ON p.order_id = o.id
                WHERE o.id = ?
                """, order.id());
    }

    /** Cạnh tranh hợp lệ trả false khi bước khác thắng; lỗi hệ thống khác vẫn làm test thất bại. */
    private boolean tryTransition(Callable<OrderResponse> task) throws Exception {
        try {
            task.call();
            return true;
        } catch (ConflictException expected) {
            return false;
        }
    }

    /** Worker xuất phát cùng lúc và có timeout để lỗi khóa treo không bị bỏ qua. */
    private <T> List<T> runTogether(List<Callable<T>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        try {
            for (Callable<T> task : tasks) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Hết thời gian chờ bắt đầu test.");
                    }
                    return task.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<T> result = new ArrayList<>();
            for (Future<T> future : futures) {
                result.add(future.get(30, TimeUnit.SECONDS));
            }
            return result;
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }
}
