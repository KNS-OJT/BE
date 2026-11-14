package com.p03.inventory.adjustment;

import com.p03.inventory.adjustment.dto.*;
import com.p03.inventory.common.error.ApiException;
import com.p03.inventory.common.paging.PageResponse;
import com.p03.inventory.common.security.CurrentUser;
import com.p03.inventory.common.security.Role;
import com.p03.inventory.product.ProductRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
    @PersistenceContext
    private EntityManager entityManager;

    public StockAdjustmentService(StockAdjustmentRepository adjustments, StockAdjustmentItemRepository lines,
                                  ProductRepository products) {
        this.adjustments = adjustments;
        this.lines = lines;
        this.products = products;
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
        adjustment.setType(request.type());
        adjustment.setCreatedBy(CurrentUser.id());
        adjustment.setReason(request.reason().trim());
        adjustments.saveAndFlush(adjustment);
        replaceLines(adjustment.getId(), request.items());
        return response(adjustment);
    }

    @Transactional
    public StockAdjustmentResponse update(Long id, StockAdjustmentUpdateRequest request) {
        requireWarehouse();
        StockAdjustment adjustment = adjustments.findByIdForUpdate(id).orElseThrow(ApiException::notFound);
        requireDraft(adjustment);
        requireCreator(adjustment);
        validateLines(request.items());
        adjustment.setReason(request.reason().trim());
        adjustments.saveAndFlush(adjustment);
        replaceLines(id, request.items());
        return response(adjustment);
    }

    @Transactional
    public StockAdjustmentResponse approve(Long id) {
        requireManager();
        StockAdjustment adjustment = adjustments.findByIdForUpdate(id).orElseThrow(ApiException::notFound);
        requireDraft(adjustment);
        if (CurrentUser.id().equals(adjustment.getCreatedBy())) {
            throw ApiException.sodViolation("SA", id);
        }
        List<StockAdjustmentItem> adjustmentItems = lines.findByStockAdjustmentIdOrderByIdAsc(id);
        if (adjustmentItems.isEmpty()) throw ApiException.validation("Phiếu điều chỉnh phải có ít nhất một sản phẩm");
        adjustment.setConfirmedBy(CurrentUser.id());
        adjustment.setStatus(StockAdjustmentStatus.CONFIRMED);
        adjustments.saveAndFlush(adjustment);
        adjustmentItems.forEach(entityManager::refresh);
        return response(adjustment);
    }

    @Transactional
    public StockAdjustmentResponse cancel(Long id, StockAdjustmentDecisionRequest request) {
        requireWarehouse();
        StockAdjustment adjustment = adjustments.findByIdForUpdate(id).orElseThrow(ApiException::notFound);
        requireDraft(adjustment);
        requireCreator(adjustment);
        adjustment.setCancelReason(request.reason().trim());
        adjustment.setStatus(StockAdjustmentStatus.CANCELED);
        adjustments.saveAndFlush(adjustment);
        return response(adjustment);
    }

    private void validateLines(List<StockAdjustmentLineRequest> requested) {
        Set<Long> seen = new HashSet<>();
        for (StockAdjustmentLineRequest request : requested) {
            if (!seen.add(request.productId())) {
                throw ApiException.validation("Sản phẩm bị lặp trong phiếu điều chỉnh");
            }
            if (!products.existsById(request.productId())) {
                throw ApiException.validation("Sản phẩm không tồn tại");
            }
        }
    }

    private void replaceLines(Long adjustmentId, List<StockAdjustmentLineRequest> requested) {
        lines.deleteAll(lines.findByStockAdjustmentIdOrderByIdAsc(adjustmentId));
        lines.flush();
        for (StockAdjustmentLineRequest request : requested) {
            StockAdjustmentItem item = new StockAdjustmentItem();
            item.setStockAdjustmentId(adjustmentId);
            item.setProductId(request.productId());
            item.setCountedQuantity(request.countedQuantity());
            item.setNote(request.note() == null || request.note().isBlank() ? null : request.note().trim());
            lines.save(item);
        }
        lines.flush();
    }

    private StockAdjustmentResponse response(StockAdjustment adjustment) {
        return StockAdjustmentResponse.from(adjustment,
                lines.findByStockAdjustmentIdOrderByIdAsc(adjustment.getId()));
    }

    private static void requireDraft(StockAdjustment adjustment) {
        if (adjustment.getStatus() != StockAdjustmentStatus.DRAFT) {
            throw ApiException.invalidStateAudited("Chỉ phiếu điều chỉnh DRAFT mới được sửa hoặc xử lý", "SA",
                    adjustment.getId());
        }
    }

    private static void requireCreator(StockAdjustment adjustment) {
        if (!CurrentUser.id().equals(adjustment.getCreatedBy())) {
            throw ApiException.notOwner("SA", adjustment.getId());
        }
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
