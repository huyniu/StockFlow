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
import java.nio.charset.StandardCharsets;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kiểm chứng mô tả qua HTTP, Bean Validation, JWT thật và persistence sau khi xóa cache JPA.
 * Dữ liệu của mỗi ca được rollback; sản phẩm cũ và quyền quản trị giữ nguyên contract hiện có.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductDescriptionIntegrationTest {

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

    /** Actor và danh mục riêng để test không cần catalog seed hoặc tài khoản của người dùng. */
    @BeforeEach
    void setUp() {
        adminToken = token("ADMIN");
        category = categories.save(new Category(
                "Danh mục mô tả " + UUID.randomUUID(),
                "mo-ta-" + UUID.randomUUID()));
    }

    /** Nội dung tiếng Việt và xuống dòng được lưu thật; khách vãng lai đọc được qua API chi tiết/danh sách. */
    @Test
    void adminCreatesDescriptionAndGuestReadsStoredText() throws Exception {
        String description = "Điện thoại màu cam.\nDung lượng: cấu hình demo.\nBảo hành theo chính sách cửa hàng.";
        JsonNode product = create("  " + description + "  ");
        Long id = product.path("id").asLong();
        assertThat(product.path("description").asText()).isEqualTo(description);
        assertStoredDescription(id, description);
        mvc.perform(get("/api/v1/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value(description));
        mvc.perform(get("/api/v1/products").param("q", product.path("sku").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].description").value(description));
    }

    /** Request chưa có mô tả vẫn tạo được sản phẩm để giỏ/order và seed cũ không bị bắt buộc đổi. */
    @Test
    void legacyCreateWithoutDescriptionRemainsCompatible() throws Exception {
        JsonNode product = create(null);
        assertThat(product.path("description").isNull()).isTrue();
        assertStoredDescription(product.path("id").asLong(), null);
    }

    /** Đúng biên 5.000 ký tự được API và schema chấp nhận, không bị cắt nội dung âm thầm. */
    @Test
    void maximumDescriptionLengthIsAccepted() throws Exception {
        String description = "Đ".repeat(5000);
        JsonNode product = create(description);
        assertStoredDescription(product.path("id").asLong(), description);
    }

    /** Sửa riêng mô tả không được làm đổi tên, SKU, giá hoặc ảnh đã có. */
    @Test
    void descriptionPatchPreservesOtherFields() throws Exception {
        JsonNode product = create("Mô tả ban đầu");
        Long id = product.path("id").asLong();
        update(id, Map.of("description", "  Nội dung đã sửa\nDòng thông tin thứ hai  "))
                .andExpect(jsonPath("$.description").value("Nội dung đã sửa\nDòng thông tin thứ hai"))
                .andExpect(jsonPath("$.name").value(product.path("name").asText()))
                .andExpect(jsonPath("$.sku").value(product.path("sku").asText()))
                .andExpect(jsonPath("$.unit_price").value(31990000))
                .andExpect(jsonPath("$.image_url").value("/assets/stockflow.svg"));
        assertStoredDescription(id, "Nội dung đã sửa\nDòng thông tin thứ hai");
    }

    /** Đổi giá/trạng thái hoặc gửi mô tả null phải giữ mô tả hiện có theo PATCH từng phần. */
    @Test
    void omittedOrNullDescriptionPreservesStoredText() throws Exception {
        Long id = create("Thông tin được giữ lại").path("id").asLong();
        Map<String, Object> payload = new HashMap<>();
        payload.put("unit_price", new BigDecimal("32000000"));
        update(id, payload).andExpect(jsonPath("$.description").value("Thông tin được giữ lại"));
        payload.put("description", null);
        update(id, payload).andExpect(jsonPath("$.description").value("Thông tin được giữ lại"));
        assertStoredDescription(id, "Thông tin được giữ lại");
    }

    /** Chuỗi trống là thao tác xóa rõ ràng; sau khi flush/clear, database cũng lưu null. */
    @ParameterizedTest
    @ValueSource(strings = {"", "  \n\t  "})
    void blankDescriptionExplicitlyClearsStoredText(String value) throws Exception {
        Long id = create("Nội dung cần xóa").path("id").asLong();
        update(id, Map.of("description", value)).andExpect(jsonPath("$.description").isEmpty());
        assertStoredDescription(id, null);
    }

    /** Vượt độ dài bị chặn 400 trước persistence; PATCH lỗi không làm mất mô tả đang dùng. */
    @Test
    void oversizedDescriptionIsRejectedOnCreateAndPatch() throws Exception {
        String oversized = "a".repeat(5001);
        long count = products.count();
        Map<String, Object> payload = payload();
        payload.put("description", oversized);
        mvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("description"));
        assertThat(products.count()).isEqualTo(count);
        Long id = create("Mô tả hợp lệ").path("id").asLong();
        mvc.perform(patch("/api/v1/products/{id}", id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("description", oversized))))
                .andExpect(status().isBadRequest());
        assertStoredDescription(id, "Mô tả hợp lệ");
    }

    /** Các role ngoài ADMIN chỉ đọc catalog; không được tạo hoặc chỉnh mô tả dù có JWT hợp lệ. */
    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "MANAGER", "WAREHOUSE_STAFF"})
    void nonAdminCannotCreateOrEditDescription(String role) throws Exception {
        Long id = create("Nội dung do Admin quản lý").path("id").asLong();
        String forbiddenToken = token(role);
        Map<String, Object> payload = payload();
        payload.put("description", "Nội dung không được phép");
        mvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + forbiddenToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(payload)))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/products/{id}", id)
                        .header("Authorization", "Bearer " + forbiddenToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("description", "Nội dung không được phép"))))
                .andExpect(status().isForbidden());
        assertStoredDescription(id, "Nội dung do Admin quản lý");
    }

    /** Khách chưa đăng nhập không được sửa catalog; việc đọc công khai không mở quyền PATCH. */
    @Test
    void guestCannotPatchDescription() throws Exception {
        Long id = create("Nội dung gốc").path("id").asLong();
        mvc.perform(patch("/api/v1/products/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("description", "Sửa không có JWT"))))
                .andExpect(status().isUnauthorized());
        assertStoredDescription(id, "Nội dung gốc");
    }

    /** Flush rồi đọc lại để xác nhận cột mô tả, không chỉ giá trị đang nằm trong cache entity. */
    private void assertStoredDescription(Long id, String description) {
        products.flush();
        entities.clear();
        assertThat(products.findById(id).orElseThrow().getDescription()).isEqualTo(description);
    }

    /** Payload cũ đủ trường bắt buộc và ảnh cục bộ; không phụ thuộc CDN khi kiểm thử nội dung. */
    private Map<String, Object> payload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sku", "DETAIL-" + UUID.randomUUID());
        payload.put("name", "Điện thoại kiểm thử");
        payload.put("category_id", category.getId());
        payload.put("unit_price", new BigDecimal("31990000"));
        payload.put("status", "ACTIVE");
        payload.put("image_url", "/assets/stockflow.svg");
        return payload;
    }

    /** Tạo qua controller thật để chạy phân quyền, validation và transaction của service. */
    private JsonNode create(String description) throws Exception {
        Map<String, Object> payload = payload();
        if (description != null) {
            payload.put("description", description);
        }
        return json.readTree(mvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** PATCH với token Admin thật; mỗi ca tự kiểm tra dữ liệu cần giữ/đổi/xóa. */
    private ResultActions update(Long id, Map<String, Object> payload) throws Exception {
        return mvc.perform(patch("/api/v1/products/{id}", id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(payload)))
                .andExpect(status().isOk());
    }

    /** JWT dùng actor đã lưu để filter chain lấy role từ database, không giả lập phân quyền. */
    private String token(String role) {
        User actor = users.save(new User(
                "description-" + UUID.randomUUID() + "@example.com",
                "hash-kiểm-thử",
                "Người kiểm thử mô tả",
                roles.findByName(role).orElseThrow()));
        return jwt.generateToken(actor);
    }
}
