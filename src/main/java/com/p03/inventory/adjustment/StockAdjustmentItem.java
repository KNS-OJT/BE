package com.p03.inventory.adjustment;

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
@Table(name = "stock_adjustment_items")
@Getter
@Setter
public class StockAdjustmentItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stock_adjustment_item_id")
    private Long id;

    @Column(name = "stock_adjustment_id", nullable = false, updatable = false)
    private Long stockAdjustmentId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "counted_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal countedQuantity;

    @Column(name = "system_quantity", precision = 14, scale = 3, insertable = false, updatable = false)
    private BigDecimal systemQuantity;

    @Column(name = "note", columnDefinition = "text")
    private String note;
}
