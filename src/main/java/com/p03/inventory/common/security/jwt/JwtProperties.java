package com.p03.inventory.common.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Cấu hình JWT (SDD mục 6): HS256, khóa ≥ 256 bit từ biến môi trường JWT_SECRET, hạn 30 phút.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, Duration ttl, String issuer) {

    public JwtProperties {
        if (ttl == null) {
            ttl = Duration.ofMinutes(30);
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "p03-inventory";
        }
    }
}
