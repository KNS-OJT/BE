package com.p03.inventory.pr;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PurchaseRequestItemRepository extends JpaRepository<PurchaseRequestItem, Long> {

    /** Dòng hàng kèm thông tin sản phẩm (tránh N+1 khi trả response chi tiết). */
    @EntityGraph(attributePaths = "product")
    @Query("select i from PurchaseRequestItem i where i.purchaseRequest.id = :prId order by i.id")
    List<PurchaseRequestItem> findByPurchaseRequestId(@Param("prId") Long prId);

    /** Kiểm tra PR có ít nhất 1 dòng hàng (BR-12: cần trước khi submit). */
    boolean existsByPurchaseRequestId(Long purchaseRequestId);

    /** Xoá tất cả dòng hàng khi cần thay thế toàn bộ (chỉ khi PR ở DRAFT; trigger guard kiểm tra). */
    void deleteAllByPurchaseRequestId(Long purchaseRequestId);
}

