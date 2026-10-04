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
import com.stockflow.catalog.domain.Category;
import com.stockflow.catalog.repository.CategoryRepository;
import com.stockflow.catalog.repository.ProductRepository;
import com.stockflow.user.domain.User;
import com.stockflow.user.repository.RoleRepository;
import com.stockflow.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.HashMap;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * Kiểm thử ảnh bìa xuyên suốt migration, validation, phân quyền, persistence và API catalog công khai.
 * JWT thật đi qua filter chain; mỗi test rollback dữ liệu và không phụ thuộc CDN hay truy cập Internet.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductImageIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired RoleRepository roles;
    @Autowired UserRepository users;
    @Autowired JwtTokenProvider jwt;
    @Autowired EntityManager entities;

    private String adminToken;
    private Category category;

    /** Tạo actor ADMIN và danh mục riêng để các ca validation không dùng dữ liệu sản phẩm seed. */
    @BeforeEach
    void setUp() {
        adminToken = token("ADMIN");
        category = categories.save(new Category("Danh mục ảnh " + UUID.randomUUID(), "anh-" + UUID.randomUUID()));
    }

    /** URL được chuẩn hóa và lưu thật; khách chưa đăng nhập đọc được ảnh ở chi tiết và trang danh sách. */
    @ParameterizedTest
    @ValueSource(strings = {
            "https://images.unsplash.com/photo-1695048133142-1a20484d2569?w=600&auto=format&fit=crop&q=80",
            "http://example.com/photo.jpg",
            "/assets/stockflow.svg"
    })
    void adminCreatesImageAndPublicCatalogReturnsIt(String imageUrl) throws Exception {
        JsonNode product = create("  " + imageUrl + "  ");
        Long id = product.path("id").asLong();
        assertThat(product.path("image_url").asText()).isEqualTo(imageUrl);
        assertThat(products.findById(id).orElseThrow().getImageUrl()).isEqualTo(imageUrl);

        mvc.perform(get("/api/v1/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.image_url").value(imageUrl));
        mvc.perform(get("/api/v1/products").param("q", product.path("sku").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].image_url").value(imageUrl));
    }

    /** Request cũ chưa có image_url vẫn tạo sản phẩm thành công và lưu ảnh null. */
    @Test
    void createWithoutImageRemainsCompatible() throws Exception {
        JsonNode response = create(null);
        assertThat(products.findById(response.path("id").asLong()).orElseThrow().getImageUrl()).isNull();
    }

    /** Chỉ ảnh được đổi trong PATCH; tên và giá hiện có phải giữ nguyên, ảnh được ghi vào database. */
    @Test
    void adminReplacesImageWithoutChangingOtherFields() throws Exception {
        JsonNode product = create("https://example.com/old.jpg");
        Long id = product.path("id").asLong();
        mvc.perform(patch("/api/v1/products/{id}", id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("image_url", " https://example.com/new.jpg "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.image_url").value("https://example.com/new.jpg"))
                .andExpect(jsonPath("$.name").value(product.path("name").asText()))
                .andExpect(jsonPath("$.unit_price").value(250000));
        products.flush();
        entities.clear();
        assertThat(products.findById(id).orElseThrow().getImageUrl()).isEqualTo("https://example.com/new.jpg");
    }

    /** Bỏ qua trường ảnh hoặc gửi null khi sửa giá không được làm mất ảnh đã có. */
    @Test
    void omittedOrNullImageInPatchPreservesStoredImage() throws Exception {
        Long id = create("https://example.com/preserved.jpg").path("id").asLong();
        Map<String, Object> payload = new HashMap<>();
        payload.put("unit_price", new BigDecimal("260000"));
        update(id, payload).andExpect(jsonPath("$.image_url").value("https://example.com/preserved.jpg"));
        payload.put("image_url", null);
        update(id, payload).andExpect(jsonPath("$.image_url").value("https://example.com/preserved.jpg"));
    }

    /** Chuỗi trống là lệnh xóa ảnh rõ ràng, không ảnh hưởng SKU và nội dung sản phẩm. */
    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void emptyImageInPatchClearsStoredImage(String value) throws Exception {
        Long id = create("https://example.com/clear.jpg").path("id").asLong();
        update(id, Map.of("image_url", value)).andExpect(jsonPath("$.image_url").isEmpty());
        products.flush();
        entities.clear();
        assertThat(products.findById(id).orElseThrow().getImageUrl()).isNull();
    }

    /** Scheme thực thi, URL hỏng, credentials và traversal tài nguyên cục bộ phải bị chặn trước persistence. */
    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)",
            "data:image/svg+xml;base64,AAAA",
            "file:///C:/private.jpg",
            "//example.com/photo.jpg",
            "https://",
            "https://example.com/photo with spaces.jpg",
            "https://user:password@example.com/photo.jpg",
            "/assets/../application.yml",
            "/assets/%2e%2e/application.yml"
    })
    void invalidImageIsRejectedOnCreateAndPatch(String value) throws Exception {
        long count = products.count();
        Map<String, Object> payload = payload();
        payload.put("image_url", value);
        mvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("imageUrl"));
        assertThat(products.count()).isEqualTo(count);

        Long id = create("https://example.com/safe.jpg").path("id").asLong();
        mvc.perform(patch("/api/v1/products/{id}", id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("image_url", value))))
                .andExpect(status().isBadRequest());
        assertThat(products.findById(id).orElseThrow().getImageUrl()).isEqualTo("https://example.com/safe.jpg");
    }

    /** Cột có giới hạn 2048 ký tự; API phải trả 400 thay vì để database phát sinh lỗi 500. */
    @Test
    void oversizedImageIsRejected() throws Exception {
        Map<String, Object> payload = payload();
        payload.put("image_url", "https://example.com/" + "a".repeat(2048));
        mvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("imageUrl"));
    }

    /** Role khách, quản lý và nhân viên kho đều không được sửa ảnh hay tạo sản phẩm qua endpoint ADMIN. */
    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "MANAGER", "WAREHOUSE_STAFF"})
    void nonAdminCannotCreateOrEditProductImage(String role) throws Exception {
        Long id = create("https://example.com/admin.jpg").path("id").asLong();
        String unauthorizedToken = token(role);
        Map<String, Object> createPayload = payload();
        createPayload.put("image_url", "https://example.com/forbidden.jpg");
        mvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + unauthorizedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(createPayload)))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/products/{id}", id)
                        .header("Authorization", "Bearer " + unauthorizedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("image_url", "https://example.com/forbidden.jpg"))))
                .andExpect(status().isForbidden());
        assertThat(products.findById(id).orElseThrow().getImageUrl()).isEqualTo("https://example.com/admin.jpg");
    }

    /** Payload tối thiểu giống form quản trị và tương thích contract cũ khi chưa truyền ảnh. */
    private Map<String, Object> payload() {
        Map<String, Object> request = new HashMap<>();
        request.put("sku", "IMAGE-" + UUID.randomUUID());
        request.put("name", "Sản phẩm ảnh kiểm thử");
        request.put("category_id", category.getId());
        request.put("unit_price", new BigDecimal("250000"));
        request.put("status", "ACTIVE");
        return request;
    }

    /** Tạo sản phẩm qua HTTP để chạy Bean Validation và kiểm tra response thật. */
    private JsonNode create(String imageUrl) throws Exception {
        Map<String, Object> request = payload();
        if (imageUrl != null) {
            request.put("image_url", imageUrl);
        }
        return json.readTree(mvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    /** PATCH bằng JWT ADMIN; matcher ở từng ca xác nhận giữ, đổi hoặc xóa ảnh. */
    private org.springframework.test.web.servlet.ResultActions update(Long id, Map<String, Object> request)
            throws Exception {
        return mvc.perform(patch("/api/v1/products/{id}", id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    /** Actor đã lưu trong database để filter đọc role thực tế, không giả lập annotation phân quyền. */
    private String token(String role) {
        User user = users.save(new User(
                "image-" + UUID.randomUUID() + "@example.com",
                "hash-kiểm-thử",
                "Người kiểm thử ảnh",
                roles.findByName(role).orElseThrow()));
        return jwt.generateToken(user);
    }
}
