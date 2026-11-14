package com.p03.inventory.goodsreceipt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "goods_receipts")
@Getter
@Setter
public class GoodsReceipt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "goods_receipt_id")
    private Long id;

    @Generated
    @Column(name = "receipt_code", insertable = false, updatable = false, nullable = false)
    private String code;

    @Column(name = "purchase_order_id", nullable = false, updatable = false)
    private Long purchaseOrderId;

    @Column(name = "received_by", nullable = false, updatable = false)
    private Long receivedBy;

    @Generated
    @Column(name = "received_at", insertable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "confirmed_by")
    private Long confirmedBy;

    @Generated
    @Column(name = "confirmed_at", insertable = false, updatable = false)
    private Instant confirmedAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "document_status")
    private GoodsReceiptStatus status = GoodsReceiptStatus.DRAFT;

    @Column(name = "document_reference", length = 100)
    private String documentReference;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    @Column(name = "cancel_reason", columnDefinition = "text")
    private String cancelReason;

    @Generated
    @Column(name = "created_at", insertable = false, updatable = false, nullable = false)
    private Instant createdAt;
}
