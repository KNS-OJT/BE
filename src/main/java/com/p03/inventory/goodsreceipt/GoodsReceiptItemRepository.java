package com.p03.inventory.goodsreceipt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface GoodsReceiptItemRepository extends JpaRepository<GoodsReceiptItem, Long> {
    List<GoodsReceiptItem> findByGoodsReceiptIdOrderByIdAsc(Long goodsReceiptId);

    @Query(value = """
            SELECT COALESCE(SUM(gri.received_quantity), 0)
              FROM goods_receipt_items gri
              JOIN goods_receipts gr ON gr.goods_receipt_id = gri.goods_receipt_id
             WHERE gri.purchase_order_item_id = :poItemId AND gr.status = 'CONFIRMED'
            """, nativeQuery = true)
    BigDecimal sumConfirmedForPurchaseOrderItem(@Param("poItemId") Long poItemId);

}
