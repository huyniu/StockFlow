package com.stockflow.common.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Khai báo API của StockFlow Tech và cơ chế Authorize bằng Bearer JWT cho Swagger UI. */
@Configuration
public class OpenApiConfig {

    /** Tài liệu dùng security requirement tại từng controller hoặc operation để route public không bị gắn khóa. */
    @Bean
    public OpenAPI stockFlowOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("StockFlow API")
                        .version("1.0")
                        .description("""
                                StockFlow Tech: computer accessories and technology storefront
                                for one retailer owning multiple warehouses.
                                Reserve stock atomically, inspect an immutable inventory ledger,
                                simulate payments and query business reports.
                                Log in through /api/v1/auth/login, then paste access_token into Authorize.
                                Permissions: CUSTOMER, WAREHOUSE_STAFF, MANAGER and ADMIN.
                                """))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste the access_token value without the Bearer prefix.")));
    }
}
