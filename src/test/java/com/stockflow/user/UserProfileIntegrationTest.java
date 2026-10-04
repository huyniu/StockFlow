package com.stockflow.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.auth.security.JwtTokenProvider;
import com.stockflow.catalog.domain.Category;
import com.stockflow.catalog.domain.Product;
import com.stockflow.catalog.domain.ProductStatus;
import com.stockflow.catalog.repository.CategoryRepository;
import com.stockflow.catalog.repository.ProductRepository;
import com.stockflow.common.exception.BadRequestException;
import com.stockflow.common.exception.UnauthorizedException;
import com.stockflow.inventory.dto.StockInRequest;
import com.stockflow.inventory.service.InventoryService;
import com.stockflow.order.dto.CreateOrderRequest;
import com.stockflow.order.dto.DeliveryDetailsRequest;
import com.stockflow.order.service.OrderService;
import com.stockflow.user.domain.User;
import com.stockflow.user.dto.UpdateProfileRequest;
import com.stockflow.user.repository.RoleRepository;
import com.stockflow.user.repository.UserRepository;
import com.stockflow.user.service.UserProfileService;
import com.stockflow.warehouse.domain.Warehouse;
import com.stockflow.warehouse.domain.WarehouseStatus;
import com.stockflow.warehouse.repository.WarehouseRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Kiểm chứng hồ sơ qua JWT thật, quyền sở hữu, cập nhật từng trường và bảo toàn dữ liệu đặt hàng. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:stockflow_profile;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserProfileIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JwtTokenProvider jwt;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private UserProfileService profiles;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwords;
    @Autowired private CategoryRepository categories;
    @Autowired private ProductRepository products;
    @Autowired private WarehouseRepository warehouses;
    @Autowired private InventoryService inventory;
    @Autowired private OrderService orders;

    private User customer;
    private String token;

    /** Mỗi bài dùng tài khoản riêng; không xóa ledger hoặc dùng transaction test che hành vi ghi thật. */
    @BeforeEach
    void setup() {
        customer = user("CUSTOMER");
        token = bearer(customer);
    }

    /** Khách chưa đăng nhập hoặc gửi JWT sai không được xem/sửa hồ sơ. */
    @ParameterizedTest
    @ValueSource(strings = {"", "Bearer invalid-token"})
    void unauthenticatedRequestsAreRejected(String authorization) throws Exception {
        mvc.perform(get("/api/v1/users/me").header("Authorization", authorization))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/users/me").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"full_name\":\"Tên mới\"}"))
                .andExpect(status().isUnauthorized());
    }

    /** Mỗi role sửa được liên hệ của chính mình; token cũ vẫn nạp lại thông tin mới từ database. */
    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "WAREHOUSE_STAFF", "MANAGER", "ADMIN"})
    void authenticatedRolesCanUpdateOnlyTheirOwnContact(String role) throws Exception {
        User actor = user(role);
        String authorization = bearer(actor);
        mvc.perform(patch("/api/v1/users/me").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "full_name", "  Nguyễn Minh An  ", "phone", " +84 (90) 123-4567 "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(actor.getId()))
                .andExpect(jsonPath("$.full_name").value("Nguyễn Minh An"))
                .andExpect(jsonPath("$.phone").value("+84901234567"))
                .andExpect(jsonPath("$.role").value(role))
                .andExpect(jsonPath("$.password_hash").doesNotExist());
        mvc.perform(get("/api/v1/users/me").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+84901234567"));
    }

    /** Payload cố đổi ID/email/quyền/mật khẩu không thể tác động tài khoản khác hoặc trường bảo mật. */
    @Test
    void protectedFieldsAndOtherAccountRemainUnchanged() throws Exception {
        User other = user("ADMIN");
        var otherBefore = accountSnapshot(other.getId());
        var actorBefore = accountSnapshot(customer.getId());
        var payload = Map.of(
                "full_name", "Khách cập nhật", "phone", "0901234567",
                "id", other.getId(), "email", other.getEmail(), "role", "ADMIN",
                "role_id", other.getRole().getId(), "status", "INACTIVE", "password_hash", "new-hash");
        update(payload).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customer.getId()))
                .andExpect(jsonPath("$.email").value(customer.getEmail()))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        assertThat(accountSnapshot(other.getId())).isEqualTo(otherBefore);
        assertThat(accountSnapshot(customer.getId())).isEqualTo(actorBefore);
        mvc.perform(patch("/api/v1/users/" + other.getId()).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"full_name\":\"Tên khác\"}"))
                .andExpect(status().isNotFound());
    }

    /** Bỏ qua và null giữ trường cũ; số rỗng xóa thành null và không xóa họ tên. */
    @Test
    void partialUpdatesAndExplicitPhoneRemovalHaveDifferentSemantics() throws Exception {
        update(Map.of("phone", "0901234567")).andExpect(status().isOk())
                .andExpect(jsonPath("$.full_name").value(customer.getFullName()));
        var nameOnly = new HashMap<String, Object>();
        nameOnly.put("full_name", "Tên đã sửa");
        nameOnly.put("phone", null);
        update(nameOnly).andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("0901234567"));
        var phoneOnly = new HashMap<String, Object>();
        phoneOnly.put("full_name", null);
        phoneOnly.put("phone", "12345678");
        update(phoneOnly).andExpect(status().isOk())
                .andExpect(jsonPath("$.full_name").value("Tên đã sửa"));
        update(Map.of("phone", "   ")).andExpect(status().isOk());
        assertThat(users.findById(customer.getId()).orElseThrow().getPhone()).isNull();
        assertThat(users.findById(customer.getId()).orElseThrow().getFullName()).isEqualTo("Tên đã sửa");
    }

    /** Request không có trường chỉnh sửa, JSON null hoặc chỉ chứa trường đặc quyền đều nhận 400. */
    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{\"full_name\":null,\"phone\":null}", "{\"role\":\"ADMIN\"}"})
    void emptyEditablePayloadIsRejected(String payload) throws Exception {
        mvc.perform(patch("/api/v1/users/me").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest());
        assertThat(users.findById(customer.getId()).orElseThrow().getFullName()).isEqualTo(customer.getFullName());
    }

    /** Từng trường sai phải trả 400 và không được ghi nửa request xuống database. */
    @ParameterizedTest
    @MethodSource("invalidContact")
    void invalidContactNeverPartiallySaves(String field, String value) throws Exception {
        var payload = new HashMap<String, Object>(Map.of("full_name", "Tên mới", "phone", "0901234567"));
        payload.put(field, value);
        update(payload).andExpect(status().isBadRequest());
        var persisted = users.findById(customer.getId()).orElseThrow();
        assertThat(persisted.getFullName()).isEqualTo(customer.getFullName());
        assertThat(persisted.getPhone()).isNull();
    }

    /** Biên tối đa của tên và số quốc tế được chấp nhận, không tự đổi mã quốc gia. */
    @Test
    void maximumLengthsAndOptionalPhoneAreAccepted() throws Exception {
        update(Map.of("full_name", "Á".repeat(150), "phone", "+123456789012345"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.phone").value("+123456789012345"));
    }

    /** Tài khoản vừa bị khóa không thể sử dụng JWT cũ để cập nhật liên hệ. */
    @Test
    void inactiveAccountCannotUpdateWithPreviouslyIssuedToken() throws Exception {
        jdbc.update("""
                UPDATE users
                SET status = 'INACTIVE'
                WHERE id = ?
                """, customer.getId());
        update(Map.of("full_name", "Tên không được lưu")).andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> profiles.updateProfile(customer.getId(), new UpdateProfileRequest("Tên mới", null)))
                .isInstanceOf(UnauthorizedException.class);
        assertThat(users.findById(customer.getId()).orElseThrow().getFullName()).isEqualTo(customer.getFullName());
    }

    /** Service cũng kiểm tra dữ liệu khi được gọi ngoài HTTP; không phụ thuộc riêng annotation @Valid. */
    @Test
    void serviceValidatesRequestAndAuthentication() {
        assertThatThrownBy(() -> profiles.updateProfile(customer.getId(), new UpdateProfileRequest("", null)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> profiles.updateProfile(customer.getId(), new UpdateProfileRequest(null, null)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> profiles.updateProfile(null, new UpdateProfileRequest("Tên mới", null)))
                .isInstanceOf(UnauthorizedException.class);
    }

    /** Hai transaction cập nhật trường khác nhau cùng lúc không làm mất tên hoặc số đã ghi bởi luồng kia. */
    @Test
    void concurrentPartialUpdatesPreserveBothFields() throws Exception {
        var start = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var nameUpdate = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return profiles.updateProfile(customer.getId(), new UpdateProfileRequest("Tên đồng thời", null));
            });
            var phoneUpdate = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return profiles.updateProfile(customer.getId(), new UpdateProfileRequest(null, "0901234567"));
            });
            nameUpdate.get(10, TimeUnit.SECONDS);
            phoneUpdate.get(10, TimeUnit.SECONDS);
            var persisted = users.findById(customer.getId()).orElseThrow();
            assertThat(persisted.getFullName()).isEqualTo("Tên đồng thời");
            assertThat(persisted.getPhone()).isEqualTo("0901234567");
        } finally {
            executor.shutdownNow();
        }
    }

    /** Đổi liên hệ không đổi credential: BCrypt cũ vẫn đăng nhập được và trả hồ sơ mới. */
    @Test
    void contactUpdateDoesNotChangeLoginCredentials() throws Exception {
        jdbc.update("""
                UPDATE users
                SET password_hash = ?
                WHERE id = ?
                """, passwords.encode("Profile@123"), customer.getId());
        update(Map.of("full_name", "Tên mới", "phone", "0901234567")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", customer.getEmail(), "password", "Profile@123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.full_name").value("Tên mới"))
                .andExpect(jsonPath("$.user.phone").value("0901234567"));
    }

    /** Thông tin nhận hàng, giá, trạng thái, tồn và ledger của đơn đã tạo không phụ thuộc hồ sơ mới. */
    @Test
    void existingOrderDeliveryAndInventoryRemainUnchanged() throws Exception {
        String key = UUID.randomUUID().toString();
        var category = categories.save(new Category("Danh mục hồ sơ " + key, key));
        var product = products.save(new Product(category, key, "Sản phẩm hồ sơ", new BigDecimal("100000"),
                ProductStatus.ACTIVE));
        var warehouse = warehouses.save(new Warehouse(key, "Kho kiểm thử", "Hà Nội", WarehouseStatus.ACTIVE));
        var admin = user("ADMIN");
        inventory.stockIn(new StockInRequest(product.getId(), warehouse.getId(), 5, "Nhập hàng kiểm thử"), admin.getId());
        var order = orders.createOrder(new CreateOrderRequest(warehouse.getId(),
                List.of(new CreateOrderRequest.Item(product.getId(), 2)),
                new DeliveryDetailsRequest("Người nhận riêng", "0987654321", "12 Phố Mới, Hà Nội", "Gọi trước")),
                customer.getId());
        var orderBefore = jdbc.queryForMap("""
                SELECT *
                FROM orders
                WHERE id = ?
                """, order.id());
        var inventoryBefore = jdbc.queryForList("""
                SELECT *
                FROM inventories
                WHERE product_id = ?
                """, product.getId());
        var ledgerBefore = jdbc.queryForList("""
                SELECT m.*
                FROM inventory_movements m
                JOIN inventories i ON i.id = m.inventory_id
                WHERE i.product_id = ?
                ORDER BY m.id
                """, product.getId());
        update(Map.of("full_name", "Chủ tài khoản mới", "phone", "0901234567")).andExpect(status().isOk());
        assertThat(jdbc.queryForMap("""
                SELECT *
                FROM orders
                WHERE id = ?
                """, order.id())).isEqualTo(orderBefore);
        assertThat(jdbc.queryForList("""
                SELECT *
                FROM inventories
                WHERE product_id = ?
                """, product.getId())).isEqualTo(inventoryBefore);
        assertThat(jdbc.queryForList("""
                SELECT m.*
                FROM inventory_movements m
                JOIN inventories i ON i.id = m.inventory_id
                WHERE i.product_id = ?
                ORDER BY m.id
                """, product.getId())).isEqualTo(ledgerBefore);
        mvc.perform(get("/api/v1/orders/" + order.id()).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delivery.recipient_name").value("Người nhận riêng"))
                .andExpect(jsonPath("$.delivery.recipient_phone").value("0987654321"));
    }

    /** Bộ dữ liệu lỗi gồm tên trống/quá dài/ký tự điều khiển và số sai định dạng. */
    static Stream<Arguments> invalidContact() {
        return Stream.of(
                Arguments.of("full_name", ""),
                Arguments.of("full_name", "   "),
                Arguments.of("full_name", "N".repeat(151)),
                Arguments.of("full_name", "Tên\nkhông hợp lệ"),
                Arguments.of("phone", "1234567"),
                Arguments.of("phone", "1234567890123456"),
                Arguments.of("phone", "090abcdefg"),
                Arguments.of("phone", "++84901234567"),
                Arguments.of("phone", "０９０１２３４５６７"));
    }

    /** Lưu người dùng có email riêng; hash giả chỉ dùng ở bài không kiểm tra đăng nhập bằng mật khẩu. */
    private User user(String role) {
        return users.save(new User(UUID.randomUUID() + "@profile.test", "existing-hash", "Khách ban đầu",
                roles.findByName(role).orElseThrow()));
    }

    /** Phát JWT thật để kiểm chứng filter đọc lại trạng thái/quyền thay vì giả lập @WithMockUser. */
    private String bearer(User actor) {
        return "Bearer " + jwt.generateToken(actor);
    }

    /** Gửi PATCH với tài khoản fixture, giữ từng bài tập trung vào quy tắc cần nghiệm thu. */
    private org.springframework.test.web.servlet.ResultActions update(Object payload) throws Exception {
        return mvc.perform(patch("/api/v1/users/me").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)));
    }

    /** Chụp các trường định danh/quyền/mật khẩu để phát hiện mass assignment mà không log hash. */
    private Map<String, Object> accountSnapshot(Long id) {
        return jdbc.queryForMap("""
                SELECT id,
                       email,
                       password_hash,
                       role_id,
                       status,
                       created_at
                FROM users
                WHERE id = ?
                """, id);
    }
}
