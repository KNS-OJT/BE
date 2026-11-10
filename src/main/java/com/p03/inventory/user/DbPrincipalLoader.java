package com.p03.inventory.user;

import com.p03.inventory.common.security.AppUserPrincipal;
import com.p03.inventory.common.security.PrincipalLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

/** Mỗi request có JWT: tải user, chỉ chấp nhận khi is_active và không bị khóa tạm (SDD mục 6, bước 4). */
@Component
public class DbPrincipalLoader implements PrincipalLoader {

    private final UserRepository users;
    private final Clock clock;

    public DbPrincipalLoader(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AppUserPrincipal> loadActive(Long userId) {
        return users.findById(userId)
                .filter(User::isActive)
                .filter(u -> !u.isLockedAt(clock.instant()))
                .map(u -> new AppUserPrincipal(u.getId(), u.getUsername(), u.getRole().getName()));
    }
}
