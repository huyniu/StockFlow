package com.stockflow.warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.auth.security.JwtTokenProvider;
import com.stockflow.catalog.domain.Category;
import com.stockflow.catalog.domain.Product;
import com.stockflow.catalog.domain.ProductStatus;
import com.stockflow.catalog.repository.CategoryRepository;
import com.stockflow.catalog.repository.ProductRepository;
import com.stockflow.user.domain.User;
import com.stockflow.user.repository.RoleRepository;
import com.stockflow.user.repository.UserRepository;
import com.stockflow.warehouse.domain.Warehouse;
import com.stockflow.warehouse.domain.WarehouseStatus;
import com.stockflow.warehouse.repository.WarehouseRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Kiểm chứng contract tối thiểu cho hai giao diện, tìm kiếm thật và phân công kho qua JWT thật. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StorefrontApiIntegrationTest {

    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired WarehouseRepository warehouses;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired JwtTokenProvider jwt;
    @Autowired JdbcTemplate jdbc;

    /** Khách vãng lai chọn được chi nhánh ACTIVE nhưng không thấy địa chỉ hoặc số tồn nội bộ. */
    @Test
    void anonymousBranchesExposeOnlyMinimalActiveChoices() throws Exception {
        Warehouse active = warehouse(WarehouseStatus.ACTIVE);
        Warehouse inactive = warehouse(WarehouseStatus.INACTIVE);
        JsonNode response = response("/api/v1/storefront/branches", null);
        List<JsonNode> rows = rows(response);
        JsonNode branch = rows.stream()
                .filter(row -> row.path("id").asLong() == active.getId())
                .findFirst().orElseThrow();
        assertThat(branch.size()).isEqualTo(3);
        assertThat(branch.path("name").asText()).isEqualTo(active.getName());
        assertThat(branch.path("code").asText()).isEqualTo(active.getCode());
        assertThat(branch.has("address")).isFalse();
        assertThat(rows).noneMatch(row -> row.path("id").asLong() == inactive.getId());
    }

    /** Endpoint công khai mới không bỏ yêu cầu JWT của lựa chọn kho vận hành hoặc contract cũ. */
    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/warehouses/order-options", "/api/v1/warehouses/operating-options"})
    void privateWarehouseOptionsStillRequireAuthentication(String path) throws Exception {
        http.perform(get(path)).andExpect(status().isUnauthorized());
    }

    /** Quản lý và admin cần cả kho ngừng bán để hoàn tất các đơn lịch sử; DTO vẫn tối thiểu. */
    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "MANAGER"})
    void managementCanSelectAllOperatingWarehouses(String role) throws Exception {
        Warehouse active = warehouse(WarehouseStatus.ACTIVE);
        Warehouse inactive = warehouse(WarehouseStatus.INACTIVE);
        List<JsonNode> options = rows(response("/api/v1/warehouses/operating-options", actor(role)));
        assertThat(options.stream().map(row -> row.path("id").asLong()).toList())
                .contains(active.getId(), inactive.getId());
        assertThat(options).allMatch(row -> row.size() == 3 && !row.has("address"));
    }

    /** Staff nhận đúng kho được phân công, thu hồi phân công có hiệu lực ngay với token đã phát. */
    @Test
    void staffOptionsRespectCurrentAssignmentsIncludingRevocation() throws Exception {
        User staff = actor("WAREHOUSE_STAFF");
        Warehouse first = warehouse(WarehouseStatus.ACTIVE);
        Warehouse second = warehouse(WarehouseStatus.INACTIVE);
        Warehouse outside = warehouse(WarehouseStatus.ACTIVE);
        assign(staff, first);
        assign(staff, second);
        String token = jwt.generateToken(staff);
        assertThat(ids(responseWithToken("/api/v1/warehouses/operating-options", token)))
                .containsExactly(first.getId(), second.getId())
                .doesNotContain(outside.getId());
        jdbc.update("""
                DELETE FROM warehouse_staff_assignments
                WHERE user_id = ?
                    AND warehouse_id = ?
                """, staff.getId(), first.getId());
        assertThat(ids(responseWithToken("/api/v1/warehouses/operating-options", token)))
                .containsExactly(second.getId());
    }

    /** Staff chưa được phân công nhận lựa chọn rỗng, không suy đoán kho Hà Nội theo tài khoản. */
    @Test
    void unassignedStaffGetsNoOperatingWarehouses() throws Exception {
        assertThat(rows(response("/api/v1/warehouses/operating-options", actor("WAREHOUSE_STAFF"))))
                .isEmpty();
    }

    /** Khách đăng nhập không được truy cập lựa chọn vận hành dù biết URL từ source frontend. */
    @Test
    void customerCannotReadOperatingWarehouses() throws Exception {
        http.perform(get("/api/v1/warehouses/operating-options")
                        .header("Authorization", "Bearer " + jwt.generateToken(actor("CUSTOMER"))))
                .andExpect(status().isForbidden());
    }

    /** Từ khóa tìm toàn catalog trước phân trang và kết hợp ACTIVE/danh mục, không lọc một trang ở JS. */
    @Test
    void productSearchCombinesNameCategoryStatusAndDatabasePagination() throws Exception {
        String term = "TÌM-" + UUID.randomUUID();
        Category selected = category();
        Product first = product(selected, term + " một", ProductStatus.ACTIVE);
        Product second = product(selected, term + " hai", ProductStatus.ACTIVE);
        product(selected, term + " ngừng bán", ProductStatus.INACTIVE);
        product(category(), term + " danh mục khác", ProductStatus.ACTIVE);
        String path = "/api/v1/products?categoryId=" + selected.getId()
                + "&status=ACTIVE&size=1&sort=id,asc&q=" + term.toLowerCase(java.util.Locale.ROOT);
        JsonNode pageZero = response(path + "&page=0", null);
        JsonNode pageOne = response(path + "&page=1", null);
        assertThat(pageZero.path("total_elements").asLong()).isEqualTo(2);
        assertThat(pageZero.path("total_pages").asInt()).isEqualTo(2);
        assertThat(pageZero.path("content").get(0).path("id").asLong()).isEqualTo(first.getId());
        assertThat(pageOne.path("content").get(0).path("id").asLong()).isEqualTo(second.getId());
    }

    /** SKU tìm không phân biệt hoa/thường; từ khóa không trùng tên vẫn trả đúng sản phẩm. */
    @Test
    void productSearchMatchesSkuCaseInsensitively() throws Exception {
        Product selected = product(category(), "Tên sản phẩm khác", ProductStatus.ACTIVE);
        JsonNode result = response("/api/v1/products?q=" + selected.getSku().toLowerCase(java.util.Locale.ROOT), null);
        assertThat(result.path("total_elements").asLong()).isEqualTo(1);
        assertThat(result.path("content").get(0).path("id").asLong()).isEqualTo(selected.getId());
    }

    /** Phần trăm và gạch dưới là ký tự tên thực, không mở rộng truy vấn bằng wildcard LIKE. */
    @Test
    void productSearchTreatsLikeWildcardsAsLiteralCharacters() throws Exception {
        String prefix = UUID.randomUUID().toString();
        Product selected = product(category(), prefix + "%_", ProductStatus.ACTIVE);
        product(category(), prefix + "ab", ProductStatus.ACTIVE);
        JsonNode result = json.readTree(http.perform(get("/api/v1/products")
                        .param("q", prefix + "%_"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(result.path("total_elements").asLong()).isEqualTo(1);
        assertThat(result.path("content").get(0).path("id").asLong()).isEqualTo(selected.getId());
    }

    /** Fixture độc lập theo UUID để không lệ thuộc thứ tự chạy toàn bộ suite hiện có. */
    private Warehouse warehouse(WarehouseStatus status) {
        return warehouses.save(new Warehouse("UI-" + UUID.randomUUID(), "Chi nhánh kiểm thử",
                "Địa chỉ nội bộ không công khai", status));
    }

    /** Tài khoản có role thật trong database để filter reload trạng thái và phạm vi quyền. */
    private User actor(String role) {
        return users.save(new User(UUID.randomUUID() + "@example.com", "hash-kiểm-thử",
                "Người kiểm thử giao diện", roles.findByName(role).orElseThrow()));
    }

    /** Phân công dùng đúng bảng nối hiện có, không tạo schema hoặc giả lập quyền tại test. */
    private void assign(User actor, Warehouse warehouse) {
        jdbc.update("""
                INSERT INTO warehouse_staff_assignments (user_id, warehouse_id)
                VALUES (?, ?)
                """, actor.getId(), warehouse.getId());
    }

    /** Danh mục riêng của ca kiểm thử tìm kiếm. */
    private Category category() {
        String key = UUID.randomUUID().toString();
        return categories.save(new Category("Danh mục " + key, "ui-" + key));
    }

    /** Dùng constructor domain chuẩn để fixture tuân theo ràng buộc của catalog. */
    private Product product(Category category, String name, ProductStatus status) {
        return products.save(new Product(category, "UI-" + UUID.randomUUID(), name, BigDecimal.TEN, status));
    }

    /** Đọc JSON qua HTTP có JWT hoặc công khai, luôn kiểm tra 200 trước khi đối chiếu dữ liệu. */
    private JsonNode response(String path, User actor) throws Exception {
        return responseWithToken(path, actor == null ? null : jwt.generateToken(actor));
    }

    /** Cho phép tái sử dụng cùng token khi kiểm tra thu hồi phân công. */
    private JsonNode responseWithToken(String path, String token) throws Exception {
        var request = get(path);
        if (token != null) request.header("Authorization", "Bearer " + token);
        return json.readTree(http.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    /** Chuyển mảng JSON sang danh sách để khẳng định trường công khai và quyền theo từng kho. */
    private List<JsonNode> rows(JsonNode response) {
        return StreamSupport.stream(response.spliterator(), false).toList();
    }

    /** Đối chiếu ID thay vì tên trùng của fixture để không che lỗi phạm vi kho. */
    private List<Long> ids(JsonNode response) {
        return rows(response).stream().map(row -> row.path("id").asLong()).toList();
    }
}
