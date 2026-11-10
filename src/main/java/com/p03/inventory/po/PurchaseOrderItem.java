package com.p03.inventory.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Dòng PO. Phải thuộc đúng PR của PO và không vượt số lượng đã duyệt (trigger trg_purchase_order_items_guard).
 * Không trỏ lại dòng sang PO / dòng PR khác (updatable = false; trigger cũng chặn).
 */
@Entity
@Table(name = "purchase_order_items")
@Getter
@Setter
public class PurchaseOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "purchase_order_item_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false, updatable = false)
    private PurchaseOrder purchaseOrder;

    @Column(name = "purchase_request_item_id", nullable = false, updatable = false)
    private Long purchaseRequestItemId;

    @Column(name = "quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;
}
