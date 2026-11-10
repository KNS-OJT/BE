package com.p03.inventory.stock.dto;

import com.p03.inventory.stock.StockTransaction;
import com.p03.inventory.stock.StockTransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record StockTransactionResponse(
    Long id,
    Long chainSeq,
    Long productId,
    String sku,
    String productName,
    StockTransactionType transactionType,
    BigDecimal quantity,
    BigDecimal balanceAfter,
    Long goodsReceiptItemId,
    Long stockIssueItemId,
    Long stockAdjustmentItemId,
    Long createdBy,
    Instant createdAt,
    String note
) {

    public static StockTransactionResponse from(StockTransaction transaction) {
        var product = transaction.getProduct();

        return new StockTransactionResponse(
            transaction.getId(),
            transaction.getChainSeq(),
            transaction.getProductId(),
            product.getSku(),
            product.getName(),
            transaction.getTransactionType(),
            transaction.getQuantity(),
            transaction.getBalanceAfter(),
            transaction.getGoodsReceiptItemId(),
            transaction.getStockIssueItemId(),
            transaction.getStockAdjustmentItemId(),
            transaction.getCreatedBy(),
            transaction.getCreatedAt(),
            transaction.getNote()
        );
    }
}
