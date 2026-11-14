package com.p03.inventory.common.config;

import com.p03.inventory.auth.AuthController;
import com.p03.inventory.auth.dto.LoginRequest;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiConfigTest {
    @Test
    void bearerSecurityIsGlobalButLoginOverridesIt() throws NoSuchMethodException {
        var openApi = new OpenApiConfig().p03OpenApi();

        assertNotNull(openApi.getSecurity());
        assertTrue(openApi.getSecurity().getFirst().containsKey("bearerAuth"));
        assertTrue(openApi.getInfo().getDescription().contains("Authorize"));

        SecurityRequirements loginSecurity = AuthController.class
                .getMethod("login", LoginRequest.class)
                .getAnnotation(SecurityRequirements.class);
        assertNotNull(loginSecurity);
        assertEquals(0, loginSecurity.value().length);
    }
}
