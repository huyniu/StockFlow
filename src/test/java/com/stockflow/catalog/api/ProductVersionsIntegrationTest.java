package com.stockflow.catalog.api;

import static com.stockflow.user.support.UserTestFixtures.verifiedUser;

import static com.stockflow.order.support.CheckoutTestData.deliveryPayload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.auth.security.JwtTokenProvider;
import com.stockflow.catalog.domain.Category;
import com.stockflow.catalog.repository.CategoryRepository;
import com.stockflow.catalog.repository.ProductRepository;
import com.stockflow.catalog.repository.ProductVersionRepository;
import com.stockflow.inventory.repository.InventoryMovementRepository;
import com.stockflow.inventory.repository.InventoryRepository;
import com.stockflow.order.repository.OrderRepository;
import com.stockflow.user.domain.User;
import com.stockflow.user.repository.RoleRepository;
import com.stockflow.user.repository.UserRepository;
import com.stockflow.warehouse.domain.Warehouse;
import com.stockflow.warehouse.domain.WarehouseStatus;
import com.stockflow.warehouse.repository.WarehouseRepository;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** HTTP/JWT thật kiểm tra một model, phiên bản/màu, quyền ADMIN và transaction reserve đúng SKU. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductVersionsIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired ProductVersionRepository versions;
    @Autowired RoleRepository roles;
    @Autowired UserRepository users;
    @Autowired JwtTokenProvider jwt;
    @Autowired WarehouseRepository warehouses;
    @Autowired InventoryRepository inventories;
    @Autowired InventoryMovementRepository movements;
    @Autowired OrderRepository orders;
    @Autowired EntityManager entities;

    private Category category;
    private Warehouse warehouse;
    private String admin;
    private String customer;

    /** Fixture riêng không phụ thuộc dữ liệu demo hoặc database người dùng đang nhập. */
    @BeforeEach
    void setup() {
        category = categories.save(new Category("Phiên bản " + UUID.randomUUID(), "version-" + UUID.randomUUID()));
        warehouse = warehouses.save(new Warehouse("VER-" + UUID.randomUUID().toString().substring(0, 8),
                "Kho kiểm thử phiên bản", "Hà Nội", WarehouseStatus.ACTIVE));
        admin = token("ADMIN");
        customer = token("CUSTOMER");
    }

    /** Hai cấu hình cùng màu vẫn là hai SKU nhưng chỉ chiếm một thẻ model trước phân trang. */
    @Test
    void oneModelContainsMultipleVersionsAndSameColorHasIndependentSkus() throws Exception {
        long root = create(7000000);
        JsonNode first = addVersion(root, "40mm GPS · Dây S/M", List.of());
        assertThat(first.path("variants").get(0).path("sku_product_id").asLong()).isEqualTo(root);
        long second = lastVersion(addVersion(root, "44mm GPS · Dây M/L", List.of()));
        JsonNode model = addColor(root, second, "Ánh sao", 8000000);
        assertThat(model.path("versions")).hasSize(2);
        assertThat(model.path("variants")).hasSize(2);
        assertThat(model.path("variants").get(1).path("version_id").asLong()).isEqualTo(second);
        assertThat(model.path("variants").get(1).path("sku_product_id").asLong()).isNotEqualTo(root);
        mvc.perform(get("/api/v1/products").param("categoryId", category.getId().toString())
                        .param("grouped", "true").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total_elements").value(1))
                .andExpect(jsonPath("$.content[0].versions.length()").value(2));
        mvc.perform(get("/api/v1/products").param("grouped", "true").param("q", "44mm GPS"))
                .andExpect(jsonPath("$.total_elements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(root));
        assertThat(inventories.findByProductIdAndWarehouseId(root, warehouse.getId())).isEmpty();
        assertThat(inventories.findByProductIdAndWarehouseId(lastSku(model), warehouse.getId())).isEmpty();
    }

    /** Giá từ, filter và sort của thẻ chung chỉ tính những SKU còn được bán. */
    @Test
    void groupedPriceFilteringSortingAndPaginationUseCheapestActiveSku() throws Exception {
        long expensive = create(9000000);
        addVersion(expensive, "40mm", List.of());
        long bigger = lastVersion(addVersion(expensive, "44mm", List.of()));
        JsonNode group = addColor(expensive, bigger, "Đen", 6000000);
        long other = create(7500000);
        mvc.perform(get("/api/v1/products").param("categoryId", category.getId().toString())
                        .param("grouped", "true").param("minPrice", "6000000").param("maxPrice", "6500000"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total_elements").value(1))
                .andExpect(jsonPath("$.content[0].min_price").value(6000000));
        mvc.perform(get("/api/v1/products").param("categoryId", category.getId().toString())
                        .param("grouped", "true").param("sort", "unit_price,asc").param("size", "1"))
                .andExpect(jsonPath("$.total_elements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(expensive));
        mvc.perform(get("/api/v1/products").param("categoryId", category.getId().toString())
                        .param("grouped", "true").param("sort", "unitPrice,asc").param("size", "1").param("page", "1"))
                .andExpect(jsonPath("$.content[0].id").value(other));
        send(patch("/api/v1/products/{id}/variants/{variant}", expensive, group.path("variants").get(1).path("id").asLong()),
                Map.of("status", "INACTIVE"), admin).andExpect(status().isOk());
        mvc.perform(get("/api/v1/products/{id}", expensive)).andExpect(jsonPath("$.min_price").value(9000000));
    }

    /** Cấu hình mới chưa có SKU không tự sinh tồn/ledger và không thay đổi SKU hiện tại. */
    @Test
    void versionCreationPreservesOriginalSkuPriceImageAndHasNoAutomaticStock() throws Exception {
        long root = create(7000000);
        var original = products.findById(root).orElseThrow();
        String sku = original.getSku();
        long ledger = movements.search(null, Pageable.unpaged()).getTotalElements();
        addVersion(root, "40mm GPS", List.of());
        JsonNode model = addVersion(root, "44mm GPS", List.of());
        assertThat(model.path("variants")).hasSize(1);
        assertThat(model.path("sku").asText()).isEqualTo(sku);
        assertThat(model.path("unit_price").asInt()).isEqualTo(7000000);
        assertThat(model.path("image_url").asText()).isEqualTo("/assets/watch-star.jpg");
        assertThat(movements.search(null, Pageable.unpaged()).getTotalElements()).isEqualTo(ledger);
    }

    /** Chặn đoán màu gốc từ tên, để toàn bộ nhóm được khai báo đúng trước khi thêm SKU. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void firstVersionRequiresOriginalColorAndRollsBack() throws Exception {
        long root = create(7000000);
        send(post("/api/v1/products/{id}/versions", root), Map.of("name", "40mm"), admin)
                .andExpect(status().isBadRequest());
        assertThat(detail(root).path("versions")).isEmpty();
        assertThat(detail(root).path("variants")).isEmpty();
    }

    /** Tên phiên bản chuẩn hóa trong model; tên giống nhau giữa hai model khác nhau vẫn hợp lệ. */
    @Test
    void rejectsDuplicateVersionAndDuplicateColorOnlyWithinSameVersion() throws Exception {
        long root = create(7000000);
        long first = lastVersion(addVersion(root, "40mm GPS", List.of()));
        send(post("/api/v1/products/{id}/versions", root), Map.of("name", " 40MM GPS "), admin)
                .andExpect(status().isConflict());
        send(post("/api/v1/products/{id}/variants", root), color(first, " ÁNH SAO ", 8000000), admin)
                .andExpect(status().isConflict());
        addVersion(create(9000000), "40mm GPS", List.of());
    }

    /** Ngăn tràn schema, tên rỗng hoặc bảng riêng sai ngay tại Bean Validation. */
    @ParameterizedTest
    @ValueSource(strings = {"blank", "long", "spec-null", "spec-duplicate", "spec-count"})
    void rejectsInvalidVersionPayload(String kind) throws Exception {
        long root = create(7000000);
        var payload = new HashMap<String, Object>(Map.of("name", "40mm", "default_color_name", "Ánh sao"));
        switch (kind) {
            case "blank" -> payload.put("name", "   ");
            case "long" -> payload.put("name", "a".repeat(161));
            case "spec-null" -> {
                var rows = new ArrayList<>();
                rows.add(null);
                payload.put("specifications", rows);
            }
            case "spec-duplicate" -> payload.put("specifications", List.of(
                    Map.of("name", "RAM", "value", "8 GB"), Map.of("name", " ram ", "value", "16 GB")));
            default -> payload.put("specifications", java.util.stream.IntStream.range(0, 61)
                    .mapToObj(i -> Map.of("name", "Thông số " + i, "value", "Giá trị")).toList());
        }
        send(post("/api/v1/products/{id}/versions", root), payload, admin).andExpect(status().isBadRequest());
    }

    /** Khách, quản lý và nhân viên được đọc cấu hình nhưng không có quyền sửa catalog. */
    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "MANAGER", "WAREHOUSE_STAFF"})
    void nonAdminsCannotCreateOrEditVersions(String role) throws Exception {
        long root = create(7000000);
        long version = lastVersion(addVersion(root, "40mm", List.of()));
        String actor = token(role);
        send(post("/api/v1/products/{id}/versions", root), Map.of("name", "44mm"), actor)
                .andExpect(status().isForbidden());
        send(patch("/api/v1/products/{id}/versions/{version}", root, version), Map.of("name", "Sai quyền"), actor)
                .andExpect(status().isForbidden());
    }

    /** Phiên bản của model khác không thể nhận SKU hoặc được sửa qua đường dẫn model hiện tại. */
    @Test
    void rejectsWrongParentAndNestedSkuModel() throws Exception {
        long root = create(7000000);
        long version = lastVersion(addVersion(root, "40mm", List.of()));
        long other = create(8000000);
        addVersion(other, "44mm", List.of());
        send(post("/api/v1/products/{id}/variants", other), color(version, "Đen", 8000000), admin)
                .andExpect(status().isNotFound());
        send(patch("/api/v1/products/{id}/versions/{version}", other, version), Map.of("name", "Sai cha"), admin)
                .andExpect(status().isNotFound());
        long child = lastSku(addColor(root, version, "Đen", 8000000));
        send(post("/api/v1/products/{id}/versions", child), Map.of("name", "Nhóm lồng"), admin)
                .andExpect(status().isBadRequest());
    }

    /** Ghi đè theo nhãn giữ thông số chung khác; PATCH null giữ bảng riêng, [] đưa về bảng chung. */
    @Test
    void effectiveSpecificationsFollowVersionAndCommonUpdatesPreserveOverrides() throws Exception {
        long root = create(7000000);
        long version = lastVersion(addVersion(root, "44mm", List.of(Map.of("name", "Kích thước", "value", "44mm"))));
        long child = lastSku(addColor(root, version, "Đen", 8000000));
        send(patch("/api/v1/products/{id}", root), Map.of("name", "Model mới", "specifications", List.of(
                Map.of("name", "Chipset", "value", "Chip mới"), Map.of("name", "Kích thước", "value", "Chưa chọn"))), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versions[0].effective_specifications[0].value").value("Chip mới"))
                .andExpect(jsonPath("$.versions[0].effective_specifications[1].value").value("44mm"));
        assertThat(detail(child).path("specifications").get(1).path("value").asText()).isEqualTo("44mm");
        var nullable = new HashMap<String, Object>();
        nullable.put("specifications", null);
        send(patch("/api/v1/products/{id}/versions/{version}", root, version), nullable, admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.versions[0].specifications.length()").value(1));
        send(patch("/api/v1/products/{id}/versions/{version}", root, version),
                Map.of("name", "44mm GPS", "specifications", List.of()), admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.versions[0].effective_specifications[1].value").value("Chưa chọn"));
        assertThat(detail(child).path("name").asText()).contains("44mm GPS", "Đen");
        assertThat(detail(child).path("unit_price").asInt()).isEqualTo(8000000);
    }

    /** Bảng chung + riêng quá giới hạn rollback cả đổi tên và các nội dung đã gán trong transaction. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void combinedSpecificationLimitRollsBackEntireUpdate() throws Exception {
        long root = create(7000000);
        long version = lastVersion(addVersion(root, "40mm", List.of(Map.of("name", "Thông số riêng", "value", "Có"))));
        var rows = java.util.stream.IntStream.range(0, 60)
                .mapToObj(i -> Map.of("name", "Chung " + i, "value", "Giá trị")).toList();
        send(patch("/api/v1/products/{id}", root), Map.of("name", "Không được lưu", "specifications", rows), admin)
                .andExpect(status().isBadRequest());
        assertThat(detail(root).path("name").asText()).isEqualTo("Đồng hồ kiểm thử");
        assertThat(detail(root).path("specifications")).hasSize(2);
        assertThat(detail(root).path("versions").get(0).path("id").asLong()).isEqualTo(version);
    }

    /** Giá snapshot, reserve, xuất và hoàn toàn đơn tác động đúng hai SKU thuộc hai phiên bản. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void fullOrderLifecycleDispatchesAndRestocksCorrectVersionsWithoutDoubleDispatch() throws Exception {
        long root = create(7000000);
        addVersion(root, "40mm", List.of());
        long second = lastVersion(addVersion(root, "44mm", List.of()));
        long child = lastSku(addColor(root, second, "Ánh sao", 8000000));
        receive(root, 5);
        receive(child, 4);
        long order = body(place(List.of(item(child, 2), item(root, 1))).andExpect(status().isCreated()))
                .path("id").asLong();
        stock(root, 4, 1);
        stock(child, 2, 2);
        assertThat(orders.findById(order).orElseThrow().getTotalAmount().intValue()).isEqualTo(23000000);
        operate(order, "payment-simulations/confirm", customer);
        stock(root, 4, 0);
        stock(child, 2, 0);
        operate(order, "pack", admin);
        operate(order, "ship", admin);
        stock(root, 4, 0);
        stock(child, 2, 0);
        operate(order, "deliver", admin);
        operate(order, "return", admin);
        operate(order, "return", admin);
        stock(root, 5, 0);
        stock(child, 4, 0);
        long inventoryId = inventories.findByProductIdAndWarehouseId(child, warehouse.getId()).orElseThrow().getId();
        var ledger = movements.search(inventoryId, Pageable.unpaged()).getContent();
        assertThat(ledger.stream().filter(value -> value.getType().name().equals("RETURN_RESTOCK"))).hasSize(1);
        var returned = ledger.stream().filter(value -> value.getType().name().equals("RETURN_RESTOCK")).findFirst().orElseThrow();
        assertThat(returned.getBalanceBefore()).isEqualTo(2);
        assertThat(returned.getBalanceAfter()).isEqualTo(4);
    }

    /** Thiếu một SKU phải rollback các SKU đã giữ trước đó, đơn và movements cùng transaction. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void insufficientVersionRollsBackEntireMultiSkuOrder() throws Exception {
        long root = create(7000000);
        addVersion(root, "40mm", List.of());
        long second = lastVersion(addVersion(root, "44mm", List.of()));
        long child = lastSku(addColor(root, second, "Ánh sao", 8000000));
        receive(root, 4);
        receive(child, 1);
        long orderCount = orders.count();
        long ledger = movements.search(null, Pageable.unpaged()).getTotalElements();
        place(List.of(item(child, 2), item(root, 1))).andExpect(status().isConflict());
        stock(root, 4, 0);
        stock(child, 1, 0);
        assertThat(orders.count()).isEqualTo(orderCount);
        assertThat(movements.search(null, Pageable.unpaged()).getTotalElements()).isEqualTo(ledger);
    }

    /** Tranh cùng một SKU không được lấy hàng của phiên bản khác dù cùng model và cùng màu. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentPurchasesCannotOversellOrConsumeOtherVersion() throws Exception {
        long root = create(7000000);
        addVersion(root, "40mm", List.of());
        long second = lastVersion(addVersion(root, "44mm", List.of()));
        long child = lastSku(addColor(root, second, "Ánh sao", 8000000));
        receive(root, 5);
        receive(child, 3);
        List<Integer> results = race(20, i -> place(List.of(item(child, 1))).andReturn().getResponse().getStatus());
        assertThat(results.stream().filter(value -> value == 201)).hasSize(3);
        assertThat(results.stream().filter(value -> value == 409)).hasSize(17);
        stock(child, 0, 3);
        stock(root, 5, 0);
    }

    /** Khóa model tuần tự hóa ADMIN; hai request trùng tên chỉ tạo một cấu hình mới. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentAdminsCannotCreateDuplicateVersions() throws Exception {
        long root = create(7000000);
        addVersion(root, "40mm", List.of());
        long before = versions.count();
        List<Integer> result = race(2, i -> send(post("/api/v1/products/{id}/versions", root),
                Map.of("name", "44mm"), admin).andReturn().getResponse().getStatus());
        assertThat(result).containsExactlyInAnyOrder(201, 409);
        assertThat(versions.count()).isEqualTo(before + 1);
    }

    /** Fixture tạo model qua API; thông số chỉ là dữ liệu kiểm thử, không phải cấu hình thiết bị thật. */
    private long create(int price) throws Exception {
        return body(send(post("/api/v1/products"), Map.of(
                "sku", "VERSION-" + UUID.randomUUID(), "name", "Đồng hồ kiểm thử",
                "category_id", category.getId(), "unit_price", price, "status", "ACTIVE",
                "image_url", "/assets/watch-star.jpg", "specifications", List.of(
                        Map.of("name", "Chipset", "value", "Chip kiểm thử"),
                        Map.of("name", "Kích thước", "value", "Chưa chọn"))), admin)
                .andExpect(status().isCreated())).path("id").asLong();
    }

    /** API khai báo phiên bản đầu nhận màu gốc; các phiên bản sau chưa tự tạo SKU. */
    private JsonNode addVersion(long root, String name, List<Map<String, String>> specs) throws Exception {
        return body(send(post("/api/v1/products/{id}/versions", root), Map.of(
                "name", name, "default_color_name", "Ánh sao", "specifications", specs), admin)
                .andExpect(status().isCreated()));
    }

    /** Mỗi màu mới có mã ngẫu nhiên và ảnh riêng, không tự dùng ảnh màu khác. */
    private Map<String, Object> color(long version, String name, int price) {
        return Map.of("version_id", version, "sku", "VERSION-COLOR-" + UUID.randomUUID(),
                "color_name", name, "unit_price", price, "image_url", "/assets/watch-new.jpg");
    }

    /** Đọc response đầy đủ của model sau khi thêm SKU. */
    private JsonNode addColor(long root, long version, String name, int price) throws Exception {
        return body(send(post("/api/v1/products/{id}/variants", root), color(version, name, price), admin)
                .andExpect(status().isCreated()));
    }

    /** ID cấu hình cuối dùng cho form thêm màu; không phải ID tồn kho. */
    private long lastVersion(JsonNode root) {
        return root.path("versions").get(root.path("versions").size() - 1).path("id").asLong();
    }

    /** SKU cuối dùng cho nhập kho và checkout như contract cũ. */
    private long lastSku(JsonNode root) {
        return root.path("variants").get(root.path("variants").size() - 1).path("sku_product_id").asLong();
    }

    /** Gửi JWT thật để đi qua security, method authorization và Bean Validation. */
    private ResultActions send(MockHttpServletRequestBuilder request, Map<String, ?> payload, String actor) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + actor)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)));
    }

    /** Parse UTF-8 để không mất dấu trong tên cấu hình và màu. */
    private JsonNode body(ResultActions response) throws Exception {
        return json.readTree(response.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** Đọc công khai như khách vãng lai. */
    private JsonNode detail(long id) throws Exception {
        return body(mvc.perform(get("/api/v1/products/{id}", id)).andExpect(status().isOk()));
    }

    /** Actor ngẫu nhiên để các bài kiểm thử không dùng mật khẩu demo. */
    private String token(String role) {
        return jwt.generateToken(users.save(verifiedUser("version-" + UUID.randomUUID() + "@example.com",
                "hash", "Người thử cấu hình", roles.findByName(role).orElseThrow())));
    }

    /** Nhập đúng SKU thông qua API nghiệp vụ có ledger. */
    private void receive(long sku, int quantity) throws Exception {
        send(post("/api/v1/inventories/stock-in"), Map.of("product_id", sku,
                "warehouse_id", warehouse.getId(), "quantity", quantity), admin).andExpect(status().isCreated());
    }

    /** Snapshot tồn từ database sau bulk reserve, không đọc entity cache cũ. */
    private void stock(long sku, int available, int reserved) {
        entities.clear();
        var row = inventories.findByProductIdAndWarehouseId(sku, warehouse.getId()).orElseThrow();
        assertThat(row.getAvailableQuantity()).isEqualTo(available);
        assertThat(row.getReservedQuantity()).isEqualTo(reserved);
    }

    /** Một dòng đơn giữ khóa SKU, không dùng ID phiên bản hoặc ID mapping màu. */
    private Map<String, Object> item(long sku, int quantity) {
        return Map.of("product_id", sku, "quantity", quantity);
    }

    /** Đặt đơn thật để kiểm tra transaction nghiệp vụ. */
    private ResultActions place(List<Map<String, Object>> items) throws Exception {
        return send(post("/api/v1/orders"), Map.of(
                "warehouse_id", warehouse.getId(), "items", items, "delivery", deliveryPayload()), customer);
    }

    /** API vòng đời giữ idempotency hiện tại, không mock service kho. */
    private void operate(long order, String action, String actor) throws Exception {
        send(post("/api/v1/orders/{id}/" + action, order), Map.of(), actor).andExpect(status().isOk());
    }

    /** Đồng bộ thời điểm xuất phát; transaction thật của từng request phải commit/rollback độc lập. */
    private List<Integer> race(int count, Request request) throws Exception {
        var executor = Executors.newFixedThreadPool(Math.min(count, 8));
        var start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < count; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    start.await();
                    return request.run(index);
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(20, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            executor.shutdownNow();
        }
    }

    /** Callback có exception để assertion đi qua HTTP thật trong các thread. */
    @FunctionalInterface
    private interface Request {
        int run(int index) throws Exception;
    }
}
