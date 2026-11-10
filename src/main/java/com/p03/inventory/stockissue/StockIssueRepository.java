package com.p03.inventory.stockissue;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import java.util.Optional;
public interface StockIssueRepository extends JpaRepository<StockIssue, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StockIssue s where s.id = :id")
    Optional<StockIssue> lockById(@Param("id") Long id);
    Page<StockIssue> findByRequestedBy(Long requestedBy, Pageable pageable);
}
