package com.p03.inventory.stock.dto;

import com.p03.inventory.stock.StockBalance;

import java.math.BigDecimal;
import java.time.Instant;

public record StockResponse (
    Long productId,
    String sku,
    String productName,
    String categoryName,
    String unit,
    BigDecimal quantity,
    BigDecimal minimumStock,
    boolean lowStock,
    Instant updatedAt
){
    public static StockResponse from(StockBalance balance) {
        var product = balance.getProduct();

        return new StockResponse(
            balance.getProductId(),
            product.getSku(),
            product.getName(),
            product.getCategory().getName(),
            product.getUnit(),
            balance.getQuantity(),
            balance.getMinimumStock(),
            balance.getQuantity().compareTo(balance.getMinimumStock()) < 0,
            balance.getUpdatedAt()
        );
    }
}
