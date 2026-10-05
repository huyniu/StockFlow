package com.stockflow.catalog.api;

import static com.stockflow.user.support.UserTestFixtures.verifiedUser;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.stockflow.inventory.repository.InventoryRepository;
import com.stockflow.user.domain.User;
import com.stockflow.user.repository.RoleRepository;
import com.stockflow.user.repository.UserRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kiểm chứng khám phá catalog qua HTTP thật: khoảng giá, tổ hợp điều kiện, thứ tự và metadata phân trang.
 * Fixture rollback theo từng ca; không tải ảnh, không ghi catalog hoặc tồn kho của người dùng.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CatalogDiscoveryIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired InventoryRepository inventories;
    @Autowired JdbcTemplate jdbc;
    @Autowired RoleRepository roles;
    @Autowired UserRepository users;
    @Autowired JwtTokenProvider jwt;

    private Category category;
    private String skuPrefix;
    private Product economy;
    private Product lower;
    private Product middleA;
    private Product middleB;
    private Product upper;
    private Product premium;

    /** Hai sản phẩm cùng giá nằm sát biên trang để phát hiện thứ tự không ổn định hoặc lọc sau phân trang. */
    @BeforeEach
    void prepareCatalog() {
        skuPrefix = "DISCOVERY-" + UUID.randomUUID();
        category = categories.save(new Category("Thiết bị " + skuPrefix, "thiet-bi-" + skuPrefix.toLowerCase()));
        economy = create("ECONOMY", "Thiết bị phổ thông", "500000.00", ProductStatus.ACTIVE, category);
        lower = create("LOWER", "Thiết bị cơ bản", "1000000.00", ProductStatus.ACTIVE, category);
        middleA = create("MID-A", "Thiết bị A", "1500000.25", ProductStatus.ACTIVE, category);
        middleB = create("MID-B", "Thiết bị B", "1500000.25", ProductStatus.ACTIVE, category);
        upper = create("UPPER", "Thiết bị cao cấp", "3000000.00", ProductStatus.ACTIVE, category);
        premium = create("PREMIUM", "Thiết bị chuyên nghiệp", "25000000.00", ProductStatus.ACTIVE, category);
        create("MID-STOP", "Thiết bị ngừng bán", "2000000.00", ProductStatus.INACTIVE, category);
        Category other = categories.save(new Category("Khác " + skuPrefix, "khac-" + skuPrefix.toLowerCase()));
        create("MID-OTHER", "Thiết bị khác danh mục", "1500000.25", ProductStatus.ACTIVE, other);
    }

    /** Request cũ thiếu tham số giá vẫn đọc công khai và dùng metadata như trước. */
    @Test
    void legacyRequestRemainsPublic() throws Exception {
        JsonNode response = response(catalog());
        assertThat(ids(response)).containsExactly(
                economy.getId(), lower.getId(), middleA.getId(), middleB.getId(), upper.getId(), premium.getId());
        assertThat(response.path("total_elements").asLong()).isEqualTo(6);
    }

    /** Giá đúng hai đầu mút được giữ, dữ liệu ngoài khoảng/danh mục/trạng thái bị loại tại database. */
    @Test
    void rangeIncludesBothBoundaries() throws Exception {
        JsonNode response = response(catalog().param("minPrice", "1000000").param("maxPrice", "3000000"));
        assertThat(ids(response)).containsExactly(lower.getId(), middleA.getId(), middleB.getId(), upper.getId());
        assertThat(response.path("total_elements").asLong()).isEqualTo(4);
    }

    /** Chỉ có giá từ nghĩa là không giới hạn giá đến. */
    @Test
    void acceptsMinimumOnly() throws Exception {
        assertThat(ids(response(catalog().param("minPrice", "3000000"))))
                .containsExactly(upper.getId(), premium.getId());
    }

    /** Chỉ có giá đến nghĩa là không giới hạn giá từ. */
    @Test
    void acceptsMaximumOnly() throws Exception {
        assertThat(ids(response(catalog().param("maxPrice", "1000000"))))
                .containsExactly(economy.getId(), lower.getId());
    }

    /** Giá thập phân được so sánh bằng BigDecimal, không làm tròn sai giá tại biên. */
    @Test
    void exactDecimalRangeMatchesBothProducts() throws Exception {
        assertThat(ids(response(catalog().param("minPrice", "1500000.25").param("maxPrice", "1500000.25"))))
                .containsExactly(middleA.getId(), middleB.getId());
    }

    /** Schema chấp nhận giá bằng 0; bộ lọc phải coi 0 là điều kiện thật thay vì bỏ tham số. */
    @Test
    void zeroPriceBoundIsNotIgnored() throws Exception {
        Product free = create("FREE", "Thiết bị giá không", "0.00", ProductStatus.ACTIVE, category);
        assertThat(ids(response(catalog().param("minPrice", "0").param("maxPrice", "0"))))
                .containsExactly(free.getId());
    }

    /** Khoảng giá phải kết hợp với từ khóa SKU, trạng thái và danh mục trong cùng truy vấn. */
    @Test
    void combinesKeywordCategoryStatusAndPrice() throws Exception {
        assertThat(ids(response(catalog()
                .param("q", skuPrefix.toLowerCase() + "-mid")
                .param("minPrice", "1000000")
                .param("maxPrice", "2000000"))))
                .containsExactly(middleA.getId(), middleB.getId());
    }

    /** Hai mặt hàng cùng giá nằm trên hai trang phải xuất hiện một lần và tổng đếm phải là số sau lọc. */
    @Test
    void ascendingPricePaginationHasStableTieOrder() throws Exception {
        JsonNode first = response(catalog().param("minPrice", "1000000").param("maxPrice", "3000000")
                .param("sort", "unitPrice,asc").param("size", "2").param("page", "0"));
        JsonNode second = response(catalog().param("minPrice", "1000000").param("maxPrice", "3000000")
                .param("sort", "unitPrice,asc").param("size", "2").param("page", "1"));
        assertThat(ids(first)).containsExactly(lower.getId(), middleA.getId());
        assertThat(ids(second)).containsExactly(middleB.getId(), upper.getId());
        assertThat(response(catalog().param("minPrice", "1000000").param("maxPrice", "3000000")
                .param("sort", "unitPrice,asc").param("size", "2")).path("total_elements").asLong()).isEqualTo(4);
        assertThat(first.path("total_pages").asInt()).isEqualTo(2);
        assertThat(first.path("last").asBoolean()).isFalse();
        assertThat(second.path("last").asBoolean()).isTrue();
    }

    /** Giá giảm dần vẫn dùng ID tăng dần làm thứ tự phụ cho nhóm cùng giá. */
    @Test
    void descendingPriceUsesStableTieOrder() throws Exception {
        assertThat(ids(response(catalog().param("sort", "unitPrice,desc"))))
                .containsExactly(premium.getId(), upper.getId(), middleA.getId(), middleB.getId(), lower.getId(), economy.getId());
    }

    /** Chấp nhận tên snake_case của trường giá để API nhất quán với response hiện có. */
    @Test
    void acceptsSnakeCasePriceSort() throws Exception {
        assertThat(ids(response(catalog().param("sort", "unit_price,desc").param("size", "2"))))
                .containsExactly(premium.getId(), upper.getId());
    }

    /** Nếu client gửi thứ tự phụ hợp lệ, giữ thứ tự đó rồi mới bổ sung ID. */
    @Test
    void preservesExplicitSecondarySort() throws Exception {
        assertThat(ids(response(catalog().param("minPrice", "1500000.25").param("maxPrice", "1500000.25")
                .param("sort", "unitPrice,asc", "name,desc"))))
                .containsExactly(middleB.getId(), middleA.getId());
    }

    /** Kết quả rỗng vẫn trả 200 cùng metadata đúng, để frontend hiển thị xóa bộ lọc. */
    @Test
    void emptyRangeReturnsEmptyPage() throws Exception {
        JsonNode response = response(catalog().param("minPrice", "3000001").param("maxPrice", "4000000"));
        assertThat(ids(response)).isEmpty();
        assertThat(response.path("total_elements").asLong()).isZero();
        assertThat(response.path("total_pages").asInt()).isZero();
    }

    /** Giá từ lớn hơn giá đến trả lỗi nghiệp vụ rõ, không trả trang rỗng gây hiểu nhầm. */
    @Test
    void invertedPriceRangeReturnsBadRequest() throws Exception {
        mvc.perform(catalog().param("minPrice", "3000000").param("maxPrice", "1000000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Giá từ không được lớn hơn giá đến."));
    }

    /** Giá âm, quá giới hạn, sai độ chính xác hoặc sai định dạng đều bị chặn cho cả hai đầu mút. */
    @ParameterizedTest
    @MethodSource("invalidPriceBounds")
    void invalidPriceBoundReturnsBadRequest(String parameter, String value) throws Exception {
        mvc.perform(catalog().param(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    /** Chặn sắp xếp qua collection/quan hệ hoặc tên trường sai để tránh nhân bản hàng và lỗi 500. */
    @ParameterizedTest
    @ValueSource(strings = {"imageUrls", "category.name", "unknown"})
    void unsupportedSortReturnsBadRequest(String property) throws Exception {
        mvc.perform(catalog().param("sort", property + ",asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Trường sắp xếp sản phẩm không được hỗ trợ."));
    }

    /** Đọc catalog tiếp tục công khai cho mọi role; quyền sửa catalog vẫn do các test authorization bảo vệ. */
    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "WAREHOUSE_STAFF", "MANAGER", "ADMIN"})
    void authenticatedRolesCanReadPriceFilteredCatalog(String role) throws Exception {
        User actor = users.save(verifiedUser(
                "discovery-" + UUID.randomUUID() + "@example.com", "unused-password-hash", "Người xem catalog",
                roles.findByName(role).orElseThrow()));
        mvc.perform(catalog().param("maxPrice", "1000000")
                        .header("Authorization", "Bearer " + jwt.generateToken(actor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elements").value(2));
    }

    /** Lọc/sắp xếp là thao tác đọc; giá, gallery, tồn kho và ledger không phát sinh thay đổi. */
    @Test
    void discoveryPreservesCatalogAndInventory() throws Exception {
        middleA.replaceImageUrls(List.of("/assets/front.jpg", "/assets/back.jpg"));
        products.flush();
        long inventoryCount = inventories.count();
        long movementCount = movementCount();
        long productCount = products.count();
        JsonNode response = response(catalog().param("q", skuPrefix + "-MID-A")
                .param("minPrice", "1000000").param("maxPrice", "2000000").param("sort", "unitPrice,desc"));
        assertThat(response.path("content").get(0).path("image_urls").get(1).asText()).isEqualTo("/assets/back.jpg");
        assertThat(products.findById(middleA.getId()).orElseThrow().getUnitPrice()).isEqualByComparingTo("1500000.25");
        assertThat(products.count()).isEqualTo(productCount);
        assertThat(inventories.count()).isEqualTo(inventoryCount);
        assertThat(movementCount()).isEqualTo(movementCount);
    }

    /** Fixture riêng theo UUID để tổng đếm không phụ thuộc các suite hoặc seed demo khác. */
    private Product create(String suffix, String name, String price, ProductStatus status, Category group) {
        return products.save(new Product(group, skuPrefix + "-" + suffix, name, new BigDecimal(price), status));
    }

    /** Request đọc không gửi token để kiểm chứng storefront của khách vãng lai. */
    private MockHttpServletRequestBuilder catalog() {
        return get("/api/v1/products").param("categoryId", category.getId().toString()).param("status", "ACTIVE");
    }

    /** Parse response bằng UTF-8, giữ tên và thông báo tiếng Việt nguyên vẹn. */
    private JsonNode response(MockHttpServletRequestBuilder request) throws Exception {
        return json.readTree(mvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** So sánh ID và thứ tự thực tế, không chỉ kiểm tra số hàng trên trang. */
    private List<Long> ids(JsonNode response) {
        List<Long> ids = new ArrayList<>();
        response.path("content").forEach(product -> ids.add(product.path("id").asLong()));
        return ids;
    }

    /** Đếm sổ cái bằng truy vấn chỉ đọc vì repository bất biến không công khai các hàm CRUD tổng quát. */
    private long movementCount() {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM inventory_movements
                """, Long.class);
    }

    /** Mỗi giá sai được dùng cho cả minPrice và maxPrice để không bỏ sót một nhánh binding/validation. */
    private static Stream<Arguments> invalidPriceBounds() {
        return Stream.of("minPrice", "maxPrice").flatMap(parameter -> Stream.of(
                "-1", "10000000000", "0.001", "NaN", "Infinity", "abc", "1,000,000")
                .map(value -> Arguments.of(parameter, value)));
    }
}
