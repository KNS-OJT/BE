package com.p03.inventory.common.security;

import com.p03.inventory.common.error.ApiException;
import com.p03.inventory.common.error.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Lấy người dùng hiện tại từ JWT đã xác thực. Đây là NGUỒN DUY NHẤT để biết "ai đang thao tác"
 * (ADR-05): không bao giờ lấy requesterId / approvedBy / createdBy từ body client gửi lên.
 *
 * <pre>
 * Long me = CurrentUser.id();
 * if (pr.getRequesterId().equals(me)) throw ApiException.sodViolation("PR", pr.getId());
 * if (CurrentUser.is(Role.REQUESTER) &amp;&amp; !pr.getRequesterId().equals(me)) throw ApiException.notOwner("PR", id);
 * </pre>
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<AppUserPrincipal> get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserPrincipal p) {
            return Optional.of(p);
        }
        return Optional.empty();
    }

    /** Người dùng hiện tại; ném 401 nếu chưa đăng nhập (không xảy ra với endpoint đã có @PreAuthorize). */
    public static AppUserPrincipal require() {
        return get().orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
    }

    public static Long id() {
        return require().id();
    }

    public static Role role() {
        return require().role();
    }

    public static boolean is(Role role) {
        return get().map(p -> p.role() == role).orElse(false);
    }

    /** Id người dùng hoặc null – dùng cho hạ tầng (transaction manager, audit), không dùng trong nghiệp vụ. */
    public static Long idOrNull() {
        return get().map(AppUserPrincipal::id).orElse(null);
    }
}
