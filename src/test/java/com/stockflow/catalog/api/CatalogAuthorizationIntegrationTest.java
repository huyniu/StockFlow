package com.stockflow.catalog.api;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.auth.security.JwtTokenProvider;
import com.stockflow.catalog.domain.Category;
import com.stockflow.catalog.repository.CategoryRepository;
import com.stockflow.user.domain.Role;
import com.stockflow.user.domain.User;
import com.stockflow.user.repository.RoleRepository;
import com.stockflow.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration test kiểm tra phân quyền của Milestone 2 cho catalog.
 * Test dùng JWT thật để đi qua cùng filter chain như request thực tế.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogAuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    /**
     * Customer đã đăng nhập được phép xem danh sách sản phẩm vì catalog là dữ liệu công khai cho người mua.
     */
    @Test
    void customerCanViewProductList() throws Exception {
        String customerToken = registerCustomerAndGetToken();

        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    /**
     * Customer không được tạo sản phẩm; request phải bị chặn ở tầng authorization với 403.
     */
    @Test
    void customerCannotCreateProduct() throws Exception {
        String customerToken = registerCustomerAndGetToken();
        Category category = createCategory();

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(productPayload(category.getId(), "SKU-" + UUID.randomUUID(), BigDecimal.TEN))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    /**
     * Admin được phép tạo sản phẩm mới và API phải trả 201 Created cùng access data của sản phẩm.
     */
    @Test
    void adminCanCreateProduct() throws Exception {
        String adminToken = createAdminToken();
        Category category = createCategory();
        String sku = "SKU-" + UUID.randomUUID();

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(productPayload(category.getId(), sku, BigDecimal.valueOf(125000)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.sku").value(sku.toUpperCase()))
                .andExpect(jsonPath("$.category_name").value(category.getName()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    /**
     * Giá sản phẩm âm phải bị Bean Validation chặn trước khi service tạo Product.
     */
    @Test
    void productPriceMustNotBeNegative() throws Exception {
        String adminToken = createAdminToken();
        Category category = createCategory();

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(productPayload(category.getId(), "SKU-" + UUID.randomUUID(), BigDecimal.valueOf(-1)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0].field").value("unitPrice"));
    }

    /**
     * Đăng ký customer qua API public để token customer đi qua đúng luồng auth thật.
     */
    private String registerCustomerAndGetToken() throws Exception {
        Map<String, String> payload = new HashMap<>();
        payload.put("email", "customer-" + UUID.randomUUID() + "@example.com");
        payload.put("password", "secret123");
        payload.put("full_name", "Khách hàng kiểm thử");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.access_token", not(blankOrNullString())))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("access_token").asText();
    }

    /**
     * Tạo admin trực tiếp trong database test để kiểm tra role ADMIN mà không mở API tạo user quản trị ở Milestone 2.
     */
    private String createAdminToken() {
        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseThrow(() -> new IllegalStateException("Thiếu role ADMIN trong dữ liệu test."));
        User admin = userRepository.save(new User(
                "admin-" + UUID.randomUUID() + "@example.com",
                passwordEncoder.encode("secret123"),
                "Quản trị viên kiểm thử",
                adminRole));
        return jwtTokenProvider.generateToken(admin);
    }

    /**
     * Tạo category trực tiếp để product test có khóa ngoại hợp lệ mà không phụ thuộc vào endpoint category.
     */
    private Category createCategory() {
        String suffix = UUID.randomUUID().toString();
        return categoryRepository.save(new Category("Danh mục " + suffix, "danh-muc-" + suffix));
    }

    /**
     * Tạo payload sản phẩm đúng API contract, dùng snake_case cho category_id và unit_price.
     */
    private Map<String, Object> productPayload(Long categoryId, String sku, BigDecimal unitPrice) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sku", sku);
        payload.put("name", "Sản phẩm kiểm thử");
        payload.put("category_id", categoryId);
        payload.put("unit_price", unitPrice);
        payload.put("status", "ACTIVE");
        return payload;
    }

    /**
     * Serialize payload sang JSON bằng ObjectMapper của Spring để tránh lỗi escape thủ công.
     */
    private String toJson(Object payload) throws Exception {
        return objectMapper.writeValueAsString(payload);
    }
}
