package com.stockflow.common.api;

import static com.stockflow.user.support.UserTestFixtures.verifiedUser;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.auth.security.JwtTokenProvider;
import com.stockflow.user.domain.User;
import com.stockflow.user.repository.RoleRepository;
import com.stockflow.user.repository.UserRepository;
import com.stockflow.warehouse.domain.Warehouse;
import com.stockflow.warehouse.domain.WarehouseStatus;
import com.stockflow.warehouse.repository.WarehouseRepository;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Kiểm chứng giao diện qua HTTP thật để xử lý cả forward của welcome page tại GET /.
 * Database riêng giữ nguyên dữ liệu của các test milestone khác và không nới quyền API.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:stockflow_web;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
})
@ActiveProfiles("test")
class WebDemoIntegrationTest {

    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired WarehouseRepository warehouses;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired JwtTokenProvider jwt;

    /** URL cửa hàng công nghệ/chi tiết mở công khai; kiểm tra các khu khám phá và dashboard vẫn tách riêng. */
    @ParameterizedTest
    @ValueSource(strings = {"/", "/index.html", "/san-pham/1", "/san-pham/42", "/san-pham/99999"})
    void dashboardIsPublic(String path) {
        ResponseEntity<String> response = http.getForEntity(path, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).startsWith("text/html");
        assertThat(response.getBody()).contains("StockFlow Tech", "type=\"module\"", "data-fragment", "src=\"/app.js\"");
        StringBuilder assembled = new StringBuilder(response.getBody());
        for (String fragment : java.util.List.of("chrome", "auth-page", "icons", "storefront", "dashboard", "dialogs", "aftercare")) {
            ResponseEntity<String> partial = http.getForEntity("/assets/fragments/" + fragment + ".html", String.class);
            assertThat(partial.getStatusCode()).isEqualTo(HttpStatus.OK);
            assembled.append(partial.getBody());
        }
        assertThat(assembled.toString()).contains("StockFlow Tech", "Sản phẩm & đặt hàng", "Báo cáo quản trị",
                "ĐIỆN THOẠI · LAPTOP · PHỤ KIỆN", "Tìm điện thoại, laptop, phụ kiện",
                "id=\"discovery-categories\"", "id=\"bestseller-grid\"",
                "src=\"/app.js\"", "href=\"/styles.css\"",
                "id=\"storefront-view\"", "id=\"dashboard-view\" hidden",
                "id=\"shop-product\"", "id=\"shop-product-detail-body\"", "Tiếp tục mua sắm",
                "class=\"brand-shop\"", "Giỏ hàng của bạn");
    }

    /** CSS/JS/icon và bảng màu sáng/tối phải public để người chưa đăng nhập tải được giao diện đầy đủ. */
    @ParameterizedTest
    @ValueSource(strings = {"/styles.css", "/app.js", "/assets/stockflow.svg", "/assets/theme.js", "/assets/theme.css"})
    void webAssetsArePublic(String path) {
        ResponseEntity<String> response = http.getForEntity(path, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotBlank();
    }

    /** Mở tài nguyên tĩnh không được làm API nội bộ hay endpoint chọn kho mất yêu cầu JWT. */
    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/users/me", "/api/v1/reports/order-summary", "/api/v1/warehouses/order-options"})
    void protectedApisStillRequireJwt(String path) {
        assertThat(http.getForEntity(path, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /** CUSTOMER chỉ nhận lựa chọn kho đang hoạt động, không có địa chỉ và vẫn bị chặn danh sách kho nội bộ. */
    @Test
    void customerGetsMinimalActiveWarehouseOptions() throws Exception {
        String key = UUID.randomUUID().toString();
        Warehouse active = warehouses.save(new Warehouse("ACTIVE-" + key, "Kho hoạt động", "Địa chỉ nội bộ", WarehouseStatus.ACTIVE));
        Warehouse inactive = warehouses.save(new Warehouse("INACTIVE-" + key, "Kho ngừng hoạt động", "Địa chỉ nội bộ", WarehouseStatus.INACTIVE));
        HttpEntity<Void> customer = authenticated("CUSTOMER");

        ResponseEntity<String> response = http.exchange("/api/v1/warehouses/order-options", HttpMethod.GET, customer, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode options = json.readTree(response.getBody());
        var entries = StreamSupport.stream(options.spliterator(), false).toList();
        JsonNode option = entries.stream().filter(row -> row.path("id").asLong() == active.getId()).findFirst().orElseThrow();
        assertThat(option.size()).isEqualTo(3);
        assertThat(option.has("address")).isFalse();
        assertThat(option.path("code").asText()).isEqualTo(active.getCode());
        assertThat(option.path("name").asText()).isEqualTo("Kho hoạt động");
        assertThat(entries).noneMatch(row -> row.path("id").asLong() == inactive.getId());
        assertThat(http.exchange("/api/v1/warehouses", HttpMethod.GET, customer, String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(http.exchange("/api/v1/warehouses", HttpMethod.GET, authenticated("ADMIN"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    /** Tạo JWT từ actor đã lưu trong database để request đi qua filter chain thật. */
    private HttpEntity<Void> authenticated(String role) {
        User actor = users.save(verifiedUser(UUID.randomUUID() + "@example.com", "hash-kiểm-thử", "Người kiểm thử web",
                roles.findByName(role).orElseThrow()));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt.generateToken(actor));
        return new HttpEntity<>(headers);
    }
}
