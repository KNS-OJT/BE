package com.p03.inventory.po;

import com.p03.inventory.common.error.ApiException;
import com.p03.inventory.common.paging.PageResponse;
import com.p03.inventory.common.security.CurrentUser;
import com.p03.inventory.common.security.Role;
import com.p03.inventory.po.dto.PoCreateRequest;
import com.p03.inventory.po.dto.PoResponse;
import com.p03.inventory.po.dto.PoUpdateRequest;
import com.p03.inventory.supplier.Supplier;
import com.p03.inventory.supplier.SupplierRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Purchase Order (IN08). BR-02/BR-03/BR-19: PO chỉ lập từ PR APPROVED, mỗi PR tối đa một PO.
 * <ul>
 *   <li>Người lập = {@code CurrentUser.id()}; không nhận createdBy / status / orderCode từ body.</li>
 *   <li>Sửa và phát hành khóa dòng PO (FOR UPDATE), chỉ khi PO đang DRAFT. Đổi trạng thái chỉ qua /order.</li>
 *   <li>Phạm vi xem: Purchasing, Manager xem tất cả; Warehouse chỉ PO chờ nhận (ORDERED, PARTIALLY_RECEIVED);
 *       PO ngoài phạm vi trả 404 giống hệt PO không tồn tại (có ghi audit NOT_OWNER).</li>
 *   <li>Không đụng stock_balances / stock_transactions / audit_logs: trigger lo.</li>
 * </ul>
 */
@Service
public class PoService {

    static final Set<PoStatus> WAREHOUSE_VISIBLE = EnumSet.of(PoStatus.ORDERED, PoStatus.PARTIALLY_RECEIVED);

    private final PurchaseOrderRepository orders;
    private final SupplierRepository suppliers;
    private final PurchaseRequestReader prReader;

    public PoService(PurchaseOrderRepository orders, SupplierRepository suppliers, PurchaseRequestReader prReader) {
        this.orders = orders;
        this.suppliers = suppliers;
        this.prReader = prReader;
    }

    /** Dòng yêu cầu chung cho tạo / sửa. */
    record Line(Long prItemId, BigDecimal quantity) {
    }

