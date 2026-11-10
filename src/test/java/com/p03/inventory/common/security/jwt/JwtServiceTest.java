package com.p03.inventory.common.security.jwt;

import com.p03.inventory.common.error.ErrorCode;
import com.p03.inventory.common.security.Role;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SEC-TC-317: JWT alg none, JWT hết hạn, JWT sửa role → đều bị từ chối. */
class JwtServiceTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-0123456789";
    private static final Instant T0 = Instant.parse("2026-11-01T00:00:00Z");

    private JwtService service(Instant now) {
        return new JwtService(new JwtProperties(SECRET, Duration.ofMinutes(30), "p03-inventory"),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void issueThenParse_returnsUserId() {
        JwtService s = service(T0);
        JwtService.IssuedToken t = s.issue(42L, Role.MANAGER);
        assertThat(t.expiresIn()).isEqualTo(1800);
        assertThat(s.parseUserId(t.accessToken())).isEqualTo(42L);
    }

    @Test
    void expiredToken_isTokenExpired() {
        String token = service(T0).issue(1L, Role.REQUESTER).accessToken();
        JwtService later = service(T0.plus(Duration.ofMinutes(31)));
        assertThatThrownBy(() -> later.parseUserId(token))
                .isInstanceOf(JwtValidationException.class)
                .extracting(e -> ((JwtValidationException) e).code())
                .isEqualTo(ErrorCode.TOKEN_EXPIRED);
    }

    @Test
    void algNone_isRejected() {
        String token = service(T0).issue(1L, Role.REQUESTER).accessToken();
        String[] parts = token.split("\\.");
        String noneHeader = b64("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        String forged = noneHeader + "." + parts[1] + ".";
        assertUnauthenticated(forged);
    }

    @Test
    void tamperedPayload_isRejected() {
        JwtService s = service(T0);
        String token = s.issue(5L, Role.REQUESTER).accessToken();
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace("\"REQUESTER\"", "\"MANAGER\"");
        assertUnauthenticated(parts[0] + "." + b64(payload) + "." + parts[2]);
    }

    @Test
    void tokenSignedWithOtherKey_isRejected() {
        JwtService other = new JwtService(new JwtProperties("another-secret-another-secret-another-secret!!",
                Duration.ofMinutes(30), "p03-inventory"), Clock.fixed(T0, ZoneOffset.UTC));
        assertUnauthenticated(other.issue(1L, Role.MANAGER).accessToken());
    }

    @Test
    void garbage_isRejected() {
        assertUnauthenticated("not-a-jwt");
    }

    @Test
    void shortSecret_failsFast() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties("too-short", null, null), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
    }

    private void assertUnauthenticated(String token) {
        assertThatThrownBy(() -> service(T0).parseUserId(token))
                .isInstanceOf(JwtValidationException.class)
                .extracting(e -> ((JwtValidationException) e).code())
                .isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }
}
