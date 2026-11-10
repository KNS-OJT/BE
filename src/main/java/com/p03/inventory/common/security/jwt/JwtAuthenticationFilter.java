package com.p03.inventory.common.security.jwt;

import com.p03.inventory.common.error.ErrorCode;
import com.p03.inventory.common.security.AppUserPrincipal;
import com.p03.inventory.common.security.PrincipalLoader;
import com.p03.inventory.common.web.RequestContext;
import com.p03.inventory.common.web.RequestIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Đọc "Authorization: Bearer &lt;JWT&gt;", kiểm tra token rồi tải lại user từ database (is_active, locked_until).
 * <p>
 * Filter này KHÔNG tự trả lỗi: nếu token sai/hết hạn nó chỉ ghi lý do vào request attribute và để request đi tiếp
 * không xác thực. Endpoint cần đăng nhập sẽ bị {@code RestAuthenticationEntryPoint} chặn với 401 đúng mã
 * (TOKEN_EXPIRED / UNAUTHENTICATED). Không đăng ký làm @Component để tránh Spring Boot chạy nó 2 lần.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ATTR_AUTH_ERROR = "p03.authError";
    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final PrincipalLoader principalLoader;

    public JwtAuthenticationFilter(JwtService jwtService, PrincipalLoader principalLoader) {
        this.jwtService = jwtService;
        this.principalLoader = principalLoader;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null) {
            authenticate(header, request);
        }
        chain.doFilter(request, response);
    }

    private void authenticate(String header, HttpServletRequest request) {
        if (!header.startsWith(BEARER) || header.length() <= BEARER.length()) {
            request.setAttribute(ATTR_AUTH_ERROR, ErrorCode.UNAUTHENTICATED);
            return;
        }
        try {
            Long userId = jwtService.parseUserId(header.substring(BEARER.length()).trim());
            Optional<AppUserPrincipal> principal = principalLoader.loadActive(userId);
            if (principal.isEmpty()) {
                // Tài khoản bị khóa / vô hiệu hóa: mất quyền ngay, không đợi token hết hạn (ADR-04)
                request.setAttribute(ATTR_AUTH_ERROR, ErrorCode.UNAUTHENTICATED);
                return;
            }
            AppUserPrincipal p = principal.get();
            var authentication = UsernamePasswordAuthenticationToken.authenticated(p, null, p.authorities());
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

            request.setAttribute(RequestIdFilter.ATTR_USER_ID, p.id());
            MDC.put(RequestContext.MDC_USER_ID, p.id().toString());
        } catch (JwtValidationException e) {
            request.setAttribute(ATTR_AUTH_ERROR, e.code());
        }
    }
}
