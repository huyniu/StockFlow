package com.stockflow.catalog.api;

import static com.stockflow.user.support.UserTestFixtures.verifiedUser;

import static com.stockflow.order.support.CheckoutTestData.deliveryPayload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.stockflow.inventory.repository.InventoryMovementRepository;
import com.stockflow.inventory.repository.InventoryRepository;
import com.stockflow.user.domain.User;
import com.stockflow.user.repository.RoleRepository;
import com.stockflow.user.repository.UserRepository;
import com.stockflow.warehouse.domain.Warehouse;
import com.stockflow.warehouse.domain.WarehouseStatus;
import com.stockflow.warehouse.repository.WarehouseRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** HTTP/JWT thật kiểm tra tồn công khai đúng SKU, không lộ số lượng và không ảnh hưởng phân quyền kho. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductAvailabilityIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired WarehouseRepository warehouses;
    @Autowired InventoryRepository inventories;
    @Autowired InventoryMovementRepository movements;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired JwtTokenProvider jwt;
    @Autowired EntityManager entities;

    private Product root;
    private Warehouse first;
    private Warehouse second;
    private String admin;

    /** Seed qua nghiệp vụ nhập kho thật để mọi stock change của fixture đều có movement hợp lệ. */
    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString();
        Category category = categories.save(new Category("Tình trạng " + suffix, "availability-" + suffix));
        root = products.save(new Product(category, "AV-" + suffix, "Đồng hồ kiểm thử",
                new BigDecimal("7000000"), ProductStatus.ACTIVE));
        first = warehouses.save(new Warehouse("AV-A-" + suffix.substring(0, 8),
                "Chi nhánh kiểm thử A", "Địa chỉ nội bộ A", WarehouseStatus.ACTIVE));
        second = warehouses.save(new Warehouse("AV-B-" + suffix.substring(0, 8),
                "Chi nhánh kiểm thử B", "Địa chỉ nội bộ B", WarehouseStatus.ACTIVE));
        admin = token("ADMIN");
    }

    /** Khách vãng lai nhận tín hiệu mới nhất nhưng không nhận quantity, ledger, địa chỉ hoặc khóa dòng tồn. */
    @Test
    void anonymousGetsMinimalAvailabilityWithoutChangingStockOrLedger() throws Exception {
        stock(root.getId(), first, 2);
        long count = movements.search(null, PageRequest.of(0, 1)).getTotalElements();
        var response = mvc.perform(get(path(root.getId()))).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        JsonNode rows = json.readTree(response.getContentAsString());
        assertThat(row(rows, root.getId(), first).path("in_stock").asBoolean()).isTrue();
        assertThat(row(rows, root.getId(), second).path("in_stock").asBoolean()).isFalse();
        for (JsonNode value : rows) {
            assertThat(value.size()).isEqualTo(5);
            assertThat(value.has("available_quantity")).isFalse();
            assertThat(value.has("reserved_quantity")).isFalse();
            assertThat(value.has("address")).isFalse();
            assertThat(value.has("inventory_id")).isFalse();
        }
        assertThat(movements.search(null, PageRequest.of(0, 1)).getTotalElements()).isEqualTo(count);
        assertThat(inventories.findByProductIdAndWarehouseId(root.getId(), first.getId())
                .orElseThrow().getAvailableQuantity()).isEqualTo(2);
        assertThat(inventories.findByProductIdAndWarehouseId(root.getId(), second.getId())).isEmpty();
    }

    /** Cùng màu ở hai phiên bản có tình trạng riêng; kho ngừng phục vụ không xuất hiện trong API. */
    @Test
    void availabilitySeparatesVersionsColorsAndBranches() throws Exception {
        addVersion("40mm GPS");
        JsonNode model = addVersion("44mm GPS");
        long version = model.path("versions").get(1).path("id").asLong();
        long child = addColor(version, "Ánh sao").path("variants").get(1).path("sku_product_id").asLong();
        stock(root.getId(), first, 1);
        stock(child, second, 3);
        Warehouse closed = warehouses.save(new Warehouse("AV-C-" + UUID.randomUUID().toString().substring(0, 8),
                "Chi nhánh đã đóng", "Địa chỉ nội bộ", WarehouseStatus.INACTIVE));
        JsonNode rows = availability(root.getId());
        assertThat(row(rows, root.getId(), first).path("in_stock").asBoolean()).isTrue();
        assertThat(row(rows, root.getId(), second).path("in_stock").asBoolean()).isFalse();
        assertThat(row(rows, child, first).path("in_stock").asBoolean()).isFalse();
        assertThat(row(rows, child, second).path("in_stock").asBoolean()).isTrue();
        assertThat(StreamSupport.stream(rows.spliterator(), false))
                .noneMatch(value -> value.path("warehouse_id").asLong() == closed.getId());
    }

    /** URL SKU chỉ trả tình trạng SKU đó, không ghép tồn từ màu khác vào lựa chọn khách đang xem. */
    @Test
    void skuEndpointDoesNotBorrowStockFromSiblingColors() throws Exception {
        JsonNode model = addVersion("40mm GPS");
        long child = addColor(model.path("versions").get(0).path("id").asLong())
                .path("variants").get(1).path("sku_product_id").asLong();
        stock(root.getId(), first, 5);
        JsonNode rows = availability(child);
        assertThat(rows).isNotEmpty();
        for (JsonNode value : rows) {
            assertThat(value.path("product_id").asLong()).isEqualTo(child);
            assertThat(value.path("in_stock").asBoolean()).isFalse();
        }
    }

    /** SKU ngừng bán không báo còn hàng dù số hàng vật lý vẫn được giữ để truy vết. */
    @Test
    void inactiveProductIsUnavailableDespitePositiveStock() throws Exception {
        stock(root.getId(), first, 2);
        root.update(null, null, ProductStatus.INACTIVE);
        entities.flush();
        assertThat(row(availability(root.getId()), root.getId(), first).path("in_stock").asBoolean()).isFalse();
    }

    /** Dừng bán một màu không làm mất lịch sử hoặc vô tình công khai màu đó là còn bán. */
    @Test
    void disabledColorIsUnavailableDespitePositiveStock() throws Exception {
        JsonNode model = addVersion("40mm GPS");
        long variantId = model.path("variants").get(0).path("id").asLong();
        stock(root.getId(), first, 2);
        send(patch("/api/v1/products/{id}/variants/{variant}", root.getId(), variantId),
                Map.of("status", "INACTIVE"), admin);
        assertThat(row(availability(root.getId()), root.getId(), first).path("in_stock").asBoolean()).isFalse();
    }

    /** Đóng cả model cũng chặn SKU con dù SKU con vẫn ACTIVE và có hàng trong kho. */
    @Test
    void inactiveModelMakesItsChildSkuUnavailable() throws Exception {
        JsonNode model = addVersion("40mm GPS");
        long child = addColor(model.path("versions").get(0).path("id").asLong())
                .path("variants").get(1).path("sku_product_id").asLong();
        stock(child, first, 2);
        send(patch("/api/v1/products/{id}", root.getId()), Map.of("status", "INACTIVE"), admin);
        assertThat(row(availability(child), child, first).path("in_stock").asBoolean()).isFalse();
    }

    /** Toàn bộ hàng đã được giữ phải báo hết; hủy đơn mở bán lại bằng movement giải phóng thật. */
    @Test
    void pendingReservationChangesAvailabilityAndCancelRestoresIt() throws Exception {
        stock(root.getId(), first, 1);
        String customer = token("CUSTOMER");
        JsonNode order = send(post("/api/v1/orders"), Map.of("warehouse_id", first.getId(),
                "items", List.of(Map.of("product_id", root.getId(), "quantity", 1)),
                "delivery", deliveryPayload()), customer);
        assertThat(row(availability(root.getId()), root.getId(), first).path("in_stock").asBoolean()).isFalse();
        send(post("/api/v1/orders/{id}/cancel", order.path("id").asLong()), Map.of(), customer);
        assertThat(row(availability(root.getId()), root.getId(), first).path("in_stock").asBoolean()).isTrue();
    }

    /** Mọi role đọc cùng dữ liệu tối thiểu, không dùng JWT để tiết lộ thêm số tồn cho storefront. */
    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "WAREHOUSE_STAFF", "MANAGER", "ADMIN"})
    void allRolesCanReadPublicAvailability(String role) throws Exception {
        mvc.perform(get(path(root.getId())).header("Authorization", "Bearer " + token(role)))
                .andExpect(status().isOk());
    }

    /** API mới không mở API quản trị kho, ledger hoặc thao tác ghi cho khách. */
    @Test
    void inventoryAndLedgerRemainProtected() throws Exception {
        mvc.perform(get("/api/v1/inventories")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/inventories").header("Authorization", "Bearer " + token("CUSTOMER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/inventories/movements").header("Authorization", "Bearer " + token("CUSTOMER")))
                .andExpect(status().isForbidden());
        mvc.perform(post(path(root.getId()))).andExpect(status().isUnauthorized());
    }

    /** ID không tồn tại trả lỗi 404 thay vì mảng rỗng dễ bị hiểu nhầm là hết hàng. */
    @Test
    void missingProductReturnsNotFound() throws Exception {
        mvc.perform(get(path(Long.MAX_VALUE))).andExpect(status().isNotFound());
    }

    /** Nạp hàng qua controller để kiểm tra actor, transaction và movement thật. */
    private void stock(Long productId, Warehouse warehouse, int quantity) throws Exception {
        send(post("/api/v1/inventories/stock-in"), Map.of("product_id", productId,
                "warehouse_id", warehouse.getId(), "quantity", quantity, "note", "Nhập hàng kiểm thử storefront"), admin);
    }

    /** Khởi tạo phiên bản với màu gốc thực tế, giữ nguyên ID SKU đã có. */
    private JsonNode addVersion(String name) throws Exception {
        return send(post("/api/v1/products/{id}/versions", root.getId()), Map.of("name", name,
                "default_color_name", "Ánh sao", "default_color_hex", "#eadcc8"), admin);
    }

    /** Tạo màu thứ hai khác màu gốc để fixture không vi phạm UNIQUE của cùng phiên bản. */
    private JsonNode addColor(long versionId) throws Exception {
        return addColor(versionId, "Đêm xanh");
    }

    /** Màu cùng tên ở phiên bản khác vẫn tạo SKU độc lập. */
    private JsonNode addColor(long versionId, String colorName) throws Exception {
        return send(post("/api/v1/products/{id}/variants", root.getId()), Map.of("version_id", versionId,
                "sku", "AV-CHILD-" + UUID.randomUUID(), "color_name", colorName,
                "unit_price", 8000000), admin);
    }

    /** Lấy response qua HTTP thật thay vì gọi service bỏ qua SecurityFilterChain. */
    private JsonNode availability(Long productId) throws Exception {
        return json.readTree(mvc.perform(get(path(productId))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    /** Tìm theo cặp SKU/chi nhánh, không phụ thuộc ID seed hoặc thứ tự chạy các test khác. */
    private JsonNode row(JsonNode rows, Long productId, Warehouse warehouse) {
        return StreamSupport.stream(rows.spliterator(), false)
                .filter(value -> value.path("product_id").asLong() == productId
                        && value.path("warehouse_id").asLong() == warehouse.getId())
                .findFirst().orElseThrow();
    }

    /** Yêu cầu ghi phải thành công rồi mới kiểm tra tín hiệu đọc để tránh fixture giả thành công. */
    private JsonNode send(MockHttpServletRequestBuilder request, Map<String, ?> body, String token) throws Exception {
        var response = mvc.perform(request.header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse();
        return json.readTree(response.getContentAsString());
    }

    /** JWT của actor đã lưu giúp quyền và actor kho đi qua cùng đường xác thực như production. */
    private String token(String role) {
        User user = users.save(verifiedUser(UUID.randomUUID() + "@example.com", "hash-kiểm-thử",
                "Khách kiểm thử", roles.findByName(role).orElseThrow()));
        return jwt.generateToken(user);
    }

    /** URL công khai được SecurityConfig hiện có cho phép GET; không thêm permitAll cho API tồn kho. */
    private String path(Long productId) {
        return "/api/v1/products/" + productId + "/availability";
    }
}
