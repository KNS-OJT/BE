package com.p03.inventory.supplier;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    /**
     * Tìm theo tên / người liên hệ / email (không phân biệt hoa thường), lọc trạng thái hợp tác, phân trang (IN03).
     * @param pattern mẫu LIKE từ {@code Text.likePattern}
     * @param active  null = tất cả
     */
    @Query("""
            select s from Supplier s
            where (lower(s.name) like :pattern escape '\\'
                   or lower(s.contactPerson) like :pattern escape '\\'
                   or lower(s.email) like :pattern escape '\\')
              and (:active is null or s.active = :active)
            """)
    Page<Supplier> search(@Param("pattern") String pattern,
                          @Param("active") Boolean active,
                          Pageable pageable);
}
