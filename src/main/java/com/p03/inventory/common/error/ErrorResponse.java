package com.p03.inventory.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Định dạng lỗi thống nhất (SDD mục 4):
 * <pre>{ "status": 409, "code": "INVALID_STATE", "message": "...", "requestId": "3f1c…" }</pre>
 * {@code details} chỉ xuất hiện với lỗi validation, liệt kê từng trường sai để FE hiển thị.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(int status, String code, String message, String requestId, List<FieldViolation> details) {

    public record FieldViolation(String field, String message) {
    }

    public static ErrorResponse of(ErrorCode code, String message, String requestId) {
        return new ErrorResponse(code.status().value(), code.name(), message, requestId, List.of());
    }

    public static ErrorResponse of(ErrorCode code, String message, String requestId, List<FieldViolation> details) {
        return new ErrorResponse(code.status().value(), code.name(), message, requestId, details);
    }
}
