package com.p03.inventory.common.security.jwt;

import com.p03.inventory.common.error.ErrorCode;
import com.p03.inventory.common.security.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Phát và kiểm tra JWT theo SDD mục 6 / IA-NF01:
 * <ul>
 *   <li>Ký HS256, khóa ≥ 256 bit; khóa ngắn hơn → ứng dụng không khởi động.</li>
 *   <li>Claims: sub (user_id), role, iat, exp = iat + 30 phút, jti, iss. Không có dữ liệu nhạy cảm.</li>
 *   <li>Khi kiểm tra: cố định thuật toán HS256 (từ chối alg none / thuật toán khác), kiểm tra chữ ký, exp, iss.</li>
 * </ul>
 */
@Service
public class JwtService {

    public static final String ALGORITHM = "HS256";
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final Duration ttl;
    private final String issuer;
    private final Clock clock;

    public JwtService(JwtProperties props, Clock clock) {
        if (props.secret() == null || props.secret().getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET phải có ít nhất " + MIN_SECRET_BYTES
                    + " byte (256 bit). Sinh bằng: openssl rand -base64 48");
        }
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
        this.ttl = props.ttl();
        this.issuer = props.issuer();
        this.clock = clock;
    }

    public record IssuedToken(String accessToken, long expiresIn) {
    }

    public IssuedToken issue(Long userId, Role role) {
        Instant now = clock.instant();
        String token = Jwts.builder()
                .header().type("JWT").and()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .subject(userId.toString())
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        return new IssuedToken(token, ttl.toSeconds());
    }

    /**
     * @return user_id trong claim sub
     * @throws JwtValidationException TOKEN_EXPIRED nếu hết hạn, UNAUTHENTICATED cho mọi lỗi khác
     */
    public Long parseUserId(String token) {
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token); // từ chối JWT không ký (alg none)
            if (!ALGORITHM.equals(jws.getHeader().getAlgorithm())) {
                throw new JwtValidationException(ErrorCode.UNAUTHENTICATED);
            }
            return Long.valueOf(jws.getPayload().getSubject());
        } catch (ExpiredJwtException e) {
            throw new JwtValidationException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new JwtValidationException(ErrorCode.UNAUTHENTICATED);
        }
    }
}
