package com.stockflow.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
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
import org.springframework.transaction.annotation.Transactional;

/** Kiểm chứng logo được lưu thật, chỉ ADMIN sửa và URL nguy hiểm không lọt qua JWT/Bean Validation/JPA. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BrandLogoIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired BrandRepository brands;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired JwtTokenProvider jwt;

    private String key;
    private Category suggested;
    private Category used;
    private Brand brand;
    private Product product;

    /** Hãng có cả gợi ý và danh mục thực dùng trong sản phẩm để kiểm tra response cập nhật không mất dữ liệu. */
    @BeforeEach
    void prepare() {
        key = UUID.randomUUID().toString();
        suggested = categories.save(new Category("Gợi ý logo " + key, "logo-suggested-" + key));
        used = categories.save(new Category("Sản phẩm logo " + key, "logo-used-" + key));
        brand = brands.save(new Brand("Hãng logo " + key, "logo-" + key, Set.of(suggested), "/assets/stockflow.svg"));
        product = products.save(new Product(used, "LOGO-" + key, "Sản phẩm giữ nguyên", new BigDecimal("120000"),
                ProductStatus.ACTIVE));
        product.updateBrand(brand);
        products.flush();
    }

    /** Logo HTTP/HTTPS hoặc assets được chuẩn hóa, lưu vào database và đọc công khai sau khi tạo hãng. */
    @ParameterizedTest
    @ValueSource(strings = {
            "https://cdn.example.com/logo.svg",
            "http://cdn.example.com/logo.png",
            "/assets/stockflow.svg?logo=1",
            "  https://cdn.example.com/trimmed.png  "
    })
    void adminCreatesBrandWithLogo(String logo) throws Exception {
        String body = mvc.perform(post("/api/v1/brands").header("Authorization", token("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("name", "Hãng mới " + key,
                                "slug", "new-logo-" + key, "logo_url", logo))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.logo_url").value(logo.strip()))
                .andReturn().getResponse().getContentAsString();
        Long id = json.readTree(body).path("id").asLong();
        assertThat(brands.findById(id).orElseThrow().getLogoUrl()).isEqualTo(logo.strip());
        assertThat(publicBrand(id).path("logo_url").asText()).isEqualTo(logo.strip());
    }

    /** ADMIN sửa hãng đã có; ID/tên/slug/gợi ý và phân loại/giá/SKU sản phẩm vẫn giữ nguyên. */
    @Test
    void updateLogoKeepsExistingBrandAndProductRelations() throws Exception {
        mvc.perform(patch("/api/v1/brands/{id}/logo", brand.getId()).header("Authorization", token("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("logo_url", "  https://cdn.example.com/updated.svg  "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(brand.getId()))
                .andExpect(jsonPath("$.name").value(brand.getName()))
                .andExpect(jsonPath("$.slug").value(brand.getSlug()))
                .andExpect(jsonPath("$.logo_url").value("https://cdn.example.com/updated.svg"))
                .andExpect(jsonPath("$.category_ids.length()").value(2));
        assertThat(publicBrand(brand.getId()).path("category_ids")).hasSize(2);
        assertThat(brand.getCategories()).containsExactly(suggested);
        assertThat(product.getBrand().getId()).isEqualTo(brand.getId());
        assertThat(product.getCategory().getId()).isEqualTo(used.getId());
        assertThat(product.getSku()).isEqualTo("LOGO-" + key);
        assertThat(product.getUnitPrice()).isEqualByComparingTo("120000");
    }

    /** Chuỗi rỗng chủ động xóa logo; thao tác lặp lại vẫn trả 200 và giữ hãng dùng để lọc sản phẩm. */
    @Test
    void adminRemovesLogoIdempotently() throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(patch("/api/v1/brands/{id}/logo", brand.getId()).header("Authorization", token("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"logo_url\":\"\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.logo_url").value(nullValue()));
        }
        assertThat(brands.findById(brand.getId()).orElseThrow().getLogoUrl()).isNull();
        assertThat(publicBrand(brand.getId()).path("name").asText()).isEqualTo(brand.getName());
    }

    /** Thêm hãng bằng request cũ không có logo vẫn hợp lệ. */
    @Test
    void createWithoutLogoRemainsCompatible() throws Exception {
        mvc.perform(post("/api/v1/brands").header("Authorization", token("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("name", "Không ảnh " + key, "slug", "plain-" + key))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.logo_url").value(nullValue()));
    }

    /** Các role khác ADMIN bị chặn 403 và không thể thay logo của hãng. */
    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "WAREHOUSE_STAFF", "MANAGER"})
    void otherRolesCannotUpdateLogo(String role) throws Exception {
        mvc.perform(patch("/api/v1/brands/{id}/logo", brand.getId()).header("Authorization", token(role))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"logo_url\":\"\"}"))
                .andExpect(status().isForbidden());
        assertThat(brand.getLogoUrl()).isEqualTo("/assets/stockflow.svg");
    }

    /** Khách chưa đăng nhập không được ghi logo; GET hãng vẫn công khai. */
    @Test
    void anonymousCannotUpdateLogo() throws Exception {
        mvc.perform(patch("/api/v1/brands/{id}/logo", brand.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"logo_url\":\"\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(publicBrand(brand.getId()).path("logo_url").asText()).isEqualTo("/assets/stockflow.svg");
    }

    /** Kiểm tra URL ở cả POST và PATCH, chặn mã thực thi, thông tin đăng nhập và đường dẫn vượt assets. */
    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)", "data:image/svg+xml,<svg/>", "file:///C:/logo.png",
            "ftp://example.com/logo.png", "//example.com/logo.png", "/private/logo.png",
            "/assets/../secret/logo.png", "/assets/%2e%2e/secret/logo.png",
            "https://user:pass@example.com/logo.png", "https:///logo.png"
    })
    void invalidLogoCannotBeCreatedOrSaved(String logo) throws Exception {
        long count = brands.count();
        mvc.perform(post("/api/v1/brands").header("Authorization", token("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("name", "URL lỗi " + key,
                                "slug", "invalid-logo-" + key, "logo_url", logo))))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/brands/{id}/logo", brand.getId()).header("Authorization", token("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("logo_url", logo))))
                .andExpect(status().isBadRequest());
        assertThat(brands.count()).isEqualTo(count);
        assertThat(brand.getLogoUrl()).isEqualTo("/assets/stockflow.svg");
    }

    /** Request thiếu trường hoặc null không được vô tình xóa logo hiện có. */
    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"logo_url\":null}"})
    void absentLogoCannotRemoveExistingImage(String body) throws Exception {
        mvc.perform(patch("/api/v1/brands/{id}/logo", brand.getId()).header("Authorization", token("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertThat(brand.getLogoUrl()).isEqualTo("/assets/stockflow.svg");
    }

    /** Vượt giới hạn cột bị chặn tại DTO thay vì lỗi SQL sau khi lưu. */
    @Test
    void oversizedLogoIsRejected() throws Exception {
        mvc.perform(patch("/api/v1/brands/{id}/logo", brand.getId()).header("Authorization", token("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("logo_url", "https://example.com/" + "a".repeat(2048)))))
                .andExpect(status().isBadRequest());
    }

    /** ID không hợp lệ hoặc hãng đã không tồn tại trả lỗi rõ ràng và không tạo hãng mới ngầm. */
    @Test
    void invalidOrMissingBrandIsRejected() throws Exception {
        mvc.perform(patch("/api/v1/brands/-1/logo").header("Authorization", token("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"logo_url\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/brands/{id}/logo", Long.MAX_VALUE).header("Authorization", token("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"logo_url\":\"\"}"))
                .andExpect(status().isNotFound());
    }

    /** Tra hãng qua HTTP công khai sau thao tác lưu, không chỉ đọc entity đang ở trong test. */
    private JsonNode publicBrand(Long id) throws Exception {
        String body = mvc.perform(get("/api/v1/brands")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (JsonNode entry : json.readTree(body)) {
            if (entry.path("id").asLong() == id) return entry;
        }
        throw new AssertionError("Không có hãng trong response công khai.");
    }

    /** JWT dùng actor đã lưu thật và role từ database, đi qua filter chain của ứng dụng. */
    private String token(String role) {
        User actor = users.save(new User(UUID.randomUUID() + "@logo.test", "hash", "Người kiểm tra logo",
                roles.findByName(role).orElseThrow()));
        return "Bearer " + jwt.generateToken(actor);
    }
}
