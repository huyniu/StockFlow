package com.stockflow.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.stockflow.common.exception.ForbiddenException;
import com.stockflow.inventory.dto.StockInRequest;
import com.stockflow.inventory.service.InventoryService;
import com.stockflow.order.dto.CreateOrderRequest;
import com.stockflow.order.dto.OrderResponse;
import com.stockflow.order.service.OrderQueryService;
import com.stockflow.order.service.OrderService;
import com.stockflow.user.domain.User;
import com.stockflow.user.repository.RoleRepository;
import com.stockflow.user.repository.UserRepository;
import com.stockflow.warehouse.domain.Warehouse;
import com.stockflow.warehouse.domain.WarehouseStatus;
import com.stockflow.warehouse.repository.WarehouseRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kiểm chứng danh sách đơn qua JWT thật, phạm vi kho tại database và phân trang ổn định.
 * Fixture dùng nghiệp vụ nhập/đặt/thanh toán hiện có; rollback test không sửa hoặc xóa ledger.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:stockflow_order_query;MODE=PostgreSQL;"
                + "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderQueryIntegrationTest {

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
    @Autowired private OrderQueryService queries;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;

    private User admin;
    private User manager;
    private User staff;
    private User unassignedStaff;
    private User customer;
    private Warehouse firstWarehouse;
    private Warehouse secondWarehouse;
    private OrderResponse pending;
    private OrderResponse confirmed;
    private OrderResponse otherWarehouseOrder;

    /** Tạo ba đơn ở hai kho, hai chủ đơn và hai trạng thái để phát hiện lọc sai hoặc lộ dữ liệu. */
    @BeforeEach
    void setUp() {
        String key = UUID.randomUUID().toString();
        Category category = categories.save(new Category("Danh mục truy vấn " + key, key));
        Product product = products.save(new Product(
                category, key, "Sản phẩm thử danh sách đơn", new BigDecimal("12.50"), ProductStatus.ACTIVE));
        firstWarehouse = warehouse(key + "-A", "Kho Hà Nội kiểm thử");
        secondWarehouse = warehouse(key + "-B", "Kho Đà Nẵng kiểm thử");
        admin = user("ADMIN");
        manager = user("MANAGER");
        staff = user("WAREHOUSE_STAFF");
        unassignedStaff = user("WAREHOUSE_STAFF");
        customer = user("CUSTOMER");
        User otherCustomer = user("CUSTOMER");
        assign(staff, firstWarehouse);
        inventoryService.stockIn(new StockInRequest(
                product.getId(), firstWarehouse.getId(), 20, "Nhập fixture kho A"), admin.getId());
        inventoryService.stockIn(new StockInRequest(
                product.getId(), secondWarehouse.getId(), 20, "Nhập fixture kho B"), admin.getId());
        pending = create(product, firstWarehouse, customer, 2);
        confirmed = create(product, firstWarehouse, otherCustomer, 1);
        orders.confirmPaymentSimulation(confirmed.id(), otherCustomer.getId());
        otherWarehouseOrder = create(product, secondWarehouse, otherCustomer, 3);
        // JDBC đọc snapshot đã flush, không dựa vào trạng thái entity chưa được ghi xuống database.
        em.flush();
    }

    /** ADMIN và MANAGER thấy cả hai kho, tên kho và tổng số đơn chính xác. */
    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "MANAGER"})
    void managementCanListAllOrders(String role) throws Exception {
        User actor = role.equals("ADMIN") ? admin : manager;
        JsonNode result = list(actor, "");
        assertThat(result.path("total_elements").asLong()).isEqualTo(3);
        assertThat(ids(result)).containsExactly(
                otherWarehouseOrder.id(), confirmed.id(), pending.id());
        assertThat(result.path("content").get(0).path("warehouse_name").asText())
                .isEqualTo(secondWarehouse.getName());
        assertThat(result.path("content").get(0).has("items")).isFalse();
    }

    /** Không chọn kho vẫn chỉ thấy kho được phân công, kể cả metadata tổng số đơn. */
    @Test
    void staffScopeAppliesBeforePaginationAndCount() throws Exception {
        JsonNode result = list(staff, "?size=1");
        assertThat(result.path("total_elements").asLong()).isEqualTo(2);
        assertThat(result.path("total_pages").asInt()).isEqualTo(2);
        assertThat(ids(result)).containsExactly(confirmed.id());
        assertThat(result.path("content").get(0).path("warehouse_id").asLong())
                .isEqualTo(firstWarehouse.getId());
        assertThat(ids(list(staff, "?page=1&size=1"))).containsExactly(pending.id());
    }

    /** Bộ lọc trạng thái và kho giao nhau; staff không lọt đơn cùng trạng thái ở kho khác. */
    @Test
    void warehouseAndStatusFiltersAreCombined() throws Exception {
        JsonNode result = list(manager,
                "?warehouseId=" + firstWarehouse.getId() + "&status=PENDING");
        assertThat(result.path("total_elements").asLong()).isEqualTo(1);
        assertThat(ids(result)).containsExactly(pending.id());
        assertThat(ids(list(staff, "?status=PENDING"))).containsExactly(pending.id());
        assertThat(ids(list(staff,
                "?warehouseId=" + firstWarehouse.getId() + "&status=CONFIRMED")))
                .containsExactly(confirmed.id());
    }

    /** Kho ngoài phân công bị chặn 403 trên cả danh sách lẫn API chi tiết hiện có. */
    @Test
    void staffCannotReadAnotherWarehouse() throws Exception {
        mvc.perform(get("/api/v1/orders")
                        .param("warehouseId", secondWarehouse.getId().toString())
                        .header("Authorization", bearer(staff)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/orders/{id}", otherWarehouseOrder.id())
                        .header("Authorization", bearer(staff)))
                .andExpect(status().isForbidden());
    }

    /** Nhiều phân công mở đúng tập kho, không nhân bản đơn khi tính tổng hoặc phân trang. */
    @Test
    void staffCanReadMultipleAssignedWarehousesWithoutDuplicates() throws Exception {
        assign(staff, secondWarehouse);
        JsonNode result = list(staff, "");
        assertThat(result.path("total_elements").asLong()).isEqualTo(3);
        assertThat(ids(result)).containsExactly(
                otherWarehouseOrder.id(), confirmed.id(), pending.id());
    }

    /** Không có phân công trả trang rỗng; bộ lọc kho tường minh cũng không cấp thêm quyền. */
    @Test
    void unassignedStaffGetsEmptyList() throws Exception {
        JsonNode result = list(unassignedStaff, "");
        assertThat(result.path("total_elements").asLong()).isZero();
        assertThat(ids(result)).isEmpty();
        mvc.perform(get("/api/v1/orders")
                        .param("warehouseId", firstWarehouse.getId().toString())
                        .header("Authorization", bearer(unassignedStaff)))
                .andExpect(status().isForbidden());
    }

    /** Thu hồi phân công có hiệu lực với JWT cũ ngay ở lần đọc tiếp theo. */
    @Test
    void removedAssignmentIsNotCachedInJwt() throws Exception {
        String token = bearer(staff);
        mvc.perform(get("/api/v1/orders").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elements").value(2));
        jdbc.update("""
                DELETE FROM warehouse_staff_assignments
                WHERE user_id = ?
                """, staff.getId());
        mvc.perform(get("/api/v1/orders").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elements").value(0));
        mvc.perform(get("/api/v1/orders/{id}", pending.id()).header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    /** CUSTOMER giữ luồng /my và quyền sở hữu; không được vào danh sách đơn vận hành. */
    @Test
    void customerKeepsOwnOrderAccessOnly() throws Exception {
        mvc.perform(get("/api/v1/orders").header("Authorization", bearer(customer)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/orders/my").header("Authorization", bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(pending.id()));
        mvc.perform(get("/api/v1/orders/{id}", confirmed.id())
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isForbidden());
    }

    /** Request chưa đăng nhập phải bị chặn trước khi truy vấn dữ liệu vận hành. */
    @Test
    void anonymousCannotReadOrders() throws Exception {
        mvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized());
    }

    /** Service cũng kiểm tra role khi được gọi trực tiếp, không chỉ dựa vào annotation controller. */
    @Test
    void directServiceCallCannotBypassCustomerRole() {
        assertThatThrownBy(() -> queries.listOrders(customer.getId(), null, null, 0, 20))
                .isInstanceOf(ForbiddenException.class);
    }

    /** created_at trùng nhau vẫn có thứ tự id cố định, không lặp/mất đơn giữa các trang tĩnh. */
    @Test
    void paginationUsesIdAsTimestampTieBreaker() throws Exception {
        jdbc.update("""
                UPDATE orders
                SET created_at = ?
                """, Timestamp.from(Instant.parse("2026-09-01T08:00:00Z")));
        List<Long> ordered = List.of(otherWarehouseOrder.id(), confirmed.id(), pending.id());
        for (int page = 0; page < ordered.size(); page++) {
            JsonNode result = list(admin, "?page=" + page + "&size=1");
            assertThat(result.path("total_elements").asLong()).isEqualTo(3);
            assertThat(ids(result)).containsExactly(ordered.get(page));
        }
        assertThat(ids(list(admin, "?page=3&size=1"))).isEmpty();
    }

    /** Tham số sai nhận 400 rõ ràng, không bị tự chuẩn hóa thành trang hoặc phạm vi khác. */
    @ParameterizedTest
    @ValueSource(strings = {
            "page=-1", "size=0", "size=101", "warehouseId=0", "warehouseId=-1",
            "status=UNKNOWN", "warehouseId=abc", "page=abc"
    })
    void invalidFiltersReturnBadRequest(String query) throws Exception {
        mvc.perform(get("/api/v1/orders?" + query).header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());
    }

    /** Trạng thái hoặc kho hợp lệ nhưng không có đơn trả 200 cùng trang rỗng. */
    @Test
    void unmatchedFiltersReturnEmptyPage() throws Exception {
        assertThat(ids(list(admin, "?status=DELIVERED"))).isEmpty();
        assertThat(ids(list(admin, "?warehouseId=9223372036854775807"))).isEmpty();
    }

    /** Đọc danh sách không thay đổi tồn kho, payment hoặc sinh thêm movement. */
    @Test
    void listingDoesNotChangeInventoryOrLedger() throws Exception {
        List<Map<String, Object>> before = stockSnapshot();
        Long movementCount = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM inventory_movements
                """, Long.class);
        list(admin, "");
        list(manager, "?status=CONFIRMED");
        list(staff, "?warehouseId=" + firstWarehouse.getId());
        assertThat(stockSnapshot()).isEqualTo(before);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM inventory_movements
                """, Long.class)).isEqualTo(movementCount);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM payments
                """, Long.class)).isEqualTo(1);
    }

    /** Tạo người dùng với role thật để filter JWT và service đọc quyền từ database. */
    private User user(String role) {
        return users.save(new User(
                UUID.randomUUID() + "@example.com", "hash-kiểm-thử", "Người thử danh sách đơn",
                roles.findByName(role).orElseThrow()));
    }

    /** Tạo kho riêng cho mỗi fixture, tránh phụ thuộc ID hoặc dữ liệu seed demo. */
    private Warehouse warehouse(String code, String name) {
        return warehouses.save(new Warehouse(code, name, "Địa chỉ kiểm thử", WarehouseStatus.ACTIVE));
    }

    /** Ghi phân công kho bằng bảng hiện có, không mở API quản trị nhân viên ngoài phạm vi lượt này. */
    private void assign(User user, Warehouse warehouse) {
        jdbc.update("""
                INSERT INTO warehouse_staff_assignments (user_id, warehouse_id)
                VALUES (?, ?)
                """, user.getId(), warehouse.getId());
    }

    /** Dùng nghiệp vụ tạo đơn thật để fixture có inventory và ledger nhất quán. */
    private OrderResponse create(Product product, Warehouse warehouse, User customer, int quantity) {
        return orders.createOrder(new CreateOrderRequest(
                warehouse.getId(), List.of(new CreateOrderRequest.Item(product.getId(), quantity))), customer.getId());
    }

    /** Phát JWT thật, không dùng mock role để bỏ qua filter xác thực. */
    private String bearer(User user) {
        return "Bearer " + jwt.generateToken(user);
    }

    /** Đọc JSON từ endpoint danh sách sau khi đã kiểm tra HTTP 200. */
    private JsonNode list(User actor, String query) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/orders" + query)
                        .header("Authorization", bearer(actor)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    /** Trích thứ tự ID để kiểm tra phạm vi, tính duy nhất và phân trang cùng một cách. */
    private List<Long> ids(JsonNode page) {
        List<Long> result = new ArrayList<>();
        page.path("content").forEach(order -> result.add(order.path("id").asLong()));
        return result;
    }

    /** Chụp cả số tồn, version và thời điểm cập nhật để phát hiện thao tác ghi ngoài ý muốn. */
    private List<Map<String, Object>> stockSnapshot() {
        return jdbc.queryForList("""
                SELECT id,
                       available_quantity,
                       reserved_quantity,
                       version,
                       updated_at
                FROM inventories
                ORDER BY id
                """);
    }
}
