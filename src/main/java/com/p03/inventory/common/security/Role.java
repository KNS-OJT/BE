package com.p03.inventory.common.security;

/**
 * Đúng 4 vai trò của SRS v1.1 mục 2 (khớp giá trị cột roles.role_name). Không thêm vai trò mới.
 */
public enum Role {
    REQUESTER,
    WAREHOUSE_STAFF,
    PURCHASING_STAFF,
    MANAGER;

    /** Authority theo quy ước Spring Security: "ROLE_" + tên. */
    public String authority() {
        return "ROLE_" + name();
    }
}
