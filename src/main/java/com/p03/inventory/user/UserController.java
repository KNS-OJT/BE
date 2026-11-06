package com.p03.inventory.user;

import com.p03.inventory.common.paging.PageResponse;
import com.p03.inventory.common.paging.Paging;
import com.p03.inventory.common.security.Authz;
import com.p03.inventory.user.dto.UserCreateRequest;
import com.p03.inventory.user.dto.UserResponse;
import com.p03.inventory.user.dto.UserRoleRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** SCR-50 – chỉ MANAGER (IN01). */
@RestController
@RequestMapping("/api/users")
@PreAuthorize(Authz.MANAGER)
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public PageResponse<UserResponse> list(@RequestParam(required = false) String q,
                                           @RequestParam(required = false) Integer page,
                                           @RequestParam(required = false) Integer size) {
        return userService.search(q, Paging.of(page, size, Sort.by("username")));
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable Long id) {
        return userService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody UserCreateRequest request) {
        return userService.create(request);
    }

    @PostMapping("/{id}/lock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void lock(@PathVariable Long id) {
        userService.lock(id);
    }

    @PutMapping("/{id}/role")
    public UserResponse changeRole(@PathVariable Long id, @Valid @RequestBody UserRoleRequest request) {
        return userService.changeRole(id, request.role());
    }
}
