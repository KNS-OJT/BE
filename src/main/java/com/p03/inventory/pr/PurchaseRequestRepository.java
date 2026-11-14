package com.p03.inventory.pr;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PurchaseRequestRepository extends JpaRepository<PurchaseRequest, Long> {

    /**
     * Danh sách PR của một requester, lọc theo trạng thái và từ khoá (IN07).
     * Requester chỉ thấy PR của chính mình (BR-20).
     */
    @EntityGraph(attributePaths = "requester")
    @Query("""
            select pr from PurchaseRequest pr
            where (:requesterId is null or pr.requester.id = :requesterId)
              and pr.status = coalesce(:status, pr.status)
              and (:pattern is null or lower(pr.requestCode) like :pattern escape '\\' or lower(pr.reason) like :pattern escape '\\')
            """)
    Page<PurchaseRequest> search(@Param("requesterId") Long requesterId,
                                 @Param("status") PrStatus status,
                                 @Param("pattern") String pattern,
                                 Pageable pageable);

    /** Fetch PR kèm requester (dùng cho chi tiết, tránh N+1). */
    @EntityGraph(attributePaths = {"requester", "reviewer"})
    @Query("select pr from PurchaseRequest pr where pr.id = :id")
    Optional<PurchaseRequest> findByIdWithUsers(@Param("id") Long id);

    /** Khóa dòng PR (SELECT ... FOR UPDATE) khi thay đổi trạng thái, chống race condition (TX-02). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pr from PurchaseRequest pr where pr.id = :id")
    Optional<PurchaseRequest> findByIdForUpdate(@Param("id") Long id);
}
