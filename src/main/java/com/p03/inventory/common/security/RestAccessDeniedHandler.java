package com.p03.inventory.common.security;

import com.p03.inventory.common.audit.SecurityEventLogger;
import com.p03.inventory.common.error.ApiException;
import com.p03.inventory.common.error.ErrorCode;
import com.p03.inventory.common.error.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 403 ở tầng filter (đã đăng nhập nhưng URL bị chặn). Lỗi 403 từ @PreAuthorize do GlobalExceptionHandler xử lý;
 * cả hai đều trả FORBIDDEN_ROLE và ghi audit ACCESS_DENIED (IN01).
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ErrorResponseWriter writer;
    private final SecurityEventLogger securityEventLogger;

    public RestAccessDeniedHandler(ErrorResponseWriter writer, SecurityEventLogger securityEventLogger) {
        this.writer = writer;
        this.securityEventLogger = securityEventLogger;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        securityEventLogger.accessDenied(ApiException.AuditReason.FORBIDDEN_ROLE, null, null,
                request.getMethod() + " " + request.getRequestURI());
        writer.write(response, ErrorCode.FORBIDDEN_ROLE);
    }
}
