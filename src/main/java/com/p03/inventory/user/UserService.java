package com.p03.inventory.user;

import com.p03.inventory.common.error.ApiException;
import com.p03.inventory.common.paging.PageResponse;
import com.p03.inventory.common.security.CurrentUser;
import com.p03.inventory.common.security.Role;
import com.p03.inventory.common.util.Text;
import com.p03.inventory.user.dto.UserCreateRequest;
import com.p03.inventory.user.dto.UserResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quản lý tài khoản (IN01, ADR-06): chỉ Manager; không xóa, chỉ khóa (is_active = false).
 * Database tự kiểm tra lại người thực hiện là Manager (trg_users_manager_only) và tự ghi audit
 * INSERT / UPDATE trên bảng users (password_hash bị che).
 */
@Service
public class UserService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(String q, Pageable pageable) {
        return PageResponse.of(users.search(Text.likePattern(q), pageable), UserResponse::from);
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return UserResponse.from(load(id));
    }

    @Transactional
    public UserResponse create(UserCreateRequest req) {
        String username = req.username().trim();
        if (users.existsByUsernameIgnoreCase(username)) {
            throw ApiException.duplicate("Tên đăng nhập đã tồn tại");
        }
        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        u.setFullName(req.fullName().trim());
        u.setEmail(Text.trimToNull(req.email()));
        u.setRole(role(req.role()));
        u.setActive(true);
        return UserResponse.from(users.saveAndFlush(u));
    }

    @Transactional
    public void lock(Long id) {
        User u = load(id);
        if (u.getId().equals(CurrentUser.id())) {
            throw ApiException.invalidState("Không thể tự khóa tài khoản của chính mình");
        }
        u.setActive(false);
    }

    @Transactional
    public UserResponse changeRole(Long id, Role newRole) {
        User u = load(id);
        if (u.getId().equals(CurrentUser.id())) {
            throw ApiException.invalidState("Không thể tự đổi vai trò của chính mình");
        }
        u.setRole(role(newRole));
        return UserResponse.from(users.saveAndFlush(u));
    }

    private User load(Long id) {
        return users.findById(id).orElseThrow(ApiException::notFound);
    }

    private RoleEntity role(Role r) {
        return roles.findByName(r).orElseThrow(() -> new IllegalStateException("Thiếu dòng roles cho " + r));
    }
}
