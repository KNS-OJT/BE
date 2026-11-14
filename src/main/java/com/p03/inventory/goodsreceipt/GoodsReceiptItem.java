package com.p03.inventory.goodsreceipt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "goods_receipt_items")
@Getter
@Setter
public class GoodsReceiptItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "goods_receipt_item_id")
    private Long id;

    @Column(name = "goods_receipt_id", nullable = false, updatable = false)
    private Long goodsReceiptId;

    @Column(name = "purchase_order_item_id", nullable = false, updatable = false)
    private Long purchaseOrderItemId;

    @Column(name = "received_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal receivedQuantity;
}
