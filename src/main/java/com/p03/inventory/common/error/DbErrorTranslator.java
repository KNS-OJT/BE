package com.p03.inventory.common.error;

import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Đổi lỗi do database chặn (trigger, CHECK, UNIQUE, quyền role) thành mã lỗi API – lớp 4 của mô hình phân quyền
 * (SDD mục 6). Không bao giờ đưa nguyên văn thông báo của PostgreSQL ra client.
 * <p>
 * Quy ước với trigger: {@code RAISE EXCEPTION 'INSUFFICIENT_STOCK: Số lượng xuất vượt quá tồn kho khả dụng'}
 * – phần trước dấu ":" là tên một {@link ErrorCode}, phần sau là thông báo tiếng Việt cho người dùng.
 * Trigger không theo quy ước → 409 INVALID_STATE với thông báo mặc định.
 */
@Component
public class DbErrorTranslator {

    private static final Pattern CODED_MESSAGE = Pattern.compile("^([A-Z_]{3,40}):\\s*(.{1,300})$", Pattern.DOTALL);

    public record Translated(ErrorCode code, String message, String sqlState,
                             ApiException.AuditReason auditReason) {
    }

    public Translated translate(Throwable ex) {
        SQLException sql = findSqlException(ex);
        if (sql == null) {
            return of(ErrorCode.INTERNAL_ERROR, null);
        }
        String state = sql.getSQLState() == null ? "" : sql.getSQLState();
        return switch (state) {
            case "23505" -> of(ErrorCode.DUPLICATE, state);                       // unique_violation
            case "23503" -> new Translated(ErrorCode.VALIDATION_ERROR,            // foreign_key_violation
                    "Dữ liệu tham chiếu không tồn tại", state, null);
            case "23514", "23502" -> of(ErrorCode.VALIDATION_ERROR, state);       // check / not_null violation
            case "40001", "40P01", "55P03" -> new Translated(ErrorCode.INVALID_STATE, // serialization, deadlock, lock_not_available
                    "Dữ liệu đang được người khác xử lý, vui lòng thử lại", state, null);
            case "42501" -> new Translated(ErrorCode.FORBIDDEN_ROLE,              // insufficient_privilege (role DB chặn)
                    ErrorCode.FORBIDDEN_ROLE.defaultMessage(), state,
                    ApiException.AuditReason.FORBIDDEN_ROLE);
            case "P0001" -> fromRaise(firstLine(sql.getMessage()), state);        // RAISE EXCEPTION trong trigger
            default -> of(ErrorCode.INTERNAL_ERROR, state);
        };
    }

    private Translated fromRaise(String message, String state) {
        if (message != null) {
            // PostgreSQL JDBC thêm tiền tố "ERROR: " vào message
            String m = message.startsWith("ERROR: ") ? message.substring(7) : message;
            Matcher matcher = CODED_MESSAGE.matcher(m.trim());
            if (matcher.matches()) {
                try {
                    ErrorCode code = ErrorCode.valueOf(matcher.group(1));
                    var audit = code == ErrorCode.SOD_VIOLATION
                            ? ApiException.AuditReason.SOD_VIOLATION : null;
                    return new Translated(code, matcher.group(2).trim(), state, audit);
                } catch (IllegalArgumentException ignored) {
                    // tiền tố không phải ErrorCode → rơi xuống mặc định
                }
            }
        }
        return of(ErrorCode.INVALID_STATE, state);
    }

    private static Translated of(ErrorCode code, String state) {
        return new Translated(code, code.defaultMessage(), state, null);
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
