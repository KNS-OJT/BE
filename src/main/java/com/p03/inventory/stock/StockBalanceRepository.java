package com.p03.inventory.stock;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface StockBalanceRepository extends JpaRepository<StockBalance, Long> {

    Optional<StockBalance> findByProductId(Long productId);

    /** Cập nhật ngưỡng tồn tối thiểu (IN05). Đây là thao tác ghi DUY NHẤT backend được làm trên stock_balances. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE stock_balances SET minimum_stock = :min WHERE product_id = :productId", nativeQuery = true)
    int updateMinimumStock(@Param("productId") Long productId, @Param("min") BigDecimal min);
}
