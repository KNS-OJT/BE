package com.p03.inventory.stock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface StockTransactionRepository
    extends JpaRepository<StockTransaction, Long> {
    @EntityGraph(attributePaths = {"product", "product.category"})
    @Query("""
        SELECT st
        FROM StockTransaction st
        JOIN st.product p
        WHERE (:productId IS NULL OR st.productId = :productId)
          AND st.transactionType = coalesce(:type, st.transactionType)
          AND (
              :pattern IS NULL
              OR lower(p.sku) LIKE :pattern ESCAPE '\\'
              OR lower(p.name) LIKE :pattern ESCAPE '\\'
          )
        """)
    Page<StockTransaction> search(
        @Param("productId") Long productId,
        @Param("type") StockTransactionType type,
        @Param("pattern") String pattern,
        Pageable pageable
    );
}
