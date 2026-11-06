package com.p03.inventory.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** username UNIQUE không phân biệt hoa thường (index lower(username)). */
    @Query("select u from User u where lower(u.username) = lower(:username)")
    Optional<User> findByUsernameIgnoreCase(@Param("username") String username);

    @Query("select count(u) > 0 from User u where lower(u.username) = lower(:username)")
    boolean existsByUsernameIgnoreCase(@Param("username") String username);

    @Query("""
            select u from User u
            where (:q is null or lower(u.username) like lower(concat('%', :q, '%'))
                              or lower(u.fullName) like lower(concat('%', :q, '%')))
            """)
    Page<User> search(@Param("q") String q, Pageable pageable);
}
