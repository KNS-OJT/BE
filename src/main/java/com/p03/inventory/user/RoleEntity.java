package com.p03.inventory.user;

import com.p03.inventory.common.security.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Bảng roles: 4 dòng cố định, chỉ đọc. */
@Entity
@Table(name = "roles")
@Immutable
@Getter
public class RoleEntity {

    @Id
    @Column(name = "role_id")
    private Short id;

    /** Cột kiểu ENUM user_role của PostgreSQL. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "role_name", nullable = false, columnDefinition = "user_role")
    private Role name;
}
