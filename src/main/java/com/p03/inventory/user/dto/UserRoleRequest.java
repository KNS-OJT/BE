package com.p03.inventory.user.dto;

import com.p03.inventory.common.security.Role;
import jakarta.validation.constraints.NotNull;

public record UserRoleRequest(@NotNull Role role) {
}
