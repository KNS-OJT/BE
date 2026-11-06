package com.p03.inventory.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsBySku(String sku);

    /**
     * Tìm sản phẩm theo SKU / tên (không phân biệt hoa thường), lọc nhóm hàng, phân trang (IN15).
     * @param pattern mẫu LIKE từ {@code Text.likePattern}
     */
    @EntityGraph(attributePaths = "category")
    @Query("""
            select p from Product p
            where (lower(p.sku) like :pattern escape '\\' or lower(p.name) like :pattern escape '\\')
              and (:categoryId is null or p.category.id = :categoryId)
              and (:activeOnly = false or p.active = true)
            """)
    Page<Product> search(@Param("pattern") String pattern,
                         @Param("categoryId") Long categoryId,
                         @Param("activeOnly") boolean activeOnly,
                         Pageable pageable);

    /** BR-01: SKU bị khóa khi sản phẩm đã xuất hiện trong chứng từ hoặc sổ kho (trùng điều kiện trigger DB). */
    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM purchase_request_items WHERE product_id = :id)
                OR EXISTS (SELECT 1 FROM stock_issue_items      WHERE product_id = :id)
                OR EXISTS (SELECT 1 FROM stock_transactions     WHERE product_id = :id)
            """, nativeQuery = true)
    boolean isUsedInDocuments(@Param("id") Long productId);
}
