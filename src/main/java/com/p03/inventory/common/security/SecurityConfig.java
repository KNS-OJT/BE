package com.p03.inventory.common.security;

import com.p03.inventory.common.security.jwt.JwtAuthenticationFilter;
import com.p03.inventory.common.security.jwt.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

/**
 * Cấu hình bảo mật chung (SDD mục 1, 6):
 * <ul>
 *   <li>Stateless, không session, không CSRF (API dùng Bearer token, không dùng cookie).</li>
 *   <li>Chỉ POST /api/auth/login là public. Mọi /api/** cần JWT; mọi URL khác bị từ chối.</li>
 *   <li>Lớp RBAC chi tiết đặt bằng @PreAuthorize trên từng endpoint; endpoint thiếu annotation
 *       làm app không khởi động (EndpointSecurityVerifier) → "mặc định từ chối".</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity(jsr250Enabled = true)
public class SecurityConfig {

    public static final int BCRYPT_COST = 12;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtService jwtService,
                                                   PrincipalLoader principalLoader,
                                                   RestAuthenticationEntryPoint entryPoint,
                                                   RestAccessDeniedHandler accessDeniedHandler,
                                                   @Value("${springdoc.api-docs.enabled:false}") boolean apiDocsEnabled)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {
                })
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll();
                    auth.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
                    auth.requestMatchers("/error").permitAll();
                    if (apiDocsEnabled) {
                        auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                    }
                    auth.requestMatchers("/api/**").authenticated();
                    auth.anyRequest().denyAll();
                })
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, principalLoader),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_COST);
    }

    /**
     * CORS chỉ cần ở môi trường dev (Vite chạy cổng khác). Production đi qua Nginx cùng origin nên để trống.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:}") List<String> allowedOrigins) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> origins = allowedOrigins.stream().filter(o -> !o.isBlank()).toList();
        if (!origins.isEmpty()) {
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOrigins(origins);
            config.setAllowedMethods(List.of("GET", "POST", "PUT", "OPTIONS"));
            config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id"));
            config.setExposedHeaders(List.of("X-Request-Id"));
            config.setAllowCredentials(false);
            config.setMaxAge(Duration.ofHours(1));
            source.registerCorsConfiguration("/api/**", config);
        }
        return source;
    }
}
