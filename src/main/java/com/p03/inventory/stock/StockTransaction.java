package com.p03.inventory.stock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.p03.inventory.product.Product;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Getter;

@Entity
@Table(name = "stock_transactions")
@Getter
public class StockTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stock_transaction_id")
    private Long id;

    @Column(name = "chain_seq", nullable = false, unique = true)
    private Long chainSeq;

    @Column(name = "product_id", nullable = false, insertable = false, updatable = false)
    private Long productId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", insertable = false, updatable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "transaction_type", nullable = false, columnDefinition = "stock_transaction_type")
    private StockTransactionType transactionType;

    @Column(name = "quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(name = "balance_after", nullable = false, precision = 14, scale = 3)
    private BigDecimal balanceAfter;

    @Column(name = "goods_receipt_item_id", unique = true)
    private Long goodsReceiptItemId;

    @Column(name = "stock_issue_item_id", unique = true)
    private Long stockIssueItemId;

    @Column(name = "stock_adjustment_item_id", unique = true)
    private Long stockAdjustmentItemId;

    @Column(name = "created_by", nullable = false, insertable = false, updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "note")
    private String note;

    @Column(name = "prev_hash", nullable = false, length = 64, insertable = false, updatable = false)
    private String prevHash;

    @Column(name = "row_hash", nullable = false, unique = true, length = 64, insertable = false, updatable = false)
    private String rowHash;
}
