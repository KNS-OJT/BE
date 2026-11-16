package com.p03.inventory.stockadjustment;

import com.p03.inventory.common.error.ApiException;
import com.p03.inventory.common.paging.PageResponse;
import com.p03.inventory.common.security.CurrentUser;
import com.p03.inventory.common.security.Role;
import com.p03.inventory.product.ProductRepository;
import com.p03.inventory.stockadjustment.dto.StockAdjustmentCancelRequest;
import com.p03.inventory.stockadjustment.dto.StockAdjustmentCreateRequest;
import com.p03.inventory.stockadjustment.dto.StockAdjustmentLineRequest;
import com.p03.inventory.stockadjustment.dto.StockAdjustmentResponse;
import com.p03.inventory.stockadjustment.dto.StockAdjustmentUpdateRequest;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class StockAdjustmentService {
    private final StockAdjustmentRepository adjustments;
    private final StockAdjustmentItemRepository lines;
    private final ProductRepository products;
    private final EntityManager entityManager;

    public StockAdjustmentService(StockAdjustmentRepository adjustments, StockAdjustmentItemRepository lines,
                                  ProductRepository products, EntityManager entityManager) {
        this.adjustments = adjustments;
        this.lines = lines;
        this.products = products;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public PageResponse<StockAdjustmentResponse> list(StockAdjustmentStatus status, Pageable pageable) {
        requireReader();
        Page<StockAdjustment> page = status == null
                ? adjustments.findAll(pageable) : adjustments.findByStatus(status, pageable);
        return PageResponse.of(page, this::response);
    }

    @Transactional(readOnly = true)
    public StockAdjustmentResponse get(Long id) {
        requireReader();
        return response(adjustments.findById(id).orElseThrow(ApiException::notFound));
    }

    @Transactional
    public StockAdjustmentResponse create(StockAdjustmentCreateRequest request) {
        requireWarehouse();
        validateLines(request.items());
        StockAdjustment adjustment = new StockAdjustment();
        adjustment.setAdjustmentType(request.adjustmentType());
        adjustment.setCreatedBy(CurrentUser.id());
        adjustment.setReason(request.reason().trim());
        adjustments.saveAndFlush(adjustment);
        replaceLines(adjustment.getId(), request.items());
        return response(adjustment);
    }

    @Transactional
    public StockAdjustmentResponse update(Long id, StockAdjustmentUpdateRequest request) {
        requireWarehouse();
        StockAdjustment adjustment = lockedDraft(id);
        validateLines(request.items());
        adjustment.setAdjustmentType(request.adjustmentType());
        adjustment.setReason(request.reason().trim());
        adjustments.saveAndFlush(adjustment);
        replaceLines(id, request.items());
        return response(adjustment);
    }

    @Transactional
    public StockAdjustmentResponse confirm(Long id) {
        requireManager();
        StockAdjustment adjustment = lockedDraft(id);
        if (adjustment.getCreatedBy().equals(CurrentUser.id())) {
            throw ApiException.sodViolation("SA", id);
        }
        List<StockAdjustmentItem> items = lines.findByStockAdjustmentIdOrderByIdAsc(id);
        if (items.isEmpty()) throw ApiException.validation("Phiếu điều chỉnh phải có ít nhất một dòng hàng");

        adjustment.setConfirmedBy(CurrentUser.id());
        adjustment.setStatus(StockAdjustmentStatus.CONFIRMED);
        // Posting, OPENING eligibility and stock locks belong to the existing DB trigger.
        // Any trigger failure rolls back the entire confirmation, including all its lines.
        adjustments.saveAndFlush(adjustment);
        // The trigger updates other rows, which Hibernate's persistence context cannot see.
        items.forEach(entityManager::refresh);
        return StockAdjustmentResponse.from(adjustment, items);
    }

    @Transactional
    public StockAdjustmentResponse cancel(Long id, StockAdjustmentCancelRequest request) {
        requireWarehouse();
        StockAdjustment adjustment = lockedDraft(id);
        adjustment.setCancelReason(request.reason().trim());
        adjustment.setStatus(StockAdjustmentStatus.CANCELED);
        adjustments.saveAndFlush(adjustment);
        return response(adjustment);
    }

    private StockAdjustment lockedDraft(Long id) {
        StockAdjustment adjustment = adjustments.findByIdForUpdate(id).orElseThrow(ApiException::notFound);
        if (adjustment.getStatus() != StockAdjustmentStatus.DRAFT) {
            throw ApiException.invalidStateAudited("Chỉ phiếu điều chỉnh DRAFT mới được sửa hoặc xử lý", "SA", id);
        }
        return adjustment;
    }

    private void validateLines(List<StockAdjustmentLineRequest> requested) {
        Set<Long> productIds = new HashSet<>();
        for (StockAdjustmentLineRequest line : requested) {
            if (!productIds.add(line.productId())) {
                throw ApiException.validation("Sản phẩm bị lặp trong phiếu điều chỉnh");
            }
        }
        // Stocktaking may include inactive products that still have stock to reconcile.
        if (products.findAllById(productIds).size() != productIds.size()) {
            throw ApiException.validation("Sản phẩm trong phiếu điều chỉnh không tồn tại");
        }
    }

    private void replaceLines(Long adjustmentId, List<StockAdjustmentLineRequest> requested) {
        lines.deleteAll(lines.findByStockAdjustmentIdOrderByIdAsc(adjustmentId));
        lines.flush();
        for (StockAdjustmentLineRequest request : requested) {
            StockAdjustmentItem line = new StockAdjustmentItem();
            line.setStockAdjustmentId(adjustmentId);
            line.setProductId(request.productId());
            line.setCountedQuantity(request.countedQuantity());
            line.setNote(request.note() == null || request.note().isBlank() ? null : request.note().trim());
            lines.save(line);
        }
        lines.flush();
    }

    private StockAdjustmentResponse response(StockAdjustment adjustment) {
        return StockAdjustmentResponse.from(adjustment,
                lines.findByStockAdjustmentIdOrderByIdAsc(adjustment.getId()));
    }

    private static void requireReader() {
        if (!CurrentUser.is(Role.WAREHOUSE_STAFF) && !CurrentUser.is(Role.MANAGER)) {
            throw ApiException.forbiddenRole("SA", null);
        }
    }

    private static void requireWarehouse() {
        if (!CurrentUser.is(Role.WAREHOUSE_STAFF)) throw ApiException.forbiddenRole("SA", null);
    }

    private static void requireManager() {
        if (!CurrentUser.is(Role.MANAGER)) throw ApiException.forbiddenRole("SA", null);
    }
}
