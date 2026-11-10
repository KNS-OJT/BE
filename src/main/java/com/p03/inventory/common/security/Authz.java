package com.p03.inventory.common.security;

/**
 * Biểu thức phân quyền dùng chung cho {@code @PreAuthorize} (lớp 1 – RBAC, SDD mục 6).
 * Dùng hằng số này thay vì gõ chuỗi tay để tránh sai chính tả tên vai trò.
 *
 * <pre>
 * &#64;PreAuthorize(Authz.MANAGER)
 * &#64;PostMapping("/{id}/approve")
 * public PrResponse approve(@PathVariable Long id) { ... }
 * </pre>
 *
 * Quy tắc: MỌI handler trong /api/** phải có {@code @PreAuthorize} hoặc {@code @PermitAll}
 * (ở method hoặc class). Thiếu → ứng dụng không khởi động được (xem EndpointSecurityVerifier).
 * Lớp 1 chỉ kiểm vai trò; kiểm chủ sở hữu, trạng thái, SoD làm trong service (lớp 2, 3).
 */
public final class Authz {

    private Authz() {
    }

    /** Mọi vai trò đã đăng nhập (vd. tạo PR, tạo yêu cầu xuất, tìm sản phẩm). */
    public static final String AUTHENTICATED = "isAuthenticated()";

    public static final String MANAGER = "hasRole('MANAGER')";
    public static final String PURCHASING = "hasRole('PURCHASING_STAFF')";
    public static final String WAREHOUSE = "hasRole('WAREHOUSE_STAFF')";
    public static final String REQUESTER = "hasRole('REQUESTER')";

    public static final String WAREHOUSE_OR_MANAGER = "hasAnyRole('WAREHOUSE_STAFF','MANAGER')";
    public static final String PURCHASING_OR_MANAGER = "hasAnyRole('PURCHASING_STAFF','MANAGER')";
    public static final String WAREHOUSE_PURCHASING_MANAGER = "hasAnyRole('WAREHOUSE_STAFF','PURCHASING_STAFF','MANAGER')";
}
