package com.p03.inventory.stockissue;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import com.p03.inventory.stockissue.StockIssueStatus;
import java.util.Optional;
public interface StockIssueRepository extends JpaRepository<StockIssue, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StockIssue s where s.id = :id")
    Optional<StockIssue> lockById(@Param("id") Long id);
    Page<StockIssue> findByRequestedBy(Long requestedBy, Pageable pageable);
    Page<StockIssue> findByRequestedByAndStatus(Long requestedBy, StockIssueStatus status, Pageable pageable);
    Page<StockIssue> findByStatus(StockIssueStatus status, Pageable pageable);
}
