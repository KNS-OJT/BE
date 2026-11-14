 package com.p03.inventory.common.audit;

import com.p03.inventory.common.error.ApiException;
import com.p03.inventory.common.security.CurrentUser;
import com.p03.inventory.common.web.RequestContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.query.NativeQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ghi sự kiện bảo mật vào audit_logs qua hàm {@code fn_log_security_event} (TX-07, IA-IN05, SEC-TC-302).
 * <p>
 * Chạy trong TRANSACTION RIÊNG (REQUIRES_NEW, connection riêng) nên vẫn được commit khi giao dịch nghiệp vụ
 * bị rollback. Lỗi khi ghi log chỉ ghi ra app log, không bao giờ làm hỏng response.
 * <p>
 * Các module KHÔNG cần gọi class này cho ACCESS_DENIED: chỉ cần ném {@code ApiException.notOwner / sodViolation /
 * forbiddenRole}; GlobalExceptionHandler tự ghi. Gọi trực tiếp chỉ dùng cho sự kiện đăng nhập.
 */
@Component
public class SecurityEventLogger {

    private static final Logger log = LoggerFactory.getLogger("security.audit");

    /** fn_log_security_event(p_action, p_result, p_actor_id, p_entity_type, p_entity_id, p_reason, p_details) */
    private static final String SQL = """
            SELECT fn_log_security_event(CAST(?1 AS varchar), CAST(?2 AS varchar), CAST(?3 AS bigint),
                                         CAST(?4 AS varchar), CAST(?5 AS bigint), CAST(?6 AS text), CAST(?7 AS jsonb))
            """;

    @PersistenceContext
    private EntityManager em;

    private final TransactionTemplate requiresNew;

    public SecurityEventLogger(PlatformTransactionManager transactionManager) {
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void accessDenied(ApiException.AuditReason reason, String entityType, Long entityId, String target) {
        persist("ACCESS_DENIED", "DENIED", CurrentUser.idOrNull(),
                entityType == null ? "security" : entityType, entityId, reason.name(), target);
    }

    public void loginSucceeded(Long userId) {
        persist("LOGIN_SUCCESS", "SUCCESS", userId, "users", userId, null, null);
    }

    /** userId null khi username không tồn tại (không lộ username đã thử ra ngoài, chỉ ghi vào audit). */
    public void loginFailed(Long userIdOrNull, String reason) {
        persist("LOGIN_FAILED", "FAILED", userIdOrNull, "users", userIdOrNull, reason, null);
    }

    public void accountLocked(Long userId) {
        persist("ACCOUNT_LOCKED", "SUCCESS", userId, "users", userId, "MAX_FAILED_LOGINS", null);
    }

    private void persist(String action, String result, Long actorId, String entityType, Long entityId,
                         String reason, String target) {
        try {
            String details = buildDetails(target);
            // Khai báo kiểu cho từng tham số để Hibernate bind được giá trị null (actor/entity có thể rỗng)
            requiresNew.executeWithoutResult(status -> em.createNativeQuery(SQL)
                    .unwrap(NativeQuery.class)
                    .setParameter(1, action, String.class)
                    .setParameter(2, result, String.class)
                    .setParameter(3, actorId, Long.class)
                    .setParameter(4, entityType, String.class)
                    .setParameter(5, entityId, Long.class)
                    .setParameter(6, reason, String.class)
                    .setParameter(7, details, String.class)
                    .getSingleResult());
        } catch (RuntimeException e) {
            // Không được làm hỏng response; vẫn để lại dấu vết trong app log cho IA đối chiếu
            log.error("Không ghi được audit {} {} actor={} entity={}#{} reason={}: {}",
                    action, result, actorId, entityType, entityId, reason, e.getMessage());
        }
    }

    /** changed_data dạng JSON: target (method + path) và request_id để nối với app log. */
    private static String buildDetails(String target) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"request_id\":").append(jsonString(RequestContext.requestId()));
        if (target != null) {
            sb.append(",\"target\":").append(jsonString(target));
        }
        return sb.append('}').toString();
    }

    private static String jsonString(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
