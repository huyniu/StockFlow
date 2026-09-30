package com.stockflow.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Kiểm chứng Swagger public và hợp đồng JWT, không nới lỏng quyền của API nghiệp vụ. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    /** Trang Swagger, tài nguyên giao diện và cấu hình đều phải truy cập được khi chưa đăng nhập. */
    @Test
    void swaggerIsPublicWhileBusinessApiRemainsProtected() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    }

    /** Tài liệu phải khai báo Bearer JWT và chỉ gắn yêu cầu đăng nhập cho operation được bảo vệ. */
    @Test
    void specificationDocumentsJwtAndPublicOperations() throws Exception {
        JsonNode document = specification();
        assertThat(document.at("/info/title").asText()).isEqualTo("StockFlow API");
        assertThat(document.at("/info/version").asText()).isEqualTo("1.0");
        JsonNode scheme = document.path("components").path("securitySchemes").path("bearerAuth");
        assertThat(scheme.path("type").asText()).isEqualTo("http");
        assertThat(scheme.path("scheme").asText()).isEqualTo("bearer");
        assertThat(scheme.path("bearerFormat").asText()).isEqualTo("JWT");

        for (String path : List.of("/api/v1/orders", "/api/v1/products")) {
            assertThat(document.path("paths").path(path).path("post")
                    .path("security").get(0).has("bearerAuth")).isTrue();
        }
        assertThat(document.path("paths").path("/api/v1/reports/revenue").path("get")
                .path("security").get(0).has("bearerAuth")).isTrue();
        assertThat(document.path("paths").path("/api/v1/products").path("get")
                .path("security").isEmpty()).isTrue();
        assertThat(document.path("paths").path("/api/v1/auth/login").path("post")
                .path("security").isEmpty()).isTrue();
        assertThat(document.path("paths").path("/api/v1/orders").path("post")
                .path("summary").asText()).isNotBlank();
    }

    /** Phân trang xuất hiện dưới dạng tham số HTTP; principal lấy từ JWT không được expose cho người dùng. */
    @Test
    void pageableIsDocumentedAndPrincipalIsHidden() throws Exception {
        JsonNode paths = specification().path("paths");
        assertThat(parameterNames(paths.path("/api/v1/products").path("get")))
                .contains("page", "size", "sort", "categoryId", "status");
        assertThat(parameterNames(paths.path("/api/v1/inventories").path("get")))
                .contains("productId", "warehouseId", "page", "size")
                .doesNotContain("user", "email", "passwordHash", "role");
        assertThat(parameterNames(paths.path("/api/v1/orders/{id}").path("get")))
                .containsExactly("id");
    }

    /** Lấy OpenAPI qua filter chain thực để kiểm tra cả phân quyền lẫn JSON được sinh bởi SpringDoc. */
    private JsonNode specification() throws Exception {
        return json.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    /** Gom tên tham số để phát hiện việc tài liệu vô tình lộ entity người dùng hoặc sai hợp đồng phân trang. */
    private List<String> parameterNames(JsonNode operation) {
        List<String> names = new ArrayList<>();
        operation.path("parameters").forEach(parameter -> names.add(parameter.path("name").asText()));
        return names;
    }
}
