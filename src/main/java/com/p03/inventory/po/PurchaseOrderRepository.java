package com.p03.inventory.po;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    /** BR-19: một PR tối đa một PO (UNIQUE purchase_request_id chặn lại khi tranh chấp). */
    boolean existsByPurchaseRequestId(Long purchaseRequestId);

    /** SELECT … FOR UPDATE: khóa dòng PO trước khi sửa / phát hành để hai thao tác đồng thời không cùng qua kiểm tra. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select po from PurchaseOrder po where po.id = :id")
    Optional<PurchaseOrder> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = "supplier")
    Page<PurchaseOrder> findByStatusIn(Collection<PoStatus> statuses, Pageable pageable);
}
