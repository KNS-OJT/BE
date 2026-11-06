package com.p03.inventory.user.dto;

import com.p03.inventory.common.security.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * IN01 / SCR-50: Manager tạo tài khoản và gán 1 trong 4 vai trò.
 * password là mật khẩu tạm ban đầu (≥ 8 ký tự, SDD mục 6), chỉ lưu dạng bcrypt.
 */
public record UserCreateRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9._-]{3,100}$",
                message = "username 3–100 ký tự, chỉ gồm chữ, số, dấu . _ -") String username,
        @NotBlank @Size(min = 8, max = 72, message = "mật khẩu 8–72 ký tự") String password,
        @NotBlank @Size(max = 150) String fullName,
        @Email @Size(max = 254) String email,
        @NotNull Role role
) {
    @Override
    public String toString() {
        return "UserCreateRequest[username=" + username + ", password=***, fullName=" + fullName
                + ", email=" + email + ", role=" + role + "]";
    }
}
