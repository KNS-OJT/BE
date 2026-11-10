package com.p03.inventory.stockissue;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface StockIssueItemRepository extends JpaRepository<StockIssueItem, Long> {
    List<StockIssueItem> findByStockIssueIdOrderByIdAsc(Long stockIssueId);
}
