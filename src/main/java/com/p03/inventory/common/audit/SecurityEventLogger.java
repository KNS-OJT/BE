package com.p03.inventory.common.audit;

import com.p03.inventory.common.error.ApiException;
import com.p03.inventory.common.security.CurrentUser;
import com.p03.inventory.common.web.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Ghi sự kiện bảo mật (ACCESS_DENIED, LOGIN_FAILED, ACCOUNT_LOCKED…) – TX-07, IA-IN05.
 * <p>
 * Thiết kế: ghi vào audit_logs qua hàm database fn_log_security_event trong TRANSACTION RIÊNG, để log không mất
 * khi giao dịch nghiệp vụ bị rollback; lỗi khi ghi log không bao giờ làm hỏng response.
 * <p>
 * TRẠNG THÁI: đang ghi ra app log (JSON, có request_id). Phần gọi fn_log_security_event sẽ nối vào
 * {@link #persist} khi có 01_schema.sql (cần đúng chữ ký hàm). Các module KHÔNG gọi trực tiếp class này cho
 * ACCESS_DENIED – chỉ cần ném ApiException.notOwner / sodViolation / forbiddenRole, handler tự ghi.
 */
@Component
public class SecurityEventLogger {

    private static final Logger SECURITY_LOG = LoggerFactory.getLogger("security.audit");

    public void accessDenied(ApiException.AuditReason reason, String entityType, Long entityId, String target) {
        persist("ACCESS_DENIED", "DENIED", CurrentUser.idOrNull(), entityType, entityId, reason.name(), target);
    }

    public void loginFailed(Long userIdOrNull, String reason) {
        persist("LOGIN_FAILED", "FAILED", userIdOrNull, "USER", userIdOrNull, reason, null);
    }

    public void accountLocked(Long userId) {
        persist("ACCOUNT_LOCKED", "SUCCESS", userId, "USER", userId, "MAX_FAILED_LOGINS", null);
    }

    public void loginSucceeded(Long userId) {
        persist("LOGIN_SUCCESS", "SUCCESS", userId, "USER", userId, null, null);
    }

    private void persist(String action, String result, Long actorId, String entityType, Long entityId,
                         String reason, String target) {
        try {
            SECURITY_LOG.warn("action={} result={} actor={} entity={}#{} reason={} target={} ip={} request_id={}",
                    action, result, actorId, entityType, entityId, reason, target,
                    RequestContext.clientIp(), RequestContext.requestId());
        } catch (RuntimeException e) {
            // Ghi log thất bại không được làm hỏng response
        }
    }
}
