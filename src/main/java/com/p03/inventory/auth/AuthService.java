package com.p03.inventory.auth;

import com.p03.inventory.auth.dto.LoginRequest;
import com.p03.inventory.auth.dto.LoginResponse;
import com.p03.inventory.auth.dto.MeResponse;
import com.p03.inventory.common.audit.SecurityEventLogger;
import com.p03.inventory.common.error.ApiException;
import com.p03.inventory.common.error.ErrorCode;
import com.p03.inventory.common.security.CurrentUser;
import com.p03.inventory.common.security.jwt.JwtService;
import com.p03.inventory.user.User;
import com.p03.inventory.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

/**
 * Đăng nhập (IN01, SDD mục 6, IA-NF01):
 * <ul>
 *   <li>So khớp bcrypt; sai → tăng failed_login_count; sai đủ 5 lần → khóa 15 phút (locked_until).</li>
 *   <li>Mọi trường hợp lỗi (sai username, sai mật khẩu, tài khoản khóa / vô hiệu hóa) trả CÙNG một 401
 *       INVALID_CREDENTIALS, để không dò được username nào tồn tại.</li>
 *   <li>Ghi audit LOGIN_SUCCESS / LOGIN_FAILED / ACCOUNT_LOCKED qua fn_log_security_event.</li>
 * </ul>
 * Không đánh dấu @Transactional: mỗi lần ghi đã có transaction riêng (LoginBookkeeping, SecurityEventLogger),
 * nên ném 401 xong vẫn còn số lần sai và log.
 */
@Service
public class AuthService {

    /** Hash bcrypt giả để so khớp khi username không tồn tại → thời gian phản hồi như nhau. */
    private static final String DUMMY_HASH = "$2a$12$W6ZjZD5.IJunD3qoIkHcr.NTctlw2dG1tt.8yuuIM2O126yhQQowW";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginBookkeeping bookkeeping;
    private final SecurityEventLogger securityLog;
    private final Clock clock;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                       LoginBookkeeping bookkeeping, SecurityEventLogger securityLog, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.bookkeeping = bookkeeping;
        this.securityLog = securityLog;
        this.clock = clock;
    }

    public LoginResponse login(LoginRequest req) {
        Optional<User> found = users.findByUsernameIgnoreCase(req.username().trim());
        if (found.isEmpty()) {
            passwordEncoder.matches(req.password(), DUMMY_HASH);
            securityLog.loginFailed(null, "UNKNOWN_USERNAME");
            throw invalidCredentials();
        }
        User user = found.get();

        if (!user.isActive()) {
            passwordEncoder.matches(req.password(), DUMMY_HASH);
            securityLog.loginFailed(user.getId(), "ACCOUNT_INACTIVE");
            throw invalidCredentials();
        }
        if (user.isLockedAt(clock.instant())) {
            passwordEncoder.matches(req.password(), DUMMY_HASH);
            securityLog.loginFailed(user.getId(), "ACCOUNT_LOCKED");
            throw invalidCredentials();
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            LoginBookkeeping.FailureResult r = bookkeeping.recordFailure(user.getId());
            securityLog.loginFailed(user.getId(), "BAD_PASSWORD");
            if (r.lockedNow()) {
                securityLog.accountLocked(user.getId());
            }
            throw invalidCredentials();
        }

        bookkeeping.recordSuccess(user.getId());
        securityLog.loginSucceeded(user.getId());
        JwtService.IssuedToken token = jwtService.issue(user.getId(), user.getRole().getName());
        return new LoginResponse(token.accessToken(), "Bearer", token.expiresIn(), user.getRole().getName(),
                user.getId(), user.getUsername(), user.getFullName());
    }

    @Transactional(readOnly = true)
    public MeResponse me() {
        User u = users.findById(CurrentUser.id()).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        return new MeResponse(u.getId(), u.getUsername(), u.getFullName(), u.getEmail(), u.getRole().getName());
    }

    private static ApiException invalidCredentials() {
        return new ApiException(ErrorCode.INVALID_CREDENTIALS);
    }
}