    // ---- Đọc ----------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<PoResponse> list(PoStatus status, Pageable pageable) {
        Set<PoStatus> allowed = visibleStatuses();
        Set<PoStatus> filter = EnumSet.noneOf(PoStatus.class);
        filter.addAll(allowed);
        if (status != null) {
            filter.retainAll(EnumSet.of(status));
        }
        if (filter.isEmpty()) {
            return new PageResponse<>(List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0, 0);
        }
        Page<PurchaseOrder> page = orders.findByStatusIn(filter, pageable);
        Set<Long> prItemIds = new HashSet<>();
        page.forEach(po -> po.getItems().forEach(i -> prItemIds.add(i.getPurchaseRequestItemId())));
        Map<Long, Long> productIds = prReader.findProductIds(prItemIds);
        return PageResponse.of(page, po -> PoResponse.from(po, productIds));
    }

    @Transactional(readOnly = true)
    public PoResponse get(Long id) {
        PurchaseOrder po = orders.findById(id).orElseThrow(ApiException::notFound);
        if (!visibleStatuses().contains(po.getStatus())) {
            throw ApiException.notOwner("PO", id);
        }
        return toResponse(po);
    }

    // ---- Ghi ----------------------------------------------------------------------------

    @Transactional
    public PoResponse create(PoCreateRequest req) {
        Long prId = req.purchaseRequestId();
        String prStatus = prReader.findStatus(prId)
                .orElseThrow(() -> ApiException.validation("Yêu cầu mua hàng không tồn tại"));
        if (!"APPROVED".equals(prStatus)) {
            throw ApiException.invalidState("Chỉ lập PO từ yêu cầu mua hàng đã duyệt (PR đang " + prStatus + ")");
        }
        if (orders.existsByPurchaseRequestId(prId)) {
            throw ApiException.duplicate("Yêu cầu mua hàng này đã được lập PO");
        }
        Supplier supplier = activeSupplier(req.supplierId());
        List<Line> lines = req.items() == null ? List.of()
                : req.items().stream().map(i -> new Line(i.prItemId(), i.quantity())).toList();
        validateLines(lines, prReader.findItems(prId));

        PurchaseOrder po = new PurchaseOrder();
        po.setPurchaseRequestId(prId);
        po.setSupplier(supplier);
        po.setCreatedBy(CurrentUser.id());
        po.setExpectedDeliveryDate(req.expectedDeliveryDate());
        po.setStatus(PoStatus.DRAFT);
        lines.forEach(l -> po.addItem(l.prItemId(), l.quantity()));
        return toResponse(orders.saveAndFlush(po));
    }

    /** Sửa nhà cung cấp, ngày giao dự kiến và các dòng khi PO còn DRAFT. Sau khi sửa PO còn ít nhất 1 dòng. */
    @Transactional
    public PoResponse update(Long id, PoUpdateRequest req) {
        PurchaseOrder po = orders.findByIdForUpdate(id).orElseThrow(ApiException::notFound);
        requireDraft(po);
        Supplier supplier = activeSupplier(req.supplierId());
        List<Line> lines = req.items() == null ? List.of()
                : req.items().stream().map(i -> new Line(i.prItemId(), i.quantity())).toList();
        validateLines(lines, prReader.findItems(po.getPurchaseRequestId()));

        po.setSupplier(supplier);
        po.setExpectedDeliveryDate(req.expectedDeliveryDate());
        syncItems(po, lines);
        return toResponse(orders.saveAndFlush(po));
    }

    /** Phát hành: DRAFT → ORDERED. Nhà cung cấp phải còn hợp tác (trigger DB không kiểm tra ở bước này). */
    @Transactional
    public PoResponse order(Long id) {
        PurchaseOrder po = orders.findByIdForUpdate(id).orElseThrow(ApiException::notFound);
        requireDraft(po);
        if (!po.getSupplier().isActive()) {
            throw ApiException.validation("Nhà cung cấp đã ngừng hợp tác, hãy đổi nhà cung cấp trước khi phát hành PO");
        }
        if (po.getItems().isEmpty()) {
            throw ApiException.validation("PO phải có ít nhất một dòng hàng");
        }
        po.setStatus(PoStatus.ORDERED);
        return toResponse(orders.saveAndFlush(po));
    }

    // ---- helpers ------------------------------------------------------------------------

    private static Set<PoStatus> visibleStatuses() {
        if (CurrentUser.is(Role.PURCHASING_STAFF) || CurrentUser.is(Role.MANAGER)) {
            return EnumSet.allOf(PoStatus.class);
        }
        if (CurrentUser.is(Role.WAREHOUSE_STAFF)) {
            return WAREHOUSE_VISIBLE;
        }
        return EnumSet.noneOf(PoStatus.class);
    }

    private static void requireDraft(PurchaseOrder po) {
        if (po.getStatus() != PoStatus.DRAFT) {
            throw ApiException.invalidState("PO " + po.getOrderCode() + " đang " + po.getStatus()
                    + ", chỉ PO DRAFT mới được sửa hoặc phát hành");
        }
    }

    private Supplier activeSupplier(Long supplierId) {
        Supplier s = suppliers.findById(supplierId)
                .orElseThrow(() -> ApiException.validation("Nhà cung cấp không tồn tại"));
        if (!s.isActive()) {
            throw ApiException.validation("Nhà cung cấp đã ngừng hợp tác");
        }
        return s;
    }

    /** Mỗi dòng phải thuộc PR, không trùng, số lượng > 0 và không vượt số đã duyệt (trigger kiểm tra lại). */
    static void validateLines(List<Line> lines, Map<Long, PurchaseRequestReader.PrItem> prItems) {
        if (lines.isEmpty()) {
            throw ApiException.validation("PO phải có ít nhất một dòng hàng");
        }
        Set<Long> seen = new HashSet<>();
        for (Line l : lines) {
            if (!seen.add(l.prItemId())) {
                throw ApiException.validation("Dòng yêu cầu " + l.prItemId() + " bị lặp");
            }
            PurchaseRequestReader.PrItem pri = prItems.get(l.prItemId());
            if (pri == null) {
                throw ApiException.validation("Dòng yêu cầu " + l.prItemId() + " không thuộc yêu cầu mua hàng của PO");
            }
            if (l.quantity() == null || l.quantity().signum() <= 0) {
                throw ApiException.validation("Số lượng của dòng " + l.prItemId() + " phải lớn hơn 0");
            }
            if (l.quantity().compareTo(pri.quantity()) > 0) {
                throw ApiException.validation("Số lượng của dòng " + l.prItemId()
                        + " vượt số lượng đã duyệt (" + pri.quantity().stripTrailingZeros().toPlainString() + ")");
            }
        }
    }

    /**
     * Đồng bộ dòng PO theo prItemId: dòng đã có → đổi số lượng; dòng mới → thêm; dòng không còn → xóa.
     * Không bao giờ trỏ lại dòng cũ sang dòng PR khác (trigger cấm).
     */
    static void syncItems(PurchaseOrder po, List<Line> lines) {
        Map<Long, BigDecimal> wanted = new LinkedHashMap<>();
        lines.forEach(l -> wanted.put(l.prItemId(), l.quantity()));
        for (Iterator<PurchaseOrderItem> it = po.getItems().iterator(); it.hasNext(); ) {
            PurchaseOrderItem item = it.next();
            BigDecimal qty = wanted.remove(item.getPurchaseRequestItemId());
            if (qty == null) {
                it.remove();
            } else if (item.getQuantity() == null || item.getQuantity().compareTo(qty) != 0) {
                item.setQuantity(qty);
            }
        }
        wanted.forEach(po::addItem);
    }

    private PoResponse toResponse(PurchaseOrder po) {
        Map<Long, Long> productIds = prReader.findProductIds(
                po.getItems().stream().map(PurchaseOrderItem::getPurchaseRequestItemId).toList());
        return PoResponse.from(po, productIds);
    }
}
