package com.p03.inventory.common.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/**
 * Người dùng đã xác thực của request hiện tại. Vai trò lấy từ DATABASE ở mỗi request (không tin claim role
 * trong JWT), nên đổi vai trò / khóa tài khoản có hiệu lực ngay (ADR-04).
 */
public record AppUserPrincipal(Long id, String username, Role role) {

    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    public boolean is(Role r) {
        return role == r;
    }
}
