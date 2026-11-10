package com.p03.inventory.stock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Số dư tồn theo sản phẩm. quantity CHỈ thay đổi qua posting của trigger khi chứng từ Confirmed (BR-04, BR-07);
 * p03_app chỉ được UPDATE cột minimum_stock. Vì vậy entity không có setter cho quantity.
 * Ghi minimum_stock dùng {@link StockBalanceRepository#updateMinimumStock}.
 */
@Entity
@Table(name = "stock_balances")
@Getter
public class StockBalance {

    @Id
    @Column(name = "stock_balance_id")
    private Long id;

    @Column(name = "product_id", nullable = false, insertable = false, updatable = false)
    private Long productId;

    @Column(name = "quantity", nullable = false, precision = 14, scale = 3, insertable = false, updatable = false)
    private BigDecimal quantity;

    @Column(name = "minimum_stock", nullable = false, precision = 14, scale = 3, insertable = false, updatable = false)
    private BigDecimal minimumStock;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
