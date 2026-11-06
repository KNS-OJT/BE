package com.p03.inventory.common.error;

import tools.jackson.databind.exc.UnrecognizedPropertyException;
import com.p03.inventory.common.audit.SecurityEventLogger;
import com.p03.inventory.common.web.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Đổi mọi exception thành JSON lỗi chuẩn (SDD mục 4, IA-NF04). Không bao giờ trả stack trace hay chi tiết
 * kỹ thuật ra client; chi tiết chỉ nằm trong app log, nối với response bằng requestId.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final SecurityEventLogger securityEventLogger;
    private final DbErrorTranslator dbErrorTranslator;

    public GlobalExceptionHandler(SecurityEventLogger securityEventLogger, DbErrorTranslator dbErrorTranslator) {
        this.securityEventLogger = securityEventLogger;
        this.dbErrorTranslator = dbErrorTranslator;
    }

    // ---- Nghiệp vụ ----------------------------------------------------------------------

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex, HttpServletRequest req) {
        if (ex.auditReason() != null) {
            securityEventLogger.accessDenied(ex.auditReason(), ex.entityType(), ex.entityId(), describe(req));
        }
        return build(ex.code(), ex.getMessage());
    }

    // ---- 400 -----------------------------------------------------------------------------

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        if (ex.getCause() instanceof UnrecognizedPropertyException upe) {
            // Chống mass assignment (TH-03, SEC-TC-308): body có trường lạ như status, approvedBy, requesterId → 400
            return build(ErrorCode.UNKNOWN_FIELD, "Trường không được phép: " + upe.getPropertyName());
        }
        return build(ErrorCode.VALIDATION_ERROR, "Body không đúng định dạng JSON hoặc sai kiểu dữ liệu");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex) {
        List<ErrorResponse.FieldViolation> details = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> new ErrorResponse.FieldViolation(f.getField(), f.getDefaultMessage()))
                .toList();
        return build(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), details);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleParamValidation(HandlerMethodValidationException ex) {
        List<ErrorResponse.FieldViolation> details = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream().map(err -> new ErrorResponse.FieldViolation(
                        r.getMethodParameter().getParameterName(), err.getDefaultMessage())))
                .toList();
        return build(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException ex) {
        List<ErrorResponse.FieldViolation> details = ex.getConstraintViolations().stream()
                .map(v -> new ErrorResponse.FieldViolation(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return build(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), details);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<ErrorResponse> handleBadParam(Exception ex) {
        return build(ErrorCode.VALIDATION_ERROR, "Tham số request không hợp lệ");
    }

    // ---- 403 / 404 -----------------------------------------------------------------------

    /** 403 từ @PreAuthorize (lớp RBAC). */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        securityEventLogger.accessDenied(ApiException.AuditReason.FORBIDDEN_ROLE, null, null, describe(req));
        return build(ErrorCode.FORBIDDEN_ROLE, ErrorCode.FORBIDDEN_ROLE.defaultMessage());
    }

    @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<ErrorResponse> handleNoRoute(Exception ex) {
        return build(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.defaultMessage());
    }

    // ---- Database (trigger, CHECK, UNIQUE) -----------------------------------------------

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccess(DataAccessException ex, HttpServletRequest req) {
        DbErrorTranslator.Translated t = dbErrorTranslator.translate(ex);
        if (t.code() == ErrorCode.INTERNAL_ERROR) {
            log.error("Lỗi database không phân loại được tại {}", describe(req), ex);
        } else {
            log.warn("Database từ chối thao tác tại {}: {} ({})", describe(req), t.code(), t.sqlState());
        }
        if (t.auditReason() != null) {
            securityEventLogger.accessDenied(t.auditReason(), null, null, describe(req));
        }
        return build(t.code(), t.message());
    }

    // ---- 500 -----------------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Lỗi không mong đợi tại {}", describe(req), ex);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage());
    }

    // ---- helpers -------------------------------------------------------------------------

    private static String describe(HttpServletRequest req) {
        return req.getMethod() + " " + req.getRequestURI();
    }

    private static ResponseEntity<ErrorResponse> build(ErrorCode code, String message) {
        return build(code, message, List.of());
    }

    private static ResponseEntity<ErrorResponse> build(ErrorCode code, String message,
                                                       List<ErrorResponse.FieldViolation> details) {
        return ResponseEntity.status(code.status())
                .body(ErrorResponse.of(code, message, RequestContext.requestId(), details));
    }
}
