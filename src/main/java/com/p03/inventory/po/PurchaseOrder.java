package com.p03.inventory.po;

import com.p03.inventory.supplier.Supplier;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Đơn mua hàng (IN08). Mỗi PO tham chiếu đúng một PR đã APPROVED, mỗi PR tối đa một PO (BR-03, BR-19).
 * <ul>
 *   <li>order_code, order_date, created_at do DB sinh; purchase_request_id, created_by bất biến (trigger kiểm tra).</li>
 *   <li>PR chỉ giữ id (module PR thuộc nhánh khác); đọc PR qua {@link PurchaseRequestReader}.</li>
 *   <li>note chỉ đọc; cancel_reason được ghi bởi thao tác hủy PO.</li>
 * </ul>
 */
@Entity
@Table(name = "purchase_orders")
@Getter
@Setter
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "purchase_order_id")
    private Long id;

    @Column(name = "order_code", nullable = false, length = 30, insertable = false, updatable = false)
    @Generated
    private String orderCode;

    @Column(name = "purchase_request_id", nullable = false, updatable = false)
    private Long purchaseRequestId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @Column(name = "order_date", nullable = false, insertable = false, updatable = false)
    @Generated
    private LocalDate orderDate;

    @Column(name = "expected_delivery_date")
    private LocalDate expectedDeliveryDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "purchase_order_status")
    private PoStatus status;

    @Column(name = "note", columnDefinition = "text", insertable = false, updatable = false)
    private String note;

    @Column(name = "cancel_reason", columnDefinition = "text")
    private String cancelReason;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    @Generated
    private Instant createdAt;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    @BatchSize(size = 100)
    private List<PurchaseOrderItem> items = new ArrayList<>();

    public void addItem(Long purchaseRequestItemId, BigDecimal quantity) {
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setPurchaseOrder(this);
        item.setPurchaseRequestItemId(purchaseRequestItemId);
        item.setQuantity(quantity);
        items.add(item);
    }
}
