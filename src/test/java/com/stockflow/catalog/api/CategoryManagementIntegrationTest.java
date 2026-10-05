package com.stockflow.catalog.api;

import static com.stockflow.user.support.UserTestFixtures.verifiedUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.auth.security.JwtTokenProvider;
import com.stockflow.catalog.domain.Brand;
import com.stockflow.catalog.domain.Category;
import com.stockflow.catalog.domain.Product;
import com.stockflow.catalog.domain.ProductStatus;
import com.stockflow.catalog.repository.BrandRepository;
import com.stockflow.catalog.repository.CategoryRepository;
import com.stockflow.catalog.repository.ProductRepository;
import com.stockflow.user.domain.User;
import com.stockflow.user.repository.RoleRepository;
import com.stockflow.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Kiểm chứng CRUD danh mục qua JWT/HTTP/JPA thật, bảo toàn cây/SKU và không xóa dây chuyền khi còn sử dụng. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CategoryManagementIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired BrandRepository brands;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired JwtTokenProvider jwt;
    @Autowired EntityManager entities;

    private String key;
    private Category root;
    private Category leaf;
    private Category used;
    private Brand brand;
    private Product product;

    /** Tạo cây có lá trống, danh mục đang dùng và gợi ý hãng để kiểm tra tác động xóa bằng database. */
    @BeforeEach
    void prepare() {
        key = UUID.randomUUID().toString();
        root = categories.save(new Category("Gốc quản lý " + key, "manage-root-" + key));
        leaf = categories.save(new Category("Màn hình kiểm tra " + key, "man-hinh-" + key, root));
        used = categories.save(new Category("Đang sử dụng " + key, "manage-used-" + key));
        brand = brands.save(new Brand("Hãng quản lý " + key, "manage-brand-" + key, Set.of(leaf, used)));
        product = products.save(new Product(used, "MANAGE-CAT-" + key, "Sản phẩm giữ nguyên",
                new BigDecimal("250000"), ProductStatus.ACTIVE));
        product.updateBrand(brand);
        products.flush();
    }

    /** Sửa danh mục có hàng không làm thay ID/cha hoặc giá/SKU/phân loại; public response đọc tên mới. */
    @Test
    void adminUpdatesNameAndSlugWithoutChangingRelations() throws Exception {
        update(used.getId(), Map.of("name", "  Tên mới " + key + "  ", "slug", " NEW-" + key + " "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(used.getId()))
                .andExpect(jsonPath("$.name").value("Tên mới " + key))
                .andExpect(jsonPath("$.slug").value("new-" + key));
        assertThat(products.findById(product.getId()).orElseThrow().getCategory().getId()).isEqualTo(used.getId());
        assertThat(product.getSku()).isEqualTo("MANAGE-CAT-" + key);
        assertThat(product.getUnitPrice()).isEqualByComparingTo("250000");
        update(leaf.getId(), Map.of("name", "Lá mới " + key, "slug", "leaf-new-" + key))
                .andExpect(status().isOk()).andExpect(jsonPath("$.parent_id").value(root.getId()));
        assertThat(search("new-" + key)).hasSize(2);
        assertThat(brands.findById(brand.getId()).orElseThrow().getCategories()).hasSize(2);
    }

    /** Lưu lại chính tên/slug hiện tại không bị báo trùng hoặc tạo thêm ID. */
    @Test
    void updatingOwnValuesIsAllowed() throws Exception {
        long count = categories.count();
        for (int attempt = 0; attempt < 2; attempt++) {
            update(leaf.getId(), Map.of("name", leaf.getName(), "slug", leaf.getSlug()))
                    .andExpect(status().isOk());
        }
        assertThat(categories.count()).isEqualTo(count);
    }

    /** Tên không phân biệt hoa/thường và slug của danh mục khác bị chặn, giữ nguyên dữ liệu cũ. */
    @Test
    void duplicateNamesOrSlugsAreConflict() throws Exception {
        String originalName = leaf.getName();
        String originalSlug = leaf.getSlug();
        update(leaf.getId(), Map.of("name", used.getName().toUpperCase(), "slug", "unique-" + key))
                .andExpect(status().isConflict());
        update(leaf.getId(), Map.of("name", "Tên riêng " + key, "slug", used.getSlug().toUpperCase()))
                .andExpect(status().isConflict());
        assertThat(categories.findById(leaf.getId()).orElseThrow().getName()).isEqualTo(originalName);
        assertThat(leaf.getSlug()).isEqualTo(originalSlug);
    }

    /** Tạo cũng chặn tên khác hoa/thường để cùng quy tắc với sửa danh mục. */
    @Test
    void createRejectsCaseInsensitiveDuplicateName() throws Exception {
        mvc.perform(post("/api/v1/categories").header("Authorization", token("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("name", leaf.getName().toUpperCase(), "slug", "dup-" + key))))
                .andExpect(status().isConflict());
    }

    /** Xóa lá trống gỡ đúng gợi ý hãng, giữ cha/hãng/SKU và liên kết gợi ý ở danh mục khác. */
    @Test
    void deleteEmptyCategoryOnlyRemovesItsSuggestionLinks() throws Exception {
        Long categoryId = leaf.getId();
        Long brandId = brand.getId();
        Long productId = product.getId();
        mvc.perform(delete("/api/v1/categories/{id}", categoryId).header("Authorization", token("ADMIN")))
                .andExpect(status().isNoContent());
        entities.clear();
        assertThat(categories.findById(categoryId)).isEmpty();
        assertThat(categories.findById(root.getId())).isPresent();
        assertThat(brands.findById(brandId).orElseThrow().getCategories())
                .extracting(Category::getId).containsExactly(used.getId());
        assertThat(products.findById(productId).orElseThrow().getUnitPrice()).isEqualByComparingTo("250000");
        assertThat(search("man-hinh-" + key)).isEmpty();
        mvc.perform(delete("/api/v1/categories/{id}", categoryId).header("Authorization", token("ADMIN")))
                .andExpect(status().isNotFound());
    }

    /** Không xóa cả nhánh; danh mục cha có con và các gợi ý hãng đều còn sau lỗi 409. */
    @Test
    void parentWithChildrenCannotBeDeleted() throws Exception {
        mvc.perform(delete("/api/v1/categories/{id}", root.getId()).header("Authorization", token("ADMIN")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(
                        "Không thể xóa danh mục đang có danh mục con. Hãy xử lý các danh mục con trước."));
        assertThat(categories.findById(root.getId())).isPresent();
        assertThat(categories.findById(leaf.getId())).isPresent();
    }

    /** Sản phẩm đang bán hoặc đã ẩn đều chặn xóa; không để SKU/lịch sử tham chiếu danh mục bị mất. */
    @ParameterizedTest
    @EnumSource(ProductStatus.class)
    void categoryWithAnyProductCannotBeDeleted(ProductStatus status) throws Exception {
        products.save(new Product(leaf, "BLOCK-" + key, "Sản phẩm chặn xóa", new BigDecimal("12000"), status));
        products.flush();
        mvc.perform(delete("/api/v1/categories/{id}", leaf.getId()).header("Authorization", token("ADMIN")))
                .andExpect(status().isConflict());
        entities.clear();
        assertThat(categories.findById(leaf.getId())).isPresent();
        assertThat(brands.findById(brand.getId()).orElseThrow().getCategories()).hasSize(2);
    }

    /** Không mở quyền ghi cho MANAGER/staff/customer, kể cả khi cố gọi API trực tiếp. */
    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "WAREHOUSE_STAFF", "CUSTOMER"})
    void nonAdminCannotUpdateOrDelete(String role) throws Exception {
        String authorization = token(role);
        mvc.perform(patch("/api/v1/categories/{id}", leaf.getId()).header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("name", "Bị chặn " + key, "slug", "denied-" + key))))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/categories/{id}", leaf.getId()).header("Authorization", authorization))
                .andExpect(status().isForbidden());
        assertThat(categories.findById(leaf.getId())).isPresent();
    }

    /** Khách vãng lai vẫn đọc/tìm danh mục nhưng phải đăng nhập trước mọi thao tác ghi. */
    @Test
    void anonymousCannotUpdateOrDelete() throws Exception {
        mvc.perform(patch("/api/v1/categories/{id}", leaf.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("name", "Ẩn danh", "slug", "anonymous"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/categories/{id}", leaf.getId())).andExpect(status().isUnauthorized());
    }

    /** ID không dương trả 400 ở cả hai endpoint, không được dùng để ghi/xóa dữ liệu. */
    @ParameterizedTest
    @ValueSource(longs = {-1, 0})
    void invalidIdIsBadRequest(long id) throws Exception {
        update(id, Map.of("name", "Sai ID", "slug", "invalid-id")).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/categories/{id}", id).header("Authorization", token("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    /** ID thiếu trả 404 rõ ràng; không mặc định ghi vào danh mục đầu tiên. */
    @Test
    void missingIdIsNotFound() throws Exception {
        update(Long.MAX_VALUE, Map.of("name", "Không tồn tại", "slug", "missing"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/categories/{id}", Long.MAX_VALUE).header("Authorization", token("ADMIN")))
                .andExpect(status().isNotFound());
    }

    /** Tên/slug bắt buộc và giới hạn độ dài; request sai không đổi bản ghi. */
    @ParameterizedTest
    @MethodSource("invalidUpdates")
    void invalidUpdateDoesNotChangeCategory(Map<String, String> body) throws Exception {
        String name = leaf.getName();
        update(leaf.getId(), body).andExpect(status().isBadRequest());
        assertThat(categories.findById(leaf.getId()).orElseThrow().getName()).isEqualTo(name);
    }

    /** Các biên validation được dùng chung để kiểm tra qua Bean Validation/HTTP thật. */
    private static Stream<Map<String, String>> invalidUpdates() {
        return Stream.of(
                Map.of("name", "", "slug", "valid"),
                Map.of("name", "   ", "slug", "valid"),
                Map.of("name", "x".repeat(151), "slug", "valid"),
                Map.of("name", "Hợp lệ", "slug", ""),
                Map.of("name", "Hợp lệ", "slug", "x".repeat(181)),
                Map.of("name", "Hợp lệ"),
                Map.of("slug", "valid"));
    }

    /** Có/không dấu, khác hoa/thường và slug đều tìm được dữ liệu bằng API công khai. */
    @Test
    void publicSearchMatchesVietnameseNameAndAsciiSlug() throws Exception {
        for (String query : List.of("MÀN HÌNH KIỂM TRA " + key, "man hinh " + key, "man-hinh-" + key)) {
            assertThat(search(query)).hasSize(1);
            assertThat(search(query).get(0).path("id").asLong()).isEqualTo(leaf.getId());
        }
        assertThat(search("không có " + key)).isEmpty();
        assertThat(search("   ")).hasSize((int) categories.count());
    }

    /** Escape LIKE: dấu %/_/! trong tên chỉ khớp đúng ký tự, không trở thành wildcard quét mọi nhóm. */
    @Test
    void publicSearchEscapesWildcardCharacters() throws Exception {
        Category special = categories.save(new Category("Ký hiệu_%! " + key, "special-" + key));
        categories.flush();
        assertThat(search("_%!")).hasSize(1);
        assertThat(search("_%!").get(0).path("id").asLong()).isEqualTo(special.getId());
    }

    /** Giới hạn từ khóa tránh request quá dài, giữ response lỗi thống nhất. */
    @Test
    void tooLongSearchIsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/categories").param("q", "x".repeat(151)))
                .andExpect(status().isBadRequest());
    }

    /** Helper PATCH dùng JWT ADMIN thật; không thay bộ lọc security bằng mock user. */
    private org.springframework.test.web.servlet.ResultActions update(Long id, Map<String, String> body) throws Exception {
        return mvc.perform(patch("/api/v1/categories/{id}", id).header("Authorization", token("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)));
    }

    /** Đọc JSON bằng UTF-8 và so sánh ID thay vì dựa vào thứ tự dữ liệu seed. */
    private List<JsonNode> search(String query) throws Exception {
        JsonNode result = json.readTree(mvc.perform(get("/api/v1/categories").param("q", query))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        return json.convertValue(result, json.getTypeFactory().constructCollectionType(List.class, JsonNode.class));
    }

    /** Mỗi ca tạo user có role đã seed rồi phát JWT qua provider hiện có. */
    private String token(String role) {
        User user = users.save(verifiedUser(UUID.randomUUID() + "@category-management.test", "hash", "Quản lý danh mục",
                roles.findByName(role).orElseThrow()));
        return "Bearer " + jwt.generateToken(user);
    }
}
