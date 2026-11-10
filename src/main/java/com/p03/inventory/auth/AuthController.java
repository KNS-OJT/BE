package com.p03.inventory.auth;

import com.p03.inventory.auth.dto.LoginRequest;
import com.p03.inventory.auth.dto.LoginResponse;
import com.p03.inventory.auth.dto.MeResponse;
import com.p03.inventory.common.security.Authz;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** POST /api/auth/login – public. Nginx giới hạn 5 request/phút/IP (IA-NF05). */
    @PermitAll
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** Người dùng hiện tại theo JWT. Đăng xuất = FE xóa token (MVP không có refresh token, ADR-04). */
    @PreAuthorize(Authz.AUTHENTICATED)
    @GetMapping("/me")
    public MeResponse me() {
        return authService.me();
    }
}
