package com.stockflow.catalog.api;

import static com.stockflow.user.support.UserTestFixtures.verifiedUser;

import static com.stockflow.order.support.CheckoutTestData.deliveryPayload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.auth.security.JwtTokenProvider;
import com.stockflow.catalog.domain.Category;
import com.stockflow.catalog.domain.ProductStatus;
import com.stockflow.catalog.repository.CategoryRepository;
import com.stockflow.catalog.repository.ProductRepository;
import com.stockflow.catalog.repository.ProductVariantRepository;
import com.stockflow.catalog.repository.ProductVersionRepository;
import com.stockflow.inventory.domain.MovementType;
import com.stockflow.inventory.repository.InventoryMovementRepository;
import com.stockflow.inventory.repository.InventoryRepository;
import com.stockflow.user.domain.User;
import com.stockflow.user.repository.RoleRepository;
import com.stockflow.user.repository.UserRepository;
import com.stockflow.warehouse.domain.Warehouse;
import com.stockflow.warehouse.domain.WarehouseStatus;
import com.stockflow.warehouse.repository.WarehouseRepository;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
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
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** Xóa/khôi phục cấu hình qua HTTP/JWT; giữ SKU, kho, ledger và luồng đơn đã tạo. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductConfigurationArchiveIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired ProductVersionRepository versions;
    @Autowired WarehouseRepository warehouses;
    @Autowired InventoryRepository inventories;
    @Autowired InventoryMovementRepository movements;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired JwtTokenProvider jwt;
    @Autowired EntityManager entities;

    private String admin;
    private String customer;
    private long root;
    private long firstVersion;
    private long secondVersion;
    private long originalColor;
    private long childColor;
    private long child;
    private long warehouse;
    private long category;

    /** Hai phiên bản cùng màu có giá/tồn riêng; fixture không phụ thuộc dữ liệu thật hoặc seed demo. */
    @BeforeEach
    void setup() throws Exception {
        admin = token("ADMIN");
        customer = token("CUSTOMER");
        category = categories.save(new Category("Cấu hình " + UUID.randomUUID(), "archive-" + UUID.randomUUID())).getId();
        warehouse = warehouses.save(new Warehouse("ARCH-" + UUID.randomUUID().toString().substring(0, 8),
                "Kho cấu hình kiểm thử", "Hà Nội", WarehouseStatus.ACTIVE)).getId();
        root = body(send(post("/api/v1/products"), Map.of(
                "category_id", category, "sku", "ARCH-ROOT-" + UUID.randomUUID(),
                "name", "Điện thoại kiểm thử", "unit_price", 100, "status", "ACTIVE"), admin)
                .andExpect(status().isCreated())).path("id").asLong();
        JsonNode first = body(send(post("/api/v1/products/{id}/versions", root),
                Map.of("name", "256 GB", "default_color_name", "Đen"), admin).andExpect(status().isCreated()));
        firstVersion = first.path("versions").get(0).path("id").asLong();
        originalColor = first.path("variants").get(0).path("id").asLong();
        JsonNode second = body(send(post("/api/v1/products/{id}/versions", root),
                Map.of("name", "512 GB", "specifications", List.of(Map.of("name", "Bộ nhớ", "value", "512 GB"))),
                admin).andExpect(status().isCreated()));
        secondVersion = second.path("versions").get(1).path("id").asLong();
        JsonNode added = body(send(post("/api/v1/products/{id}/variants", root), Map.of(
                "version_id", secondVersion, "sku", "ARCH-COLOR-" + UUID.randomUUID(), "color_name", "Đen",
                "unit_price", 50, "image_url", "/assets/test-black.jpg"), admin).andExpect(status().isCreated()));
        childColor = added.path("variants").get(1).path("id").asLong();
        child = added.path("variants").get(1).path("sku_product_id").asLong();
        receive(root, 10);
        receive(child, 8);
    }

    /** Xóa màu là idempotent, giữ dòng SKU/mapping và không phát sinh thay đổi vật lý hoặc movement. */
    @Test
    void archiveColorPreservesInventoryAndStopsDirectCheckout() throws Exception {
        long before = movementCount();
        long mappings = variants.count();
        for (int attempt = 0; attempt < 2; attempt++) {
            JsonNode result = archiveColor(childColor);
            assertThat(result.path("variants").get(1).path("archived").asBoolean()).isTrue();
            assertThat(result.path("variants").get(1).path("status").asText()).isEqualTo("INACTIVE");
        }
        entities.clear();
        assertThat(products.findById(child).orElseThrow().getStatus()).isEqualTo(ProductStatus.INACTIVE);
        assertThat(variants.count()).isEqualTo(mappings);
        assertThat(movementCount()).isEqualTo(before);
        stock(child, 8, 0);
        place(child, 1).andExpect(status().isConflict());
        assertThat(availability(child)).isFalse();
    }

    /** Màu của SKU gốc cũng xóa được mà không ẩn model hoặc làm mất khả năng bán màu còn lại. */
    @Test
    void archiveOriginalColorKeepsModelAndOtherColorsAvailable() throws Exception {
        archiveColor(originalColor);
        entities.clear();
        assertThat(products.findById(root).orElseThrow().getStatus()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(detail().path("variants").get(0).path("archived").asBoolean()).isTrue();
        place(root, 1).andExpect(status().isConflict());
        place(child, 1).andExpect(status().isCreated());
        stock(root, 10, 0);
        stock(child, 7, 1);
    }

    /** Xóa phiên bản chặn mọi SKU bên dưới nhưng giữ giá, ảnh, thông số và trạng thái màu riêng. */
    @Test
    void archiveVersionPreservesDataAndProtectsDirectSkuAndPriceQueries() throws Exception {
        long before = movementCount();
        JsonNode beforeDetail = detail();
        JsonNode result = archiveVersion(secondVersion);
        assertThat(result.path("versions").get(1).path("archived").asBoolean()).isTrue();
        assertThat(result.path("versions").get(1).path("specifications"))
                .isEqualTo(beforeDetail.path("versions").get(1).path("specifications"));
        assertThat(result.path("variants").get(1).path("image_url").asText()).isEqualTo("/assets/test-black.jpg");
        assertThat(result.path("variants").get(1).path("archived").asBoolean()).isFalse();
        assertThat(result.path("variants").get(1).path("status").asText()).isEqualTo("INACTIVE");
        assertThat(result.path("min_price").decimalValue()).isEqualByComparingTo("100");
        place(child, 1).andExpect(status().isConflict());
        assertThat(availability(child)).isFalse();
        JsonNode filtered = body(mvc.perform(get("/api/v1/products").param("categoryId", String.valueOf(category))
                .param("grouped", "true").param("minPrice", "80").param("maxPrice", "120"))
                .andExpect(status().isOk()));
        assertThat(filtered.path("content")).hasSize(1);
        assertThat(filtered.path("content").get(0).path("id").asLong()).isEqualTo(root);
        stock(child, 8, 0);
        assertThat(movementCount()).isEqualTo(before);
    }

    /** Xóa phiên bản gốc không xóa trang chung hoặc những cấu hình khác. */
    @Test
    void archiveOriginalVersionKeepsOtherVersionPurchasable() throws Exception {
        archiveVersion(firstVersion);
        place(root, 1).andExpect(status().isConflict());
        place(child, 1).andExpect(status().isCreated());
        assertThat(detail().path("status").asText()).isEqualTo("ACTIVE");
        stock(root, 10, 0);
    }

    /** Phiên bản chưa có SKU vẫn có thể xóa/khôi phục; không bỏ ràng buộc tên hoặc tạo SKU giả. */
    @Test
    void archiveAndRestoreEmptyVersionIsRepeatable() throws Exception {
        JsonNode result = body(send(post("/api/v1/products/{id}/versions", root), Map.of("name", "1 TB"), admin)
                .andExpect(status().isCreated()));
        long empty = result.path("versions").get(2).path("id").asLong();
        long before = variants.count();
        archiveVersion(empty);
        archiveVersion(empty);
        send(post("/api/v1/products/{id}/variants", root), Map.of(
                "version_id", empty, "sku", "ARCH-EMPTY-" + UUID.randomUUID(), "color_name", "Trắng"), admin)
                .andExpect(status().isConflict());
        restoreVersion(empty);
        assertThat(versions.findById(empty).orElseThrow().isArchived()).isFalse();
        assertThat(variants.count()).isEqualTo(before);
    }

    /** Khôi phục phiên bản không tự mở màu đã xóa hoặc màu tạm ngừng bán. */
    @Test
    void restoreVersionRetainsIndependentColorStates() throws Exception {
        archiveColor(childColor);
        archiveVersion(secondVersion);
        restoreVersion(secondVersion);
        assertThat(detail().path("variants").get(1).path("archived").asBoolean()).isTrue();
        place(child, 1).andExpect(status().isConflict());
        send(patch("/api/v1/products/{id}/variants/{color}", root, childColor), Map.of("status", "ACTIVE"), admin)
                .andExpect(status().isOk());
        assertThat(detail().path("variants").get(1).path("archived").asBoolean()).isFalse();
        assertThat(availability(child)).isTrue();
        place(child, 1).andExpect(status().isCreated());
        stock(child, 7, 1);
    }

    /** Màu ngừng bán chỉ dùng trạng thái; không bị ghi nhận là đã xóa khi khôi phục phiên bản. */
    @Test
    void pauseColorRemainsDistinctFromDeletedColor() throws Exception {
        send(patch("/api/v1/products/{id}/variants/{color}", root, childColor), Map.of("status", "INACTIVE"), admin)
                .andExpect(status().isOk());
        archiveVersion(secondVersion);
        restoreVersion(secondVersion);
        JsonNode color = detail().path("variants").get(1);
        assertThat(color.path("archived").asBoolean()).isFalse();
        assertThat(color.path("status").asText()).isEqualTo("INACTIVE");
    }

    /** Không mở lại màu riêng dưới phiên bản đã xóa; lỗi rollback cả sửa giá cùng request. */
    @Test
    void restoreColorRequiresActiveVersionAndRollsBackOtherChanges() throws Exception {
        archiveColor(childColor);
        archiveVersion(secondVersion);
        send(patch("/api/v1/products/{id}/variants/{color}", root, childColor),
                Map.of("status", "ACTIVE", "unit_price", 999), admin).andExpect(status().isConflict());
        entities.clear();
        assertThat(products.findById(child).orElseThrow().getUnitPrice()).isEqualByComparingTo("50");
        assertThat(variants.findById(childColor).orElseThrow().isArchived()).isTrue();
        stock(child, 8, 0);
    }

    /** Khôi phục màu gốc không thay ID/ảnh/tồn hoặc kích hoạt lại một trang model đã bị ẩn. */
    @Test
    void restoreOriginalColorUsesExistingSku() throws Exception {
        archiveColor(originalColor);
        send(patch("/api/v1/products/{id}/variants/{color}", root, originalColor), Map.of("status", "ACTIVE"), admin)
                .andExpect(status().isOk());
        assertThat(detail().path("variants").get(0).path("sku_product_id").asLong()).isEqualTo(root);
        assertThat(availability(root)).isTrue();
        stock(root, 10, 0);
    }

    /** Đơn đã giữ trước khi xóa vẫn thanh toán, giao và hoàn được bằng SKU lịch sử. */
    @Test
    void existingOrderCompletesAndReturnsAfterVersionIsArchived() throws Exception {
        long order = body(place(child, 3).andExpect(status().isCreated())).path("id").asLong();
        long afterHold = movementCount();
        archiveVersion(secondVersion);
        assertThat(movementCount()).isEqualTo(afterHold);
        stock(child, 5, 3);
        send(post("/api/v1/orders/{id}/payment-simulations/confirm", order), null, customer).andExpect(status().isOk());
        for (String action : List.of("pack", "ship", "deliver", "return")) {
            send(post("/api/v1/orders/{id}/" + action, order), null, admin).andExpect(status().isOk());
        }
        stock(child, 8, 0);
        long inventoryId = inventories.findByProductIdAndWarehouseId(child, warehouse).orElseThrow().getId();
        assertThat(movements.search(inventoryId, Pageable.unpaged()).getContent().stream()
                .filter(value -> value.getType() == MovementType.RETURN_RESTOCK)).hasSize(1);
        assertThat(detail().path("versions").get(1).path("archived").asBoolean()).isTrue();
    }

    /** Xóa màu không ngăn hủy đơn đã giữ và ghi RESERVATION_RELEASE đúng một lần. */
    @Test
    void pendingOrderReleasesStockAfterColorIsArchived() throws Exception {
        long order = body(place(child, 2).andExpect(status().isCreated())).path("id").asLong();
        archiveColor(childColor);
        send(post("/api/v1/orders/{id}/cancel", order), null, customer).andExpect(status().isOk());
        stock(child, 8, 0);
    }

    /** Cả hai loại xóa và khôi phục đều chỉ ADMIN; JWT của role khác bị chặn trước ghi. */
    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "MANAGER", "WAREHOUSE_STAFF"})
    void nonAdminCannotArchiveOrRestoreConfiguration(String role) throws Exception {
        String actor = token(role);
        send(delete("/api/v1/products/{id}/variants/{color}", root, childColor), null, actor).andExpect(status().isForbidden());
        send(delete("/api/v1/products/{id}/versions/{version}", root, secondVersion), null, actor).andExpect(status().isForbidden());
        send(post("/api/v1/products/{id}/versions/{version}/restore", root, secondVersion), null, actor).andExpect(status().isForbidden());
        assertThat(detail().path("versions").get(1).path("archived").asBoolean()).isFalse();
    }

    /** Chưa đăng nhập không được truy cập API ghi cấu hình. */
    @Test
    void anonymousCannotArchive() throws Exception {
        mvc.perform(delete("/api/v1/products/{id}/versions/{version}", root, firstVersion)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/products/{id}/variants/{color}", root, childColor)).andExpect(status().isUnauthorized());
    }

    /** Sai model hoặc ID thiếu trả 404 và không ẩn nhầm cấu hình. */
    @Test
    void foreignAndMissingIdsCannotBeArchived() throws Exception {
        long another = body(send(post("/api/v1/products"), Map.of(
                "category_id", category, "sku", "ARCH-OTHER-" + UUID.randomUUID(), "name", "Model khác",
                "unit_price", 100, "status", "ACTIVE"), admin).andExpect(status().isCreated())).path("id").asLong();
        send(delete("/api/v1/products/{id}/versions/{version}", another, firstVersion), null, admin).andExpect(status().isNotFound());
        send(delete("/api/v1/products/{id}/variants/{color}", another, childColor), null, admin).andExpect(status().isNotFound());
        send(delete("/api/v1/products/{id}/versions/{version}", root, Long.MAX_VALUE), null, admin).andExpect(status().isNotFound());
        send(post("/api/v1/products/{id}/versions/{version}/restore", root, Long.MAX_VALUE), null, admin).andExpect(status().isNotFound());
        assertThat(detail().path("variants").get(1).path("archived").asBoolean()).isFalse();
    }

    /** SKU con không được giả làm model để tác động cấu hình của trang gốc. */
    @Test
    void childSkuCannotManageRootConfiguration() throws Exception {
        send(delete("/api/v1/products/{id}/versions/{version}", child, secondVersion), null, admin).andExpect(status().isBadRequest());
        send(delete("/api/v1/products/{id}/variants/{color}", child, childColor), null, admin).andExpect(status().isBadRequest());
    }

    /** Dùng API nhập thật để kho và ledger ban đầu có cùng chứng từ hợp lệ. */
    private void receive(long sku, int quantity) throws Exception {
        send(post("/api/v1/inventories/stock-in"), Map.of("product_id", sku, "warehouse_id", warehouse,
                "quantity", quantity), admin).andExpect(status().isCreated());
    }

    /** Checkout luôn gửi ID SKU, không dùng ID version hoặc màu. */
    private ResultActions place(long sku, int quantity) throws Exception {
        return send(post("/api/v1/orders"), Map.of("warehouse_id", warehouse,
                "items", List.of(Map.of("product_id", sku, "quantity", quantity)),
                "delivery", deliveryPayload()), customer);
    }

    /** Parse UTF-8 và đọc model qua cùng API công khai như storefront. */
    private JsonNode detail() throws Exception {
        return body(mvc.perform(get("/api/v1/products/{id}", root)).andExpect(status().isOk()));
    }

    /** Phiên bản/màu xóa bằng API ADMIN, trả model cập nhật để UI đồng bộ. */
    private JsonNode archiveColor(long id) throws Exception {
        return body(send(delete("/api/v1/products/{id}/variants/{color}", root, id), null, admin).andExpect(status().isOk()));
    }

    /** Xóa cả phiên bản là lưu trữ metadata, không cập nhật số lượng tồn. */
    private JsonNode archiveVersion(long id) throws Exception {
        return body(send(delete("/api/v1/products/{id}/versions/{version}", root, id), null, admin).andExpect(status().isOk()));
    }

    /** Khôi phục cấu hình cũ giữ ID và trạng thái màu độc lập. */
    private void restoreVersion(long id) throws Exception {
        send(post("/api/v1/products/{id}/versions/{version}/restore", root, id), null, admin).andExpect(status().isOk());
    }

    /** Availability chỉ công khai còn/hết, không cần mở API tồn kho nội bộ cho khách. */
    private boolean availability(long sku) throws Exception {
        JsonNode result = body(mvc.perform(get("/api/v1/products/{id}/availability", sku)).andExpect(status().isOk()));
        return result.get(0).path("in_stock").asBoolean();
    }

    /** Clear JPA trước đọc tồn để đối chiếu các bulk update của reserve/dispatch/restock. */
    private void stock(long sku, int available, int reserved) {
        entities.clear();
        var row = inventories.findByProductIdAndWarehouseId(sku, warehouse).orElseThrow();
        assertThat(row.getAvailableQuantity()).isEqualTo(available);
        assertThat(row.getReservedQuantity()).isEqualTo(reserved);
    }

    /** Repository sổ cái chỉ cho đọc/thêm; đếm qua truy vấn đọc để không mở rộng quyền sửa/xóa. */
    private long movementCount() {
        return movements.search(null, Pageable.unpaged()).getTotalElements();
    }

    /** Token thật cho account fixture; không cần log mật khẩu hoặc access token. */
    private String token(String role) {
        return jwt.generateToken(users.save(verifiedUser("archive-" + UUID.randomUUID() + "@example.com", "hash",
                "Người thử cấu hình", roles.findByName(role).orElseThrow())));
    }

    /** Đi qua security và controller để kiểm tra toàn bộ HTTP contract. */
    private ResultActions send(MockHttpServletRequestBuilder request, Map<String, ?> payload, String actor) throws Exception {
        request.header("Authorization", "Bearer " + actor);
        if (payload != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload));
        ResultActions result = mvc.perform(request);
        // MockMvc dùng transaction của test; flush thành công mô phỏng ranh giới commit của mỗi HTTP request.
        if (result.andReturn().getResponse().getStatus() < 400) {
            entities.flush();
        }
        return result;
    }

    /** Đọc chuỗi có dấu đúng encoding thay vì phụ thuộc charset mặc định. */
    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
