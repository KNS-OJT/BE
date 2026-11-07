package com.p03.inventory.stock;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;

import java.math.BigDecimal;
import java.util.Optional;

public interface StockBalanceRepository extends JpaRepository<StockBalance, Long> {

    Optional<StockBalance> findByProductId(Long productId);
    @Query(value = """
        SELECT sb.*
        FROM stock_balances sb
        JOIN products p ON p.product_id = sb.product_id
        WHERE p.is_active = true
          AND sb.quantity < sb.minimum_stock
        ORDER BY sb.product_id
        """,
        countQuery = """
        SELECT COUNT(*)
        FROM stock_balances sb
        JOIN products p ON p.product_id = sb.product_id
        WHERE p.is_active = true
          AND sb.quantity < sb.minimum_stock
        """,
        nativeQuery = true)
    Page<StockBalance> findLowStock(Pageable pageable);
    @EntityGraph(attributePaths = {"product", "product.category"})
    @Query("""
            SELECT sb
            FROM StockBalance sb
            JOIN sb.product p
            WHERE p.active = true
              AND (
                  lower(p.sku) LIKE :pattern ESCAPE '\\'
                  OR lower(p.name) LIKE :pattern ESCAPE '\\'
              )
            """)
    Page<StockBalance> search(
        @Param("pattern") String pattern,
        Pageable pageable
    );

    /** Cập nhật ngưỡng tồn tối thiểu (IN05). Đây là thao tác ghi DUY NHẤT backend được làm trên stock_balances. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE stock_balances SET minimum_stock = :min WHERE product_id = :productId", nativeQuery = true)
    int updateMinimumStock(@Param("productId") Long productId, @Param("min") BigDecimal min);
}
