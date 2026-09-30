package com.stockflow.auth.api;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.auth.security.JwtTokenProvider;
import com.stockflow.user.domain.User;
import com.stockflow.user.repository.RoleRepository;
import com.stockflow.user.repository.UserRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration test cho luồng authentication sử dụng MockMvc, H2, Flyway, BCrypt và JWT thật.
 * Test này kiểm tra behavior qua HTTP layer thay vì chỉ test service riêng lẻ.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository users;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private JwtTokenProvider jwt;

    @Autowired
    private PasswordEncoder passwords;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * Kiểm tra đăng ký thành công phải tạo user CUSTOMER, trả về Bearer token và không làm lộ password hash.
     */
    @Test
    void registerCreatesCustomerAndReturnsToken() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(registerPayload(email, "secret123", "Nguyen Van A"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.access_token", not(blankOrNullString())))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.full_name").value("Nguyen Van A"))
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.user.status").value("ACTIVE"));
    }

    /**
     * Kiểm tra đăng ký trùng email phải trả 409 để client biết request xung đột với dữ liệu hiện tại.
     */
    @Test
    void registerDuplicateEmailReturnsConflict() throws Exception {
        String email = uniqueEmail();
        String payload = toJson(registerPayload(email, "secret123", "Nguyen Van A"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/register"));
    }

    /**
     * Kiểm tra đăng nhập đúng credential phải trả về JWT access token với token_type là Bearer.
     */
    @Test
    void loginReturnsJwtToken() throws Exception {
        String email = uniqueEmail();
        registerUser(email, "secret123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(loginPayload(email, "secret123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token", not(blankOrNullString())))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value(email));
    }

    /**
     * Kiểm tra endpoint /users/me trả 200 khi có JWT hợp lệ và trả 401 khi không có token.
     */
    @Test
    void meRequiresValidJwtToken() throws Exception {
        String email = uniqueEmail();
        String token = registerUser(email, "secret123");

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    /** Tài khoản INACTIVE không được phát token dù mật khẩu đúng; lỗi giống đăng nhập sai. */
    @Test
    void inactiveAccountCannotLogin() throws Exception {
        String email = uniqueEmail();
        registerUser(email, "secret123");
        jdbc.update("""
                UPDATE users
                SET status = 'INACTIVE'
                WHERE email = ?
                """, email);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(loginPayload(email, "secret123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email hoặc mật khẩu không đúng."))
                .andExpect(jsonPath("$.access_token").doesNotExist());
    }

    /** JWT đã phát cho mọi role mất quyền trên API bảo vệ khi tài khoản bị vô hiệu hóa. */
    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "MANAGER", "WAREHOUSE_STAFF", "CUSTOMER"})
    void issuedTokenCannotAuthenticateInactiveAccount(String role) throws Exception {
        User actor = users.save(new User(
                uniqueEmail(), passwords.encode("secret123"), "Tài khoản kiểm thử trạng thái",
                roles.findByName(role).orElseThrow()));
        String token = "Bearer " + jwt.generateToken(actor);
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", token))
                .andExpect(status().isOk());
        jdbc.update("""
                UPDATE users
                SET status = 'INACTIVE'
                WHERE id = ?
                """, actor.getId());
        String operationalPath = role.equals("CUSTOMER") ? "/api/v1/orders/my" : "/api/v1/orders";
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(operationalPath).header("Authorization", token))
                .andExpect(status().isUnauthorized());
    }

    /** Helper đăng ký user và trả về access token để các test khác dùng lại. */
    private String registerUser(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(registerPayload(email, password, "Nguyen Van A"))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("access_token").asText();
    }

    /**
     * Tạo payload register dùng snake_case như API contract.
     */
    private Map<String, String> registerPayload(String email, String password, String fullName) {
        Map<String, String> payload = new HashMap<>();
        payload.put("email", email);
        payload.put("password", password);
        payload.put("full_name", fullName);
        return payload;
    }

    /**
     * Tạo payload login cho test credential.
     */
    private Map<String, String> loginPayload(String email, String password) {
        Map<String, String> payload = new HashMap<>();
        payload.put("email", email);
        payload.put("password", password);
        return payload;
    }

    /**
     * Serialize payload thành JSON bằng ObjectMapper của Spring để tránh ghép chuỗi thủ công.
     */
    private String toJson(Object payload) throws Exception {
        return objectMapper.writeValueAsString(payload);
    }

    /**
     * Tạo email duy nhất cho mỗi test để tránh đụng dữ liệu giữa các test trong cùng H2 context.
     */
    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
