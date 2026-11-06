package com.p03.inventory.common.tx;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Ghi đè "người thực hiện" cho các tình huống không có JWT (vd. đăng nhập: cập nhật failed_login_count
 * của chính user đang đăng nhập). Nghiệp vụ bình thường KHÔNG dùng class này.
 *
 * <pre>
 * ActorContext.runAs(user.getId(), () -> userService.recordSuccessfulLogin(user.getId()));
 * </pre>
 * Transaction phải được MỞ BÊN TRONG runAs thì set_config mới lấy được giá trị này.
 */
public final class ActorContext {

    private static final ThreadLocal<Long> ACTOR = new ThreadLocal<>();

    private ActorContext() {
    }

    public static Optional<Long> current() {
        return Optional.ofNullable(ACTOR.get());
    }

    public static <T> T runAs(Long userId, Supplier<T> action) {
        Long previous = ACTOR.get();
        ACTOR.set(userId);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                ACTOR.remove();
            } else {
                ACTOR.set(previous);
            }
        }
    }

    public static void runAs(Long userId, Runnable action) {
        runAs(userId, () -> {
            action.run();
            return null;
        });
    }
}
