package com.p03.inventory.stockadjustment;

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
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "stock_adjustments")
@Getter
@Setter
public class StockAdjustment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stock_adjustment_id")
    private Long id;

    @Generated
    @Column(name = "adjustment_code", nullable = false, length = 30, insertable = false, updatable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "adjustment_type", nullable = false, columnDefinition = "stock_adjustment_type")
    private StockAdjustmentType adjustmentType;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @Column(name = "confirmed_by")
    private Long confirmedBy;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "document_status")
    private StockAdjustmentStatus status = StockAdjustmentStatus.DRAFT;

    @Column(name = "reason", nullable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "cancel_reason", columnDefinition = "text")
    private String cancelReason;

    @Generated
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "confirmed_at", insertable = false, updatable = false)
    private Instant confirmedAt;
}
