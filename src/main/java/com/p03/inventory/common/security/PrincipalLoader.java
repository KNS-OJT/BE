package com.p03.inventory.common.security;

import java.util.Optional;

/**
 * Tải người dùng từ database cho mỗi request có JWT (SDD mục 6, bước 4): chỉ trả về khi tài khoản
 * đang hoạt động (is_active = true) và không bị khóa tạm (locked_until null hoặc đã qua).
 * Module user cài đặt interface này; tầng security không phụ thuộc trực tiếp entity User.
 */
public interface PrincipalLoader {

    Optional<AppUserPrincipal> loadActive(Long userId);
}
