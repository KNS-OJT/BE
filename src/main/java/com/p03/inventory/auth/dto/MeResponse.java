package com.p03.inventory.auth.dto;

import com.p03.inventory.common.security.Role;

/** Thông tin người dùng hiện tại – FE dùng để dựng menu theo vai trò sau khi đăng nhập. */
public record MeResponse(Long userId, String username, String fullName, String email, Role role) {
}
