package com.p03.inventory.auth;

import com.p03.inventory.common.security.LoginPolicyProperties;
import com.p03.inventory.common.tx.ActorContext;
import com.p03.inventory.user.User;
import com.p03.inventory.user.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;

/**
 * Cập nhật failed_login_count / locked_until / last_login_at. Mỗi lần ghi là MỘT transaction riêng (REQUIRES_NEW),
 * để số lần sai vẫn được lưu dù sau đó AuthService ném 401. Database cho phép p03_app ghi các cột này mà không cần
 * actor (01_schema.sql, trg_users_manager_only); ta vẫn đặt actor = chính user để audit_logs ghi đúng người.
 * <p>
 * Dùng TransactionTemplate thay vì @Transactional vì transaction phải mở BÊN TRONG ActorContext.runAs.
 */
@Component
class LoginBookkeeping {

    record FailureResult(boolean lockedNow) {
    }

    private final UserRepository users;
    private final LoginPolicyProperties policy;
    private final Clock clock;
    private final TransactionTemplate tx;

    LoginBookkeeping(UserRepository users, LoginPolicyProperties policy, Clock clock,
                     PlatformTransactionManager transactionManager) {
        this.users = users;
        this.policy = policy;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    FailureResult recordFailure(Long userId) {
        return ActorContext.runAs(userId, () -> tx.execute(status -> {
            User u = users.findById(userId).orElseThrow();
            int failed = u.getFailedLoginCount() + 1;
            if (failed >= policy.maxFailedLogins()) {
                u.setFailedLoginCount((short) 0);
                u.setLockedUntil(Instant.now(clock).plus(policy.lockDuration()));
                return new FailureResult(true);
            }
            u.setFailedLoginCount((short) failed);
            return new FailureResult(false);
        }));
    }

    void recordSuccess(Long userId) {
        ActorContext.runAs(userId, () -> tx.executeWithoutResult(status -> {
            User u = users.findById(userId).orElseThrow();
            u.setFailedLoginCount((short) 0);
            u.setLockedUntil(null);
            u.setLastLoginAt(Instant.now(clock));
        }));
    }
}
