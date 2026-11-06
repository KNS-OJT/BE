package com.p03.inventory.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** username UNIQUE không phân biệt hoa thường (index ux_users_username_ci). */
    @Query("select u from User u where lower(u.username) = lower(:username)")
    Optional<User> findByUsernameIgnoreCase(@Param("username") String username);

    @Query("select count(u) > 0 from User u where lower(u.username) = lower(:username)")
    boolean existsByUsernameIgnoreCase(@Param("username") String username);

    /** @param pattern chuỗi LIKE đã chuẩn hóa chữ thường, vd "%an%" ("%" = tất cả) */
    @Query("""
            select u from User u
            where lower(u.username) like :pattern escape '\\' or lower(u.fullName) like :pattern escape '\\'
            """)
    Page<User> search(@Param("pattern") String pattern, Pageable pageable);
}
