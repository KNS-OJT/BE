package com.p03.inventory.goodsreceipt;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select gr from GoodsReceipt gr where gr.id = :id")
    Optional<GoodsReceipt> findByIdForUpdate(@Param("id") Long id);

    Page<GoodsReceipt> findByStatus(GoodsReceiptStatus status, Pageable pageable);
}
