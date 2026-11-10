package com.p03.inventory.common.util;

import java.util.Locale;

/** Tiện ích chuỗi dùng chung cho tìm kiếm / chuẩn hóa dữ liệu nhập. */
public final class Text {

    private Text() {
    }

    /** null hoặc toàn khoảng trắng → null; còn lại → đã trim. */
    public static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * Mẫu LIKE không phân biệt hoa thường, đã escape ký tự đặc biệt (IN15). Dùng với
     * {@code lower(col) like :pattern escape '\'}; keyword rỗng → "%" (lấy tất cả).
     */
    public static String likePattern(String keyword) {
        String k = trimToNull(keyword);
        if (k == null) {
            return "%";
        }
        String escaped = k.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
