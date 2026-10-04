package com.stockflow.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

/** Kiểm chứng hãng bằng JWT/JPA/Flyway thật, gồm quyền ADMIN, PATCH và lọc trước phân trang. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BrandIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired BrandRepository brands;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired JwtTokenProvider jwt;

    private String suffix;
    private Category phone;
    private Category laptop;
    private Brand apple;
    private Brand samsung;
    private Brand unused;
    private Product first;
    private Product second;
    private Product premium;

    /** Dữ liệu riêng theo UUID; một hãng dùng ở hai danh mục và hai sản phẩm cùng giá sát biên trang. */
    @BeforeEach
    void prepare() {
        suffix = UUID.randomUUID().toString();
        phone = categories.save(new Category("Điện thoại " + suffix, "phone-" + suffix));
        laptop = categories.save(new Category("Laptop " + suffix, "laptop-" + suffix));
        apple = brands.save(new Brand("Hãng A " + suffix, "a-" + suffix, Set.of(phone)));
        samsung = brands.save(new Brand("Hãng B " + suffix, "b-" + suffix, Set.of(phone)));
        unused = brands.save(new Brand("Hãng C " + suffix, "c-" + suffix, Set.of(phone)));
        first = product("A1", phone, apple, "2000000", ProductStatus.ACTIVE);
        second = product("A2", phone, apple, "2000000", ProductStatus.ACTIVE);
        premium = product("A3", phone, apple, "5000000", ProductStatus.ACTIVE);
        product("B", phone, samsung, "1800000", ProductStatus.ACTIVE);
        product("NO-BRAND", phone, null, "900000", ProductStatus.ACTIVE);
        product("STOP", phone, apple, "2000000", ProductStatus.INACTIVE);
        product("LAPTOP", laptop, apple, "2000000", ProductStatus.ACTIVE);
        products.flush();
    }

    /** Khách vãng lai đọc được hãng có gợi ý dù hãng chưa có sản phẩm. */
    @Test
    void publicCategoryBrandsIncludeEmptySuggestedBrand() throws Exception {
        JsonNode data = read(get("/api/v1/brands").param("categoryId", phone.getId().toString()));
        assertThat(ids(data)).containsExactly(apple.getId(), samsung.getId(), unused.getId());
    }

    /** Một hãng được gán cho laptop cũng có trong menu laptop, không phải tạo hãng trùng. */
    @Test
    void brandAppearsInAnotherCategoryWhenUsedByProduct() throws Exception {
        JsonNode data = read(get("/api/v1/brands").param("categoryId", laptop.getId().toString()));
        assertThat(ids(data)).containsExactly(apple.getId());
        assertThat(data.get(0).path("category_ids").toString())
                .contains(phone.getId().toString(), laptop.getId().toString());
    }

    /** V9 chuẩn bị 16 hãng điện thoại thật, liên kết bằng ID chứ không hardcode frontend. */
    @Test
    void migrationProvidesPhoneBrands() throws Exception {
        Long phoneId = categories.findBySlug("dien-thoai").orElseThrow().getId();
        JsonNode data = read(get("/api/v1/brands").param("categoryId", phoneId.toString()));
        assertThat(data).hasSize(16);
        List<String> slugs = new ArrayList<>();
        data.forEach(brand -> slugs.add(brand.path("slug").asText()));
        assertThat(slugs).contains("apple", "samsung", "oppo", "xiaomi", "infinix");
    }

    /** Mọi role đọc được hãng; quyền ghi catalog được kiểm chứng riêng. */
    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "WAREHOUSE_STAFF", "MANAGER", "ADMIN"})
    void rolesCanReadBrands(String role) throws Exception {
        mvc.perform(get("/api/v1/brands").header("Authorization", token(role))).andExpect(status().isOk());
    }

    /** Hãng chỉ ADMIN được tạo, các actor vận hành/khách đều bị chặn. */
    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "WAREHOUSE_STAFF", "MANAGER"})
    void otherRolesCannotCreateBrand(String role) throws Exception {
        mvc.perform(post("/api/v1/brands").header("Authorization", token(role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(brandBody())))
                .andExpect(status().isForbidden());
    }

    /** Chưa đăng nhập không được ghi catalog. */
    @Test
    void anonymousCannotCreateBrand() throws Exception {
        mvc.perform(post("/api/v1/brands").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(brandBody())))
                .andExpect(status().isUnauthorized());
    }

    /** Tạo hãng và hai danh mục gợi ý cùng transaction, chuẩn hóa khoảng trắng và slug. */
    @Test
    void adminCreatesBrandWithMultipleSuggestedCategories() throws Exception {
        mvc.perform(post("/api/v1/brands").header("Authorization", token("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of(
                                "name", "  Thương hiệu mới " + suffix + "  ",
                                "slug", "  NEW-" + suffix + "  ",
                                "category_ids", List.of(phone.getId(), laptop.getId(), phone.getId())))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Thương hiệu mới " + suffix))
                .andExpect(jsonPath("$.slug").value("new-" + suffix))
                .andExpect(jsonPath("$.category_ids.length()").value(2));
    }

    /** Request không có danh mục gợi ý vẫn tạo được hãng dùng chung. */
    @Test
    void adminCanCreateBrandWithoutSuggestedCategory() throws Exception {
        mvc.perform(post("/api/v1/brands").header("Authorization", token("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("name", "Chung " + suffix, "slug", "general-" + suffix))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category_ids.length()").value(0));
    }

    /** Danh mục không tồn tại làm toàn bộ thao tác tạo hãng thất bại. */
    @Test
    void missingSuggestedCategoryDoesNotCreateBrand() throws Exception {
        long count = brands.count();
        mvc.perform(post("/api/v1/brands").header("Authorization", token("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of(
                                "name", "Lỗi " + suffix, "slug", "missing-" + suffix,
                                "category_ids", List.of(Long.MAX_VALUE)))))
                .andExpect(status().isNotFound());
        assertThat(brands.count()).isEqualTo(count);
    }

    /** Chặn tên trùng khác hoa/thường hoặc slug trùng bằng lỗi nghiệp vụ 409. */
    @ParameterizedTest
    @ValueSource(strings = {"name", "slug"})
    void duplicateBrandReturnsConflict(String field) throws Exception {
        Map<String, Object> body = field.equals("name")
                ? Map.of("name", apple.getName().toUpperCase(), "slug", "duplicate-" + suffix)
                : Map.of("name", "Tên khác " + suffix, "slug", apple.getSlug());
        mvc.perform(post("/api/v1/brands").header("Authorization", token("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)))
                .andExpect(status().isConflict());
    }

    /** DTO bắt lỗi trường trống, danh mục âm và phần tử null trước khi gọi service. */
    @ParameterizedTest
    @ValueSource(strings = {
            "{\"name\":\" \",\"slug\":\"abc\"}",
            "{\"name\":\"Hãng mới\",\"slug\":\" \"}",
            "{\"name\":\"Hãng mới\",\"slug\":\"abc\",\"category_ids\":[-1]}",
            "{\"name\":\"Hãng mới\",\"slug\":\"abc\",\"category_ids\":[null]}"
    })
    void invalidBrandRequestReturnsBadRequest(String body) throws Exception {
        mvc.perform(post("/api/v1/brands").header("Authorization", token("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    /** Hãng, danh mục, từ khóa, trạng thái và giá phải được kết hợp trước tính tổng/phân trang. */
    @Test
    void productFilterCombinesBrandWithOtherConditions() throws Exception {
        JsonNode data = read(catalog().param("brandId", apple.getId().toString())
                .param("q", "A").param("minPrice", "1000000").param("maxPrice", "3000000"));
        assertThat(data.path("total_elements").asInt()).isEqualTo(2);
        assertThat(ids(data.path("content"))).containsExactly(first.getId(), second.getId());
    }

    /** Các trang cùng giá có ID làm thứ tự phụ, không lặp hoặc mất sản phẩm sau khi lọc hãng. */
    @Test
    void brandPaginationHasCorrectCountAndStableOrder() throws Exception {
        JsonNode page0 = read(catalog().param("brandId", apple.getId().toString())
                .param("sort", "unitPrice,asc").param("size", "1").param("page", "0"));
        JsonNode page1 = read(catalog().param("brandId", apple.getId().toString())
                .param("sort", "unitPrice,asc").param("size", "1").param("page", "1"));
        assertThat(page0.path("total_elements").asInt()).isEqualTo(3);
        assertThat(page0.path("total_pages").asInt()).isEqualTo(3);
        assertThat(ids(page0.path("content"))).containsExactly(first.getId());
        assertThat(ids(page1.path("content"))).containsExactly(second.getId());
    }

    /** Hãng có gợi ý nhưng chưa có sản phẩm trả trang rỗng, không tự bơm fixture vào catalog. */
    @Test
    void emptyBrandReturnsEmptyProductPage() throws Exception {
        assertThat(read(catalog().param("brandId", unused.getId().toString()))
                .path("total_elements").asInt()).isZero();
    }

    /** Hãng không có trong danh mục đó không làm rò kết quả từ danh mục khác. */
    @Test
    void unrelatedCategoryBrandCombinationReturnsEmptyPage() throws Exception {
        JsonNode data = read(get("/api/v1/products").param("categoryId", laptop.getId().toString())
                .param("brandId", samsung.getId().toString()));
        assertThat(data.path("total_elements").asInt()).isZero();
    }

    /** ID sai kiểu hoặc không dương được trả 400, thay vì gây lỗi query 500. */
    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc"})
    void invalidBrandFilterReturnsBadRequest(String id) throws Exception {
        mvc.perform(catalog().param("brandId", id)).andExpect(status().isBadRequest());
    }

    /** ADMIN tạo sản phẩm có hãng, response vẫn giữ các trường catalog trước đây. */
    @Test
    void adminCreatesBrandedProduct() throws Exception {
        mvc.perform(post("/api/v1/products").header("Authorization", token("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of(
                                "sku", "NEW-" + suffix, "name", "Điện thoại mới",
                                "category_id", phone.getId(), "brand_id", samsung.getId(),
                                "unit_price", 1200000, "status", "ACTIVE"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category_id").value(phone.getId()))
                .andExpect(jsonPath("$.brand_id").value(samsung.getId()))
                .andExpect(jsonPath("$.brand_name").value(samsung.getName()));
    }

    /** Request cũ không có hãng vẫn tạo được hàng, không suy đoán hãng từ chữ Samsung trong tên. */
    @Test
    void legacyCreateRequestDoesNotInferBrandFromName() throws Exception {
        mvc.perform(post("/api/v1/products").header("Authorization", token("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of(
                                "sku", "LEGACY-" + suffix, "name", "Samsung nhưng chưa khai báo hãng",
                                "category_id", phone.getId(), "unit_price", 1200000, "status", "ACTIVE"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.brand_id").isEmpty())
                .andExpect(jsonPath("$.brand_name").isEmpty());
    }

    /** Bỏ qua/null ở PATCH giữ hãng và bộ ảnh; không xóa ngầm dữ liệu cũ. */
    @Test
    void patchNullBrandPreservesBrandAndGallery() throws Exception {
        first.replaceImageUrls(List.of("/assets/front.jpg", "/assets/back.jpg"));
        products.flush();
        mvc.perform(patchProduct(first).content("{\"brand_id\":null,\"name\":\"Tên đã sửa\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brand_id").value(apple.getId()))
                .andExpect(jsonPath("$.image_urls[1]").value("/assets/back.jpg"));
    }

    /** Đổi hãng là cập nhật catalog, không tạo thêm SKU hoặc đổi giá/ảnh. */
    @Test
    void adminCanChangeProductBrand() throws Exception {
        mvc.perform(patchProduct(first).content(json.writeValueAsBytes(Map.of("brand_id", samsung.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brand_id").value(samsung.getId()))
                .andExpect(jsonPath("$.unit_price").value(2000000))
                .andExpect(jsonPath("$.sku").value(first.getSku()));
    }

    /** Xóa hãng dùng cờ riêng, giữ nguyên danh mục và SKU. */
    @Test
    void clearBrandRemovesOnlyBrand() throws Exception {
        mvc.perform(patchProduct(first).content("{\"clear_brand\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brand_id").isEmpty())
                .andExpect(jsonPath("$.category_id").value(phone.getId()))
                .andExpect(jsonPath("$.sku").value(first.getSku()));
    }

    /** Chặn yêu cầu mâu thuẫn vừa gán hãng vừa xóa hãng. */
    @Test
    void patchCannotSetAndClearBrandTogether() throws Exception {
        mvc.perform(patchProduct(first).content(json.writeValueAsBytes(
                        Map.of("brand_id", samsung.getId(), "clear_brand", true))))
                .andExpect(status().isBadRequest());
        assertThat(first.getBrand().getId()).isEqualTo(apple.getId());
    }

    /** ID hãng không tồn tại trả 404 trước khi có thay đổi bền vững ở database. */
    @Test
    void missingBrandDoesNotCreateProduct() throws Exception {
        long count = products.count();
        mvc.perform(post("/api/v1/products").header("Authorization", token("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of(
                                "sku", "MISSING-" + suffix, "name", "Hàng chưa có hãng",
                                "category_id", phone.getId(), "brand_id", Long.MAX_VALUE,
                                "unit_price", 1000000, "status", "ACTIVE"))))
                .andExpect(status().isNotFound());
        assertThat(products.count()).isEqualTo(count);
    }

    /** Fixture sản phẩm có hãng được lưu trực tiếp để test đọc không phụ thuộc API ghi. */
    private Product product(String tag, Category category, Brand brand, String price, ProductStatus state) {
        Product product = new Product(category, suffix + "-" + tag, "Mẫu " + tag, new BigDecimal(price), state);
        product.updateBrand(brand);
        return products.save(product);
    }

    /** JWT thật của role được lấy từ database, không giả lập quyền bằng mock security. */
    private String token(String role) {
        User actor = users.save(new User(
                "brand-" + UUID.randomUUID() + "@example.com", "unused-hash", "Người kiểm chứng hãng",
                roles.findByName(role).orElseThrow()));
        return "Bearer " + jwt.generateToken(actor);
    }

    /** Body tên/slug riêng theo từng test. */
    private Map<String, Object> brandBody() {
        return Map.of("name", "Mới " + suffix, "slug", "new-" + suffix, "category_ids", List.of(phone.getId()));
    }

    /** Request PATCH dùng actor ADMIN và content type JSON thật. */
    private MockHttpServletRequestBuilder patchProduct(Product product) {
        return patch("/api/v1/products/" + product.getId())
                .header("Authorization", token("ADMIN")).contentType(MediaType.APPLICATION_JSON);
    }

    /** Catalog công khai giới hạn fixture để tổng đếm không phụ thuộc dữ liệu của suite khác. */
    private MockHttpServletRequestBuilder catalog() {
        return get("/api/v1/products").param("categoryId", phone.getId().toString()).param("status", "ACTIVE");
    }

    /** Parse UTF-8 để phát hiện lỗi dấu tiếng Việt trong tên hãng. */
    private JsonNode read(MockHttpServletRequestBuilder request) throws Exception {
        return json.readTree(mvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** So sánh ID/thứ tự thực tế, không chỉ số lượng hoặc trạng thái HTTP. */
    private List<Long> ids(JsonNode data) {
        List<Long> result = new ArrayList<>();
        data.forEach(item -> result.add(item.path("id").asLong()));
        return result;
    }
}
