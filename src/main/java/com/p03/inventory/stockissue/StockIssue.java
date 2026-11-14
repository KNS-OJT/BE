package com.p03.inventory.stockissue;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
@Entity @Table(name="stock_issues") @Getter @Setter
public class StockIssue {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="stock_issue_id") private Long id;
    @Generated @Column(name="issue_code", insertable=false, updatable=false, length=30) private String code;
    @Column(name="requested_by", nullable=false, updatable=false) private Long requestedBy;
    @Column(name="confirmed_by") private Long confirmedBy;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name="status", nullable=false, columnDefinition="document_status")
    private StockIssueStatus status = StockIssueStatus.DRAFT;
    @Generated @Column(name="requested_at", insertable=false, updatable=false) private Instant requestedAt;
    @Generated @Column(name="confirmed_at", insertable=false, updatable=false) private Instant confirmedAt;
    @Column(name="reason", nullable=false, columnDefinition="text") private String reason;
    @Column(name="note", columnDefinition="text") private String note;
    @Column(name="cancel_reason", columnDefinition="text") private String cancelReason;
}
