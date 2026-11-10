package com.p03.inventory.common.error;

import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Đổi lỗi do database chặn (trigger, CHECK, UNIQUE, quyền role) thành mã lỗi API – lớp 4 của mô hình phân quyền
 * (SDD mục 6). Bám đúng quy ước của 01_schema.sql (README mục "Rules for the backend", điểm 6):
 * <ul>
 *   <li>{@code 42501} → 403 (sai quyền, giả mạo người thực hiện, SoD)</li>
 *   <li>{@code P0409} → 409 (sai trạng thái, không đủ tồn, nhận vượt)</li>
 *   <li>{@code 23505} → 409 DUPLICATE, {@code 23514} → 400 (CHECK; riêng ck_*_no_self_* là SoD → 403)</li>
 * </ul>
 * Thông báo của trigger bắt đầu bằng mã quy tắc (vd. {@code "BR-05/IA-IN04: insufficient stock..."}). Ta chỉ lấy
 * mã quy tắc để gắn vào thông báo tiếng Việt; KHÔNG đưa nguyên văn thông báo PostgreSQL ra client.
 */
@Component
public class DbErrorTranslator {

    private static final Pattern RULE_PREFIX = Pattern.compile("^([A-Z]{2}[A-Z0-9/-]*\\d{2}):");
    private static final Pattern CONSTRAINT = Pattern.compile("constraint \"([^\"]+)\"");

    /** Thông báo thân thiện cho các ràng buộc UNIQUE hay gặp. */
    private static final Map<String, String> UNIQUE_MESSAGES = Map.of(
            "ux_products_sku", "SKU đã tồn tại (kể cả sản phẩm đã ngừng hoạt động)",
            "ux_users_username_ci", "Tên đăng nhập đã tồn tại",
            "ux_categories_name_ci", "Tên nhóm hàng đã tồn tại",
            "purchase_orders_purchase_request_id_key", "Yêu cầu mua hàng này đã được lập PO");

    public record Translated(ErrorCode code, String message, String sqlState, ApiException.AuditReason auditReason) {
    }

    public Translated translate(Throwable ex) {
        SQLException sql = findSqlException(ex);
        if (sql == null) {
            return of(ErrorCode.INTERNAL_ERROR, null, null);
        }
        String state = sql.getSQLState() == null ? "" : sql.getSQLState();
        String raw = firstLine(stripErrorPrefix(sql.getMessage()));
        String rule = ruleId(raw);
        String constraint = constraintName(raw);

        return switch (state) {
            case "P0409" -> fromConflict(rule, state);
            case "42501" -> fromForbidden(rule, state);
            case "23505" -> new Translated(ErrorCode.DUPLICATE,
                    UNIQUE_MESSAGES.getOrDefault(constraint, ErrorCode.DUPLICATE.defaultMessage()), state, null);
            case "23514" -> constraint != null && constraint.contains("no_self")
                    ? new Translated(ErrorCode.SOD_VIOLATION, ErrorCode.SOD_VIOLATION.defaultMessage(), state,
                    ApiException.AuditReason.SOD_VIOLATION)
                    : of(ErrorCode.VALIDATION_ERROR, rule, state);
            case "23503" -> new Translated(ErrorCode.VALIDATION_ERROR, "Dữ liệu tham chiếu không tồn tại", state, null);
            case "23502" -> of(ErrorCode.VALIDATION_ERROR, rule, state);
            case "40001", "40P01", "55P03", "57014" -> new Translated(ErrorCode.INVALID_STATE,
                    "Dữ liệu đang được người khác xử lý, vui lòng thử lại", state, null);
            default -> of(ErrorCode.INTERNAL_ERROR, null, state);
        };
    }

    /** P0409: phân loại theo mã quy tắc ở đầu thông báo. */
    private Translated fromConflict(String rule, String state) {
        if (rule != null && rule.contains("BR-05")) {
            return of(ErrorCode.INSUFFICIENT_STOCK, rule, state);
        }
        if (rule != null && (rule.contains("BR-06") || rule.startsWith("BR-21"))) {
            return of(ErrorCode.OVER_RECEIPT, rule, state);
        }
        return of(ErrorCode.INVALID_STATE, rule, state);
    }

    /** 42501: SoD (BR-08, BR-23, BR-24, IA-IN01) hoặc sai vai trò / giả mạo người thực hiện. */
    private Translated fromForbidden(String rule, String state) {
        if (rule != null && (rule.contains("BR-08") || rule.contains("BR-23") || rule.contains("BR-24")
                || rule.contains("IA-IN01"))) {
            return new Translated(ErrorCode.SOD_VIOLATION, withRule(ErrorCode.SOD_VIOLATION.defaultMessage(), rule),
                    state, ApiException.AuditReason.SOD_VIOLATION);
        }
        return new Translated(ErrorCode.FORBIDDEN_ROLE, withRule(ErrorCode.FORBIDDEN_ROLE.defaultMessage(), rule),
                state, ApiException.AuditReason.FORBIDDEN_ROLE);
    }

    private static Translated of(ErrorCode code, String rule, String state) {
        return new Translated(code, withRule(code.defaultMessage(), rule), state, null);
    }

    private static String withRule(String message, String rule) {
        return rule == null ? message : message + " (" + rule + ")";
    }

    static String ruleId(String message) {
        if (message == null) {
            return null;
        }
        Matcher m = RULE_PREFIX.matcher(message.trim());
        return m.find() ? m.group(1) : null;
    }

    private static String constraintName(String message) {
        if (message == null) {
            return null;
        }
        Matcher m = CONSTRAINT.matcher(message);
        return m.find() ? m.group(1) : null;
    }

    private static String stripErrorPrefix(String s) {
        if (s == null) {
            return null;
        }
        return s.startsWith("ERROR: ") ? s.substring(7) : s;
    }

    private static String firstLine(String s) {
        if (s == null) {
            return null;
        }
        int nl = s.indexOf('\n');
        return nl < 0 ? s : s.substring(0, nl);
    }

    private static SQLException findSqlException(Throwable ex) {
        Throwable t = ex;
        SQLException found = null;
        for (int depth = 0; t != null && depth < 15; depth++, t = t.getCause()) {
            if (t instanceof SQLException s) {
                found = s; // lấy SQLException sâu nhất (gốc, có SQLState của PostgreSQL)
            }
        }
        return found;
    }
}
