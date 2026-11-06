package com.p03.inventory.common.error;

/**
 * Exception nghiệp vụ duy nhất mà service nên ném ra. GlobalExceptionHandler đổi nó thành JSON lỗi chuẩn.
 * <p>
 * Nếu {@link #auditReason()} khác null, handler sẽ ghi thêm sự kiện ACCESS_DENIED vào audit log
 * (qua transaction riêng, không mất khi giao dịch nghiệp vụ rollback).
 *
 * <pre>
 * throw ApiException.notFound();                          // 404, không audit
 * throw ApiException.notOwner("PR", prId);                // 404 + audit ACCESS_DENIED reason NOT_OWNER (BR-20)
 * throw ApiException.sodViolation("PR", prId);            // 403 + audit reason SOD_VIOLATION (BR-08, BR-23, BR-24)
 * throw ApiException.invalidState("PR không ở trạng thái Submitted");
 * </pre>
 */
public class ApiException extends RuntimeException {

    /** Lý do ghi vào audit ACCESS_DENIED (SDD mục 7). */
    public enum AuditReason { FORBIDDEN_ROLE, SOD_VIOLATION, NOT_OWNER, INVALID_STATE }

    private final ErrorCode code;
    private final AuditReason auditReason;
    private final String entityType;
    private final Long entityId;

    public ApiException(ErrorCode code) {
        this(code, code.defaultMessage());
    }

    public ApiException(ErrorCode code, String message) {
        this(code, message, null, null, null);
    }

    private ApiException(ErrorCode code, String message, AuditReason auditReason, String entityType, Long entityId) {
        super(message != null ? message : code.defaultMessage());
        this.code = code;
        this.auditReason = auditReason;
        this.entityType = entityType;
        this.entityId = entityId;
    }

    // ---- Factory dùng chung -------------------------------------------------------------

    public static ApiException validation(String message) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, message);
    }

    public static ApiException notFound() {
        return new ApiException(ErrorCode.NOT_FOUND);
    }

    public static ApiException notFound(String message) {
        return new ApiException(ErrorCode.NOT_FOUND, message);
    }

    /** Đối tượng tồn tại nhưng không thuộc phạm vi người gọi: trả 404 (không lộ sự tồn tại) và ghi audit. */
    public static ApiException notOwner(String entityType, Long entityId) {
        return new ApiException(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.defaultMessage(),
                AuditReason.NOT_OWNER, entityType, entityId);
    }

    public static ApiException forbiddenRole(String entityType, Long entityId) {
        return new ApiException(ErrorCode.FORBIDDEN_ROLE, ErrorCode.FORBIDDEN_ROLE.defaultMessage(),
                AuditReason.FORBIDDEN_ROLE, entityType, entityId);
    }

    public static ApiException sodViolation(String entityType, Long entityId) {
        return new ApiException(ErrorCode.SOD_VIOLATION, ErrorCode.SOD_VIOLATION.defaultMessage(),
                AuditReason.SOD_VIOLATION, entityType, entityId);
    }

    public static ApiException invalidState(String message) {
        return new ApiException(ErrorCode.INVALID_STATE, message);
    }

    /** Sai trạng thái có ghi audit – dùng khi đây là dấu hiệu cố tình (vd. xác nhận lặp). */
    public static ApiException invalidStateAudited(String message, String entityType, Long entityId) {
        return new ApiException(ErrorCode.INVALID_STATE, message, AuditReason.INVALID_STATE, entityType, entityId);
    }

    public static ApiException duplicate(String message) {
        return new ApiException(ErrorCode.DUPLICATE, message);
    }

    public static ApiException insufficientStock() {
        return new ApiException(ErrorCode.INSUFFICIENT_STOCK);
    }

    public static ApiException overReceipt() {
        return new ApiException(ErrorCode.OVER_RECEIPT);
    }

    // ---- Getters -----------------------------------------------------------------------

    public ErrorCode code() {
        return code;
    }

    public AuditReason auditReason() {
        return auditReason;
    }

    public String entityType() {
        return entityType;
    }

    public Long entityId() {
        return entityId;
    }
}
