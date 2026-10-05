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
import com.stockflow.catalog.repository.ProductVariantRepository;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

/** HTTP/JWT/persistence thật: thông số, màu độc lập, quyền catalog và reserve đúng SKU trong transaction. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductOptionsIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
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

    /** Dữ liệu riêng cho mỗi ca, rollback sau test; không sửa catalog thực tế của người dùng. */
    @BeforeEach
    void setup() {
        category = categories.save(new Category("Thông số " + UUID.randomUUID(), "spec-" + UUID.randomUUID()));
        warehouse = warehouses.save(new Warehouse(
                "COLOR-" + UUID.randomUUID().toString().substring(0, 8), "Kho thử màu", "Hà Nội", WarehouseStatus.ACTIVE));
        admin = token("ADMIN");
        customer = token("CUSTOMER");
    }

    /** Thứ tự/tiếng Việt giữ nguyên sau clear JPA; khách đọc bảng đã lưu, không phải dữ liệu UI giả. */
    @Test
    void createsOrderedSpecificationsAndGuestReadsAfterReload() throws Exception {
        var root = create(List.of(Map.of("name", "  Chipset  ", "value", "  Chip kiểm thử  "),
                Map.of("name", "Bộ nhớ trong", "value", "256 GB")));
        entities.flush();
        entities.clear();
        mvc.perform(get("/api/v1/products/{id}", root.path("id").asLong()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.specifications[0].name").value("Chipset"))
                .andExpect(jsonPath("$.specifications[0].value").value("Chip kiểm thử"))
                .andExpect(jsonPath("$.specifications[1].name").value("Bộ nhớ trong"));
    }

    /** POST cũ bỏ qua thông số/màu tiếp tục hoạt động và không sinh cấu hình giả. */
    @Test
    void legacyProductHasEmptySpecificationsAndVariants() throws Exception {
        var root = create(null);
        assertThat(root.path("specifications").isEmpty()).isTrue();
        assertThat(root.path("variants").isEmpty()).isTrue();
    }

    /** PATCH bỏ qua/null giữ nguyên; [] xóa bảng thông số, không đổi ảnh hoặc mô tả. */
    @Test
    void patchSpecificationsPreservesNullReplacesAndClears() throws Exception {
        long id = create(specs()).path("id").asLong();
        update(id, Map.of("name", "Tên mới"), admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.specifications[0].name").value("Chipset"));
        var nullable = new HashMap<String, Object>();
        nullable.put("specifications", null);
        update(id, nullable, admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.specifications.length()").value(2));
        update(id, Map.of("specifications", List.of(Map.of("name", "RAM", "value", "16 GB"))), admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.specifications[0].name").value("RAM"));
        update(id, Map.of("specifications", List.of()), admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.specifications.length()").value(0))
                .andExpect(jsonPath("$.description").value("Nội dung kiểm thử"));
    }

    /** Dòng thiếu tên hoặc giá trị bị Bean Validation chặn trước khi ghi database. */
    @ParameterizedTest
    @ValueSource(strings = {"name", "value"})
    void rejectsBlankSpecification(String field) throws Exception {
        var row = new HashMap<>(Map.of("name", "Chipset", "value", "Chip thử"));
        row.put(field, "   ");
        var payload = payload();
        payload.put("specifications", List.of(row));
        long before = products.count();
        send(post("/api/v1/products"), payload, admin).andExpect(status().isBadRequest());
        assertThat(products.count()).isEqualTo(before);
    }

    /** Trùng nhãn sau chuẩn hóa không làm thay giá hoặc thay một phần bảng thông số. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rejectsDuplicateSpecificationAndRollsBackOtherChanges() throws Exception {
        long id = create(specs()).path("id").asLong();
        update(id, Map.of("unit_price", 1, "specifications", List.of(
                Map.of("name", "RAM", "value", "8 GB"), Map.of("name", " ram ", "value", "16 GB"))), admin)
                .andExpect(status().isBadRequest());
        entities.clear();
        mvc.perform(get("/api/v1/products/{id}", id)).andExpect(jsonPath("$.unit_price").value(25000000))
                .andExpect(jsonPath("$.specifications.length()").value(2));
    }

    /** Chặn null, quá dài và quá nhiều dòng để không tràn schema. */
    @ParameterizedTest
    @ValueSource(strings = {"null", "name", "value", "count"})
    void rejectsInvalidSpecificationBounds(String kind) throws Exception {
        List<Object> rows = new ArrayList<>();
        switch (kind) {
            case "null" -> rows.add(null);
            case "name" -> rows.add(Map.of("name", "x".repeat(101), "value", "v"));
            case "value" -> rows.add(Map.of("name", "n", "value", "x".repeat(1001)));
            default -> {
                for (int i = 0; i < 61; i++) rows.add(Map.of("name", "Thông số " + i, "value", "v"));
            }
        }
        var payload = payload();
        payload.put("specifications", rows);
        send(post("/api/v1/products"), payload, admin).andExpect(status().isBadRequest());
    }

    /** Màu gốc dùng đúng SKU cũ, màu mới có SKU khác, giá/ảnh riêng và không tự nhập tồn. */
    @Test
    void groupsTwoColorsWithoutChangingOriginalSkuOrCreatingStock() throws Exception {
        var root = create(specs());
        long stocks = inventories.count();
        long ledger = movements.search(null, org.springframework.data.domain.Pageable.unpaged()).getTotalElements();
        var result = add(root.path("id").asLong(), colorPayload());
        assertThat(result.path("variants").size()).isEqualTo(2);
        assertThat(result.path("variants").get(0).path("sku_product_id").asLong()).isEqualTo(root.path("id").asLong());
        assertThat(result.path("variants").get(0).path("sku").asText()).isEqualTo(root.path("sku").asText());
        assertThat(result.path("variants").get(1).path("unit_price").asInt()).isEqualTo(26000000);
        assertThat(result.path("variants").get(1).path("image_url").asText()).isEqualTo("/assets/blue.jpg");
        assertThat(result.path("specifications")).isEqualTo(root.path("specifications"));
        assertThat(inventories.count()).isEqualTo(stocks);
        assertThat(movements.search(null, org.springframework.data.domain.Pageable.unpaged()).getTotalElements()).isEqualTo(ledger);
    }

    /** Gộp tại SQL trước phân trang: hai màu chỉ chiếm một kết quả và SKU cũ vẫn tra cứu được. */
    @Test
    void groupedPaginationReturnsOneModelWhileLegacyListReturnsBothSkus() throws Exception {
        long id = create(specs()).path("id").asLong();
        var result = add(id, colorPayload());
        long child = childId(result);
        mvc.perform(get("/api/v1/products").param("categoryId", category.getId().toString())
                        .param("grouped", "true").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total_elements").value(1))
                .andExpect(jsonPath("$.content[0].variants.length()").value(2));
        mvc.perform(get("/api/v1/products").param("categoryId", category.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total_elements").value(2));
        mvc.perform(get("/api/v1/products/{id}", child)).andExpect(status().isOk())
                .andExpect(jsonPath("$.parent_product_id").value(id));
        mvc.perform(get("/api/v1/products").param("grouped", "true")
                        .param("q", result.path("variants").get(1).path("sku").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total_elements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id));
    }

    /** Thiếu giá kế thừa giá gốc, ảnh mới để trống chứ không giả dùng ảnh của màu khác. */
    @Test
    void newColorDefaultsPriceAndKeepsImageEmpty() throws Exception {
        long id = create(null).path("id").asLong();
        var payload = colorPayload();
        payload.remove("unit_price");
        payload.remove("image_url");
        var variant = add(id, payload).path("variants").get(1);
        assertThat(variant.path("unit_price").asInt()).isEqualTo(25000000);
        assertThat(variant.path("image_url").isNull()).isTrue();
    }

    /** Không suy đoán màu của sản phẩm cũ từ tên; thiếu khai báo trả lỗi rõ ràng và không tạo SKU rác. */
    @Test
    void firstColorRequiresExplicitOriginalColor() throws Exception {
        long id = create(null).path("id").asLong();
        var payload = colorPayload();
        payload.remove("default_color_name");
        long before = products.count();
        send(post("/api/v1/products/{id}/variants", id), payload, admin).andExpect(status().isBadRequest());
        assertThat(products.count()).isEqualTo(before);
        assertThat(variants.findByProductIdOrderByIdAsc(id)).isEmpty();
    }

    /** Màu trùng với màu gốc hoặc một màu con bị chặn kể cả khác hoa/thường và khoảng trắng. */
    @ParameterizedTest
    @ValueSource(strings = {" cam ", " XANH "})
    void rejectsDuplicateColors(String color) throws Exception {
        long id = create(null).path("id").asLong();
        add(id, colorPayload());
        var payload = colorPayload();
        payload.put("sku", "OTHER-" + UUID.randomUUID());
        payload.put("color_name", color);
        long before = products.count();
        send(post("/api/v1/products/{id}/variants", id), payload, admin).andExpect(status().isConflict());
        assertThat(products.count()).isEqualTo(before);
    }

    /** SKU toàn hệ thống duy nhất, không chỉ trong một nhóm màu. */
    @Test
    void rejectsExistingSku() throws Exception {
        var root = create(null);
        var payload = colorPayload();
        payload.put("sku", root.path("sku").asText());
        send(post("/api/v1/products/{id}/variants", root.path("id").asLong()), payload, admin)
                .andExpect(status().isConflict());
    }

    /** Các dữ liệu không hợp lệ không để lại nhóm màu cấu hình dở. */
    @ParameterizedTest
    @ValueSource(strings = {"price", "hex", "blank", "photos"})
    void rejectsInvalidColorRequest(String field) throws Exception {
        long id = create(null).path("id").asLong();
        var payload = colorPayload();
        switch (field) {
            case "price" -> payload.put("unit_price", -1);
            case "hex" -> payload.put("color_hex", "red");
            case "blank" -> payload.put("color_name", " ");
            default -> payload.put("image_urls", List.of("/assets/x.jpg", " /assets/x.jpg "));
        }
        long before = products.count();
        send(post("/api/v1/products/{id}/variants", id), payload, admin).andExpect(status().isBadRequest());
        assertThat(products.count()).isEqualTo(before);
        assertThat(variants.findByProductIdOrderByIdAsc(id)).isEmpty();
    }

    /** CUSTOMER, MANAGER và STAFF không được sửa catalog màu hoặc thông số. */
    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "MANAGER", "WAREHOUSE_STAFF"})
    void onlyAdminMayManageColorsAndSpecifications(String role) throws Exception {
        long id = create(specs()).path("id").asLong();
        String actor = token(role);
        send(post("/api/v1/products/{id}/variants", id), colorPayload(), actor).andExpect(status().isForbidden());
        update(id, Map.of("specifications", List.of()), actor).andExpect(status().isForbidden());
        var root = add(id, colorPayload());
        send(patch("/api/v1/products/{id}/variants/{variant}", id, root.path("variants").get(1).path("id").asLong()),
                Map.of("unit_price", 1), actor).andExpect(status().isForbidden());
    }

    /** Không cho tạo nhóm màu con của một SKU màu hoặc sửa nhầm variant thuộc sản phẩm khác. */
    @Test
    void rejectsNestedGroupAndVariantFromOtherProduct() throws Exception {
        long id = create(null).path("id").asLong();
        var root = add(id, colorPayload());
        send(post("/api/v1/products/{id}/variants", childId(root)), colorPayload(), admin)
                .andExpect(status().isBadRequest());
        long other = create(null).path("id").asLong();
        send(patch("/api/v1/products/{id}/variants/{variant}", other, root.path("variants").get(1).path("id").asLong()),
                Map.of("unit_price", 1), admin).andExpect(status().isNotFound());
    }

    /** Thông số chung được cập nhật cho SKU con nhưng không ghi đè giá hay ảnh riêng. */
    @Test
    void commonContentUpdateKeepsColorPriceAndImage() throws Exception {
        long id = create(specs()).path("id").asLong();
        var root = add(id, colorPayload());
        update(id, Map.of("name", "Điện thoại tên mới", "unit_price", 24000000,
                "specifications", List.of(Map.of("name", "RAM", "value", "16 GB"))), admin)
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/products/{id}", childId(root)))
                .andExpect(jsonPath("$.unit_price").value(26000000))
                .andExpect(jsonPath("$.image_url").value("/assets/blue.jpg"))
                .andExpect(jsonPath("$.specifications[0].name").value("RAM"));
    }

    /** Sửa màu gốc chỉ thay trạng thái màu, không tắt trang chung hoặc mất màu khác. */
    @Test
    void disablingOriginalColorKeepsOtherColorAndBlocksOrderingIt() throws Exception {
        long id = create(null).path("id").asLong();
        var root = add(id, colorPayload());
        receive(id, 1);
        send(patch("/api/v1/products/{id}/variants/{variant}", id, root.path("variants").get(0).path("id").asLong()),
                Map.of("status", "INACTIVE"), admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.variants[0].status").value("INACTIVE"))
                .andExpect(jsonPath("$.variants[1].status").value("ACTIVE"));
        place(List.of(Map.of("product_id", id, "quantity", 1))).andExpect(status().isConflict());
    }

    /** Đặt màu xanh không lấy nhầm hàng cam; thanh toán/hủy hoàn đúng SKU và giữ ledger có snapshot chuẩn. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void orderReservesAndRestoresOnlySelectedColor() throws Exception {
        long id = create(null).path("id").asLong();
        long child = childId(add(id, colorPayload()));
        receive(id, 1);
        receive(child, 3);
        var order = body(place(List.of(Map.of("product_id", child, "quantity", 2))).andExpect(status().isCreated()));
        assertStock(id, 1, 0);
        assertStock(child, 1, 2);
        assertThat(order.path("total_amount").asInt()).isEqualTo(52000000);
        long inventoryId = inventories.findByProductIdAndWarehouseId(child, warehouse.getId()).orElseThrow().getId();
        var hold = movements.search(inventoryId, org.springframework.data.domain.Pageable.unpaged()).getContent().stream().filter(value -> value.getInventoryId().equals(inventoryId)
                && value.getType().name().equals("RESERVATION_HOLD")).toList();
        assertThat(hold).hasSize(1);
        assertThat(hold.get(0).getBalanceBefore()).isEqualTo(3);
        assertThat(hold.get(0).getBalanceAfter()).isEqualTo(3);
        send(post("/api/v1/orders/{id}/payment-simulations/confirm", order.path("id").asLong()), Map.of(), customer)
                .andExpect(status().isOk());
        assertStock(child, 1, 0);
        send(post("/api/v1/orders/{id}/cancel", order.path("id").asLong()), Map.of(), admin)
                .andExpect(status().isOk());
        assertStock(child, 3, 0);
        assertStock(id, 1, 0);
    }

    /** Một màu hết hàng rollback cả giỏ nhiều màu; không cộng dồn tồn các màu để bán sai lựa chọn. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void insufficientColorRollsBackAllReservations() throws Exception {
        long id = create(null).path("id").asLong();
        long child = childId(add(id, colorPayload()));
        receive(id, 3);
        receive(child, 1);
        long ledger = movements.search(null, org.springframework.data.domain.Pageable.unpaged()).getTotalElements();
        long orderCount = orders.count();
        place(List.of(Map.of("product_id", id, "quantity", 1), Map.of("product_id", child, "quantity", 2)))
                .andExpect(status().isConflict());
        assertStock(id, 3, 0);
        assertStock(child, 1, 0);
        assertThat(movements.search(null, org.springframework.data.domain.Pageable.unpaged()).getTotalElements()).isEqualTo(ledger);
        assertThat(orders.count()).isEqualTo(orderCount);
    }

    /** 24 request tranh ba sản phẩm cùng màu; transaction thật không được dùng tồn của màu khác. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentOrdersCannotOversellOneColor() throws Exception {
        long root = create(null).path("id").asLong();
        long child = childId(add(root, colorPayload()));
        receive(root, 4);
        receive(child, 3);
        var executor = Executors.newFixedThreadPool(8);
        var start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 24; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return place(List.of(Map.of("product_id", child, "quantity", 1)))
                            .andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (var result : results) statuses.add(result.get(20, TimeUnit.SECONDS));
            assertThat(statuses.stream().filter(status -> status == 201).count()).isEqualTo(3);
            assertThat(statuses.stream().filter(status -> status == 409).count()).isEqualTo(21);
            assertStock(child, 0, 3);
            assertStock(root, 4, 0);
        } finally {
            executor.shutdownNow();
        }
    }

    /** Hai ADMIN cùng thêm một màu: khóa gốc và UNIQUE chỉ cho một request thành công. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentDuplicateColorsCreateExactlyOneSku() throws Exception {
        long root = create(null).path("id").asLong();
        add(root, colorPayload());
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 2; i++) {
                results.add(executor.submit(() -> {
                    var payload = colorPayload();
                    payload.put("color_name", "Đỏ");
                    start.await();
                    return send(post("/api/v1/products/{id}/variants", root), payload, admin)
                            .andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (var result : results) statuses.add(result.get(20, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(variants.findByProductIdOrderByIdAsc(root)).hasSize(3);
        } finally {
            executor.shutdownNow();
        }
    }

    /** Ẩn sản phẩm gốc phải chặn đặt mọi SKU con, không thể lách bằng cách gửi trực tiếp ID màu. */
    @Test
    void inactiveParentBlocksChildOrdering() throws Exception {
        long root = create(null).path("id").asLong();
        long child = childId(add(root, colorPayload()));
        receive(child, 1);
        update(root, Map.of("status", "INACTIVE"), admin).andExpect(status().isOk());
        place(List.of(Map.of("product_id", child, "quantity", 1))).andExpect(status().isConflict());
    }

    /** API stock-in dùng SKU riêng đã tồn tại, không thay đổi contract warehouse scope của dự án. */
    private void receive(long productId, int quantity) throws Exception {
        send(post("/api/v1/inventories/stock-in"), Map.of(
                "product_id", productId, "warehouse_id", warehouse.getId(), "quantity", quantity), admin)
                .andExpect(status().isCreated());
    }

    /** Refresh sau bulk UPDATE để assertion đọc giá trị database, không đọc entity cache cũ. */
    private void assertStock(long id, int available, int reserved) {
        if (entities.isJoinedToTransaction()) entities.flush();
        entities.clear();
        var stock = inventories.findByProductIdAndWarehouseId(id, warehouse.getId()).orElseThrow();
        assertThat(stock.getAvailableQuantity()).isEqualTo(available);
        assertThat(stock.getReservedQuantity()).isEqualTo(reserved);
    }

    /** Đặt đơn với JWT CUSTOMER thật, giá chỉ lấy phía server. */
    private ResultActions place(List<Map<String, Object>> items) throws Exception {
        return send(post("/api/v1/orders"), Map.of(
                "warehouse_id", warehouse.getId(), "items", items, "delivery", deliveryPayload()), customer);
    }

    /** Payload tối thiểu; tên không chứa cấu hình thật của thiết bị ngoài thị trường. */
    private Map<String, Object> payload() {
        var result = new HashMap<String, Object>();
        result.put("sku", "OPTIONS-" + UUID.randomUUID());
        result.put("name", "Điện thoại kiểm thử");
        result.put("category_id", category.getId());
        result.put("unit_price", 25000000);
        result.put("status", "ACTIVE");
        result.put("description", "Nội dung kiểm thử");
        result.put("image_url", "/assets/orange.jpg");
        return result;
    }

    /** Hai dòng thông số có dữ liệu xác định để đối chiếu thứ tự. */
    private List<Map<String, String>> specs() {
        return List.of(Map.of("name", "Chipset", "value", "Chip kiểm thử"),
                Map.of("name", "Bộ nhớ trong", "value", "256 GB"));
    }

    /** Tạo sản phẩm qua HTTP thật và parse response UTF-8. */
    private JsonNode create(List<Map<String, String>> specifications) throws Exception {
        var payload = payload();
        if (specifications != null) payload.put("specifications", specifications);
        return body(send(post("/api/v1/products"), payload, admin).andExpect(status().isCreated()));
    }

    /** Hai màu có giá/ảnh khác nhau, màu gốc giữ nguyên SKU cũ. */
    private Map<String, Object> colorPayload() {
        var result = new HashMap<String, Object>();
        result.put("sku", "BLUE-" + UUID.randomUUID());
        result.put("color_name", "Xanh");
        result.put("color_hex", "#2563eb");
        result.put("default_color_name", "Cam");
        result.put("default_color_hex", "#ea580c");
        result.put("unit_price", 26000000);
        result.put("image_url", "/assets/blue.jpg");
        return result;
    }

    /** Response tạo màu trả cả trang gốc, thuận tiện đối chiếu mapping và dữ liệu chung. */
    private JsonNode add(long id, Map<String, Object> payload) throws Exception {
        return body(send(post("/api/v1/products/{id}/variants", id), payload, admin).andExpect(status().isCreated()));
    }

    /** ID Product của màu mới chính là khóa đặt hàng/nhập kho. */
    private long childId(JsonNode root) {
        return root.path("variants").get(1).path("sku_product_id").asLong();
    }

    /** PATCH sản phẩm gốc với JWT thật. */
    private ResultActions update(long id, Map<String, ?> payload, String actor) throws Exception {
        return send(patch("/api/v1/products/{id}", id), payload, actor);
    }

    /** Hàm dùng chung để mọi test đi qua security filter và Bean Validation. */
    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               Map<String, ?> payload, String actor) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + actor)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)));
    }

    /** Đọc UTF-8 rõ ràng để dữ liệu thông số/màu có dấu không bị thay đổi khi assertion. */
    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** Tài khoản kiểm thử ngẫu nhiên, không phụ thuộc mật khẩu demo và không đụng database thật. */
    private String token(String role) {
        return jwt.generateToken(users.save(verifiedUser("options-" + UUID.randomUUID() + "@example.com",
                "hash-kiểm-thử", "Người thử màu", roles.findByName(role).orElseThrow())));
    }
}
