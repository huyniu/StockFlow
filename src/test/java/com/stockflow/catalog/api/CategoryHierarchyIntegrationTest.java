package com.stockflow.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** Kiểm chứng nhóm cha–con qua HTTP thật: phân quyền ADMIN, lọc trước phân trang và gợi ý hãng đúng nhánh. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CategoryHierarchyIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired BrandRepository brands;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired JwtTokenProvider jwt;

    private Category root;
    private Category group;
    private Category leaf;
    private Category sibling;
    private Brand brand;
    private String suffix;
    private Product rootProduct;
    private Product groupProduct;
    private Product leafProduct;

    /** Ba cấp có hàng ở cả cha/lá và nhánh ngoài; toàn bộ fixture rollback sau từng ca. */
    @BeforeEach
    void fixture() {
        suffix = UUID.randomUUID().toString();
        root = categories.save(new Category("Nhóm gốc " + suffix, "root-" + suffix));
        group = categories.save(new Category("Nhóm giữa " + suffix, "group-" + suffix, root));
        leaf = categories.save(new Category("Nhóm lá " + suffix, "leaf-" + suffix, group));
        sibling = categories.save(new Category("Nhánh khác " + suffix, "sibling-" + suffix, root));
        brand = brands.save(new Brand("Hãng " + suffix, "brand-" + suffix, new LinkedHashSet<>(List.of(group))));
        rootProduct = product(root, "ROOT", "1000000", brand);
        groupProduct = product(group, "GROUP", "2000000", brand);
        leafProduct = product(leaf, "LEAF", "3000000", brand);
        product(sibling, "OTHER", "2500000", null);
        product(categories.save(new Category("Ngoài " + suffix, "outside-" + suffix)), "OUTSIDE", "2000000", brand);
    }

    /** Danh sách công khai giữ payload cũ và thêm parent_id chính xác. */
    @Test
    void publicCategoryResponseIncludesParent() throws Exception {
        JsonNode response = json.readTree(mvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode matching = null;
        for (JsonNode item : response) if (item.path("id").asLong() == leaf.getId()) matching = item;
        assertThat(matching).isNotNull();
        assertThat(matching.path("parent_id").asLong()).isEqualTo(group.getId());
    }

    /** ADMIN tạo nhóm con và lưu cha thật; API cũ không truyền cha vẫn tạo nhóm gốc. */
    @Test
    void adminCanCreateChildAndLegacyRoot() throws Exception {
        create(Map.of("name", "Con mới " + suffix, "slug", "new-" + suffix, "parent_id", group.getId()), "ADMIN")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.parent_id").value(group.getId()));
        create(Map.of("name", "Gốc mới " + suffix, "slug", "new-root-" + suffix), "ADMIN")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.parent_id").isEmpty());
    }

    /** Quyền tạo cây không được mở cho khách/nhân viên/quản lý. */
    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "WAREHOUSE_STAFF", "MANAGER"})
    void nonAdminCannotCreateChild(String role) throws Exception {
        create(Map.of("name", "Cấm " + suffix, "slug", "denied-" + suffix, "parent_id", root.getId()), role)
                .andExpect(status().isForbidden());
    }

    /** Cha thiếu trả 404 và transaction không tạo danh mục mồ côi. */
    @Test
    void missingParentDoesNotCreateCategory() throws Exception {
        long count = categories.count();
        create(Map.of("name", "Thiếu " + suffix, "slug", "missing-" + suffix, "parent_id", Long.MAX_VALUE), "ADMIN")
                .andExpect(status().isNotFound());
        assertThat(categories.count()).isEqualTo(count);
    }

    /** DTO chặn ID cha âm hoặc bằng không trước khi query database. */
    @ParameterizedTest
    @ValueSource(longs = {-1, 0})
    void invalidParentIsBadRequest(long id) throws Exception {
        create(Map.of("name", "Sai " + suffix, "slug", "invalid-" + suffix, "parent_id", id), "ADMIN")
                .andExpect(status().isBadRequest());
    }

    /** Cấp thứ tư không thuộc MVP; giữ menu và đường dẫn danh mục dễ sử dụng. */
    @Test
    void rejectsFourthCategoryLevel() throws Exception {
        create(Map.of("name", "Cấp bốn " + suffix, "slug", "fourth-" + suffix, "parent_id", leaf.getId()), "ADMIN")
                .andExpect(status().isBadRequest());
    }

    /** Nhóm gốc bao gồm hàng nằm trực tiếp ở cha và trong mọi nhóm con, không lẫn nhánh ngoài. */
    @Test
    void parentFilterIncludesDescendants() throws Exception {
        JsonNode result = response(catalog(root));
        assertThat(result.path("total_elements").asLong()).isEqualTo(4);
        assertThat(ids(result)).contains(rootProduct.getId(), groupProduct.getId(), leafProduct.getId());
        assertThat(ids(response(catalog(group)))).containsExactly(groupProduct.getId(), leafProduct.getId());
        assertThat(ids(response(catalog(leaf)))).containsExactly(leafProduct.getId());
    }

    /** Danh mục, hãng, từ khóa và giá đều áp dụng trước phân trang và total_elements. */
    @Test
    void combinesHierarchyBrandPriceSearchAndPaging() throws Exception {
        var request = catalog(root).param("brandId", brand.getId().toString())
                .param("q", suffix).param("minPrice", "2000000").param("maxPrice", "3000000")
                .param("size", "1").param("sort", "unitPrice,desc");
        JsonNode first = response(request);
        assertThat(first.path("total_elements").asLong()).isEqualTo(2);
        assertThat(first.path("total_pages").asInt()).isEqualTo(2);
        assertThat(ids(first)).containsExactly(leafProduct.getId());
        JsonNode second = response(catalog(root).param("brandId", brand.getId().toString())
                .param("q", suffix).param("minPrice", "2000000").param("maxPrice", "3000000")
                .param("size", "1").param("sort", "unitPrice,desc").param("page", "1"));
        assertThat(ids(second)).containsExactly(groupProduct.getId());
    }

    /** Nhóm lá không có hàng trả tập rỗng; không suy đoán loại hàng bằng tên. */
    @Test
    void unknownCategoryIsEmpty() throws Exception {
        JsonNode result = response(get("/api/v1/products").param("categoryId", Long.toString(Long.MAX_VALUE)));
        assertThat(result.path("total_elements").asLong()).isZero();
        assertThat(ids(result)).isEmpty();
    }

    /** Tham số ID danh mục phải dương. */
    @ParameterizedTest
    @ValueSource(longs = {-1, 0})
    void invalidCategoryFilterIsBadRequest(long id) throws Exception {
        mvc.perform(get("/api/v1/products").param("categoryId", Long.toString(id)))
                .andExpect(status().isBadRequest());
    }

    /** Gợi ý hãng của nhóm giữa xuống lá và lên cha, không tự lan sang nhánh ngang chưa dùng hãng. */
    @Test
    void suggestedBrandUsesCorrectHierarchy() throws Exception {
        for (Category category : List.of(root, group, leaf)) {
            JsonNode result = response(get("/api/v1/brands").param("categoryId", category.getId().toString()));
            boolean found = false;
            for (JsonNode item : result) if (item.path("id").asLong() == brand.getId()) found = true;
            assertThat(found).isTrue();
        }
        JsonNode result = response(get("/api/v1/brands").param("categoryId", sibling.getId().toString()));
        for (JsonNode item : result) assertThat(item.path("id").asLong()).isNotEqualTo(brand.getId());
    }

    /** Kiểm tra các nhóm trong ảnh là dữ liệu thật, không chỉ các chuỗi render ở JavaScript. */
    @Test
    void referenceGroupsAndBrandsExist() throws Exception {
        Category kitchen = categories.findBySlug("gia-dung-nha-bep").orElseThrow();
        assertThat(kitchen.getParent().getSlug()).isEqualTo("do-gia-dung-lam-dep");
        assertThat(categories.findBySlug("noi-com-dien").orElseThrow().getParent().getId()).isEqualTo(kitchen.getId());
        assertThat(categories.findBySlug("camera-an-ninh").orElseThrow().getParent().getSlug()).isEqualTo("camera");
        assertThat(categories.findBySlug("tai-nghe-chup-tai").orElseThrow().getParent().getSlug()).isEqualTo("tai-nghe");
        Category laptop = categories.findBySlug("laptop").orElseThrow();
        JsonNode result = response(get("/api/v1/brands").param("categoryId", laptop.getId().toString()));
        List<String> names = new ArrayList<>();
        result.forEach(item -> names.add(item.path("name").asText()));
        assertThat(names).contains("Apple", "ASUS", "Lenovo", "Dell", "HP", "Microsoft Surface");
    }

    /** Tạo sản phẩm theo khóa hãng thật; không thay stock hoặc ledger trong fixture catalog. */
    private Product product(Category category, String code, String price, Brand assignedBrand) {
        Product product = new Product(category, code + "-" + suffix, code + " " + suffix,
                new BigDecimal(price), ProductStatus.ACTIVE);
        product.updateBrand(assignedBrand);
        return products.save(product);
    }

    /** JWT dùng user và role trong database test thật. */
    private String token(String role) {
        User user = users.save(new User(role + "-" + UUID.randomUUID() + "@tree.test", "unused",
                "Kiểm tra " + role, roles.findByName(role).orElseThrow()));
        return jwt.generateToken(user);
    }

    /** Gọi POST qua controller với JSON và quyền cần kiểm chứng. */
    private org.springframework.test.web.servlet.ResultActions create(Map<String, Object> payload, String role)
            throws Exception {
        return mvc.perform(post("/api/v1/categories").header("Authorization", "Bearer " + token(role))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(payload)));
    }

    /** Request nhóm danh mục dùng cùng tham số công khai như storefront. */
    private MockHttpServletRequestBuilder catalog(Category category) {
        return get("/api/v1/products").param("categoryId", category.getId().toString());
    }

    /** Đọc body thành JSON sau khi yêu cầu HTTP phải thành công. */
    private JsonNode response(MockHttpServletRequestBuilder request) throws Exception {
        return json.readTree(mvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    /** So sánh ID theo thứ tự trả về để phát hiện trùng hoặc mất hàng giữa trang. */
    private List<Long> ids(JsonNode result) {
        List<Long> resultIds = new ArrayList<>();
        result.path("content").forEach(item -> resultIds.add(item.path("id").asLong()));
        return resultIds;
    }
}
