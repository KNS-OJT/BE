package com.p03.inventory.adjustment;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select sa from StockAdjustment sa where sa.id = :id")
    Optional<StockAdjustment> findByIdForUpdate(@Param("id") Long id);

    Page<StockAdjustment> findByStatus(StockAdjustmentStatus status, Pageable pageable);
}
