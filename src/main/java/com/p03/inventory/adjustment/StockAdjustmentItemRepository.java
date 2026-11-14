package com.p03.inventory.adjustment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockAdjustmentItemRepository extends JpaRepository<StockAdjustmentItem, Long> {
    List<StockAdjustmentItem> findByStockAdjustmentIdOrderByIdAsc(Long stockAdjustmentId);
    void deleteByStockAdjustmentId(Long stockAdjustmentId);
}
