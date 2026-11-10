package com.p03.inventory.common.security;

import com.p03.inventory.common.error.ErrorCode;
import com.p03.inventory.common.error.ErrorResponseWriter;
import com.p03.inventory.common.security.jwt.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 401 cho request chưa xác thực: TOKEN_EXPIRED nếu token hết hạn, còn lại UNAUTHENTICATED. FE nhận 401 → về SCR-01. */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ErrorResponseWriter writer;

    public RestAuthenticationEntryPoint(ErrorResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        Object reason = request.getAttribute(JwtAuthenticationFilter.ATTR_AUTH_ERROR);
        ErrorCode code = reason == ErrorCode.TOKEN_EXPIRED ? ErrorCode.TOKEN_EXPIRED : ErrorCode.UNAUTHENTICATED;
        writer.write(response, code);
    }
}
