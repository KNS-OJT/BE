package com.p03.inventory.common.error;

import tools.jackson.databind.ObjectMapper;
import com.p03.inventory.common.web.RequestContext;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Ghi JSON lỗi chuẩn ở tầng filter (ngoài Spring MVC), dùng cho 401/403 của Spring Security. */
@Component
public class ErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public ErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, ErrorCode code) throws IOException {
        write(response, code, code.defaultMessage());
    }

    public void write(HttpServletResponse response, ErrorCode code, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(code, message, RequestContext.requestId()));
    }
}
