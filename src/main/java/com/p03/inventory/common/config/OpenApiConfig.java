package com.p03.inventory.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Khung OpenAPI 3 (SDD mục 4). Spec sinh từ code; Swagger UI chỉ bật ở profile dev.
 * Khoa phụ trách tổng hợp contract và xuất openapi.yaml vào repo.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI p03OpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("P03 Inventory & Purchase Request API")
                        .version("v1")
                        .description("Base path /api. Mọi endpoint cần Authorization: Bearer <JWT>, trừ POST /api/auth/login."))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
