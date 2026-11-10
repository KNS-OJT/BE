package com.p03.inventory.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Filter chạy đầu tiên cho mọi request:
 * <ul>
 *   <li>Gán request_id (nhận từ Nginx nếu hợp lệ, không thì tự sinh UUID), trả về header X-Request-Id.</li>
 *   <li>Ghi app log 1 dòng / request: method, path, status, duration_ms, user_id (SDD mục 7).
 *       KHÔNG ghi body, query string, header Authorization.</li>
 * </ul>
 * client_ip lấy từ {@code request.getRemoteAddr()}: với {@code server.forward-headers-strategy=native},
 * Tomcat chỉ tin X-Forwarded-For từ proxy nội bộ (Nginx), đúng trust boundary TB1 → TB2.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final Logger ACCESS_LOG = LoggerFactory.getLogger("http.access");
    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    /** Attribute do JwtAuthenticationFilter đặt, để access log biết user (SecurityContext đã bị xóa khi tới đây). */
    public static final String ATTR_USER_ID = "p03.userId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        String incoming = request.getHeader(RequestContext.REQUEST_ID_HEADER);
        String requestId = incoming != null && SAFE_ID.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();

        MDC.put(RequestContext.MDC_REQUEST_ID, requestId);
        MDC.put(RequestContext.MDC_CLIENT_IP, request.getRemoteAddr());
        response.setHeader(RequestContext.REQUEST_ID_HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            Object userId = request.getAttribute(ATTR_USER_ID);
            if (userId != null) {
                MDC.put(RequestContext.MDC_USER_ID, userId.toString());
            }
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            MDC.put("method", request.getMethod());
            MDC.put("path", request.getRequestURI());
            MDC.put("status", Integer.toString(response.getStatus()));
            MDC.put("duration_ms", Long.toString(durationMs));
            ACCESS_LOG.info("{} {} {} {}ms", request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs);
            MDC.clear();
        }
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return true;
    }
}
