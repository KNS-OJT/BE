package com.p03.inventory.pr;

import com.p03.inventory.user.User;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Phiếu yêu cầu mua hàng (IN06, IN07). Trạng thái là PostgreSQL ENUM purchase_request_status.
 * <ul>
 *   <li>request_code, requester_id, created_at: DB tự sinh, immutable.</li>
 *   <li>Chỉ DRAFT mới sửa được nội dung (BR-11).</li>
 *   <li>reviewed_by, reviewed_at: chỉ set khi approve/reject, DB trigger kiểm tra (IA-IN07).</li>
 *   <li>ck_pr_no_self_approval: requester ≠ reviewed_by (BR-08, IA-IN01).</li>
 * </ul>
 */
@Entity
@Table(name = "purchase_requests")
@Getter
@Setter
public class PurchaseRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "purchase_request_id")
    private Long id;

    @Column(name = "request_code", nullable = false, insertable = false, updatable = false)
    @Generated
    private String requestCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false, updatable = false)
    private User requester;

    @Column(name = "reason", nullable = false, columnDefinition = "text")
    private String reason;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "purchase_request_status")
    private PrStatus status;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewer;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_reason", columnDefinition = "text")
    private String reviewReason;

    @Column(name = "cancel_reason", columnDefinition = "text")
    private String cancelReason;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    @Generated
    private Instant createdAt;

    @OneToMany(mappedBy = "purchaseRequest", fetch = FetchType.LAZY)
    private List<PurchaseRequestItem> items = new ArrayList<>();
}

