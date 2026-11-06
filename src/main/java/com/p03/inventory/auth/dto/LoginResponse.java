package com.p03.inventory.auth.dto;

import com.p03.inventory.common.security.Role;

/** SDD mục 4: {accessToken, expiresIn, role}. expiresIn tính bằng giây. */
public record LoginResponse(String accessToken, String tokenType, long expiresIn, Role role,
                            Long userId, String username, String fullName) {
}
