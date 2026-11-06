package com.p03.inventory.user.dto;

import com.p03.inventory.common.security.Role;
import com.p03.inventory.user.User;

import java.time.Instant;

/** Không bao giờ có password_hash (SRS mục 7). */
public record UserResponse(Long id, String username, String fullName, String email, Role role, boolean active,
                           Instant lockedUntil, Instant lastLoginAt, Instant createdAt) {

    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getUsername(), u.getFullName(), u.getEmail(), u.getRole().getName(),
                u.isActive(), u.getLockedUntil(), u.getLastLoginAt(), u.getCreatedAt());
    }
}
