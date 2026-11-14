package com.p03.inventory.goodsreceipt;

import com.p03.inventory.common.error.ApiException;
import com.p03.inventory.common.paging.PageResponse;
import com.p03.inventory.common.security.CurrentUser;
import com.p03.inventory.common.security.Role;
import com.p03.inventory.po.PoStatus;
import com.p03.inventory.po.PurchaseOrder;
import com.p03.inventory.po.PurchaseOrderItem;
import com.p03.inventory.po.PurchaseOrderRepository;
import com.p03.inventory.goodsreceipt.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class GoodsReceiptService {
    private final GoodsReceiptRepository receipts;
    private final GoodsReceiptItemRepository lines;
    private final PurchaseOrderRepository orders;

    public GoodsReceiptService(GoodsReceiptRepository receipts, GoodsReceiptItemRepository lines,
                               PurchaseOrderRepository orders) {
        this.receipts = receipts;
        this.lines = lines;
        this.orders = orders;
    }

    @Transactional(readOnly = true)
    public PageResponse<GoodsReceiptResponse> list(GoodsReceiptStatus status, Pageable pageable) {
        requireWarehouse();
        Page<GoodsReceipt> page = status == null
                ? receipts.findAll(pageable) : receipts.findByStatus(status, pageable);
        return PageResponse.of(page, this::response);
    }

    @Transactional(readOnly = true)
    public GoodsReceiptResponse get(Long id) {
        requireWarehouse();
        return response(receipts.findById(id).orElseThrow(ApiException::notFound));
    }

    @Transactional
    public GoodsReceiptResponse create(GoodsReceiptCreateRequest request) {
        requireWarehouse();
        PurchaseOrder po = lockedReceivablePo(request.purchaseOrderId());
        validateLines(po, request.items());
        GoodsReceipt receipt = new GoodsReceipt();
        receipt.setPurchaseOrderId(po.getId());
        receipt.setReceivedBy(CurrentUser.id());
        receipt.setDocumentReference(trimToNull(request.documentReference()));
        receipt.setNote(trimToNull(request.note()));
        receipts.saveAndFlush(receipt);
        replaceLines(receipt.getId(), request.items());
        return response(receipt);
    }

    @Transactional
    public GoodsReceiptResponse update(Long id, GoodsReceiptUpdateRequest request) {
        requireWarehouse();
        GoodsReceipt receipt = receipts.findByIdForUpdate(id).orElseThrow(ApiException::notFound);
        requireDraft(receipt);
        PurchaseOrder po = lockedReceivablePo(receipt.getPurchaseOrderId());
        validateLines(po, request.items());
        receipt.setDocumentReference(trimToNull(request.documentReference()));
        receipt.setNote(trimToNull(request.note()));
        receipts.saveAndFlush(receipt);
        replaceLines(id, request.items());
        return response(receipt);
    }

    @Transactional
    public GoodsReceiptResponse confirm(Long id, GoodsReceiptConfirmRequest request) {
        requireWarehouse();
        GoodsReceipt receipt = receipts.findByIdForUpdate(id).orElseThrow(ApiException::notFound);
        requireDraft(receipt);
        PurchaseOrder po = lockedReceivablePo(receipt.getPurchaseOrderId());
        List<GoodsReceiptItem> receiptItems = lines.findByGoodsReceiptIdOrderByIdAsc(id);
        if (receiptItems.isEmpty()) throw ApiException.validation("Phiếu nhận phải có ít nhất một dòng hàng");
        validateExistingLines(po, receiptItems);
        receipt.setDocumentReference(request.documentReference().trim());
        receipt.setConfirmedBy(CurrentUser.id());
        receipt.setStatus(GoodsReceiptStatus.CONFIRMED);
        receipts.saveAndFlush(receipt);
        return response(receipt);
    }

    @Transactional
    public GoodsReceiptResponse cancel(Long id, GoodsReceiptDecisionRequest request) {
        requireWarehouse();
        GoodsReceipt receipt = receipts.findByIdForUpdate(id).orElseThrow(ApiException::notFound);
        requireDraft(receipt);
        receipt.setCancelReason(request.reason().trim());
        receipt.setStatus(GoodsReceiptStatus.CANCELED);
        receipts.saveAndFlush(receipt);
        return response(receipt);
    }

    private PurchaseOrder lockedReceivablePo(Long id) {
        PurchaseOrder po = orders.findByIdForUpdate(id).orElseThrow(ApiException::notFound);
        if (po.getStatus() != PoStatus.ORDERED && po.getStatus() != PoStatus.PARTIALLY_RECEIVED) {
            throw ApiException.invalidState("Chỉ nhận hàng cho PO đang ORDERED hoặc PARTIALLY_RECEIVED");
        }
        return po;
    }

    private void validateLines(PurchaseOrder po, List<GoodsReceiptLineRequest> requested) {
        Map<Long, PurchaseOrderItem> poItems = new HashMap<>();
        po.getItems().forEach(item -> poItems.put(item.getId(), item));
        Set<Long> seen = new HashSet<>();
        for (GoodsReceiptLineRequest request : requested) {
            if (!seen.add(request.purchaseOrderItemId())) {
                throw ApiException.validation("Dòng PO bị lặp trong phiếu nhận");
            }
            PurchaseOrderItem poItem = poItems.get(request.purchaseOrderItemId());
            if (poItem == null) {
                throw ApiException.validation("Dòng hàng không thuộc PO đã chọn");
            }
            var received = lines.sumConfirmedForPurchaseOrderItem(poItem.getId());
            if (received.add(request.receivedQuantity()).compareTo(poItem.getQuantity()) > 0) {
                throw ApiException.overReceipt();
            }
        }
    }

    private void validateExistingLines(PurchaseOrder po, List<GoodsReceiptItem> receiptItems) {
        Map<Long, PurchaseOrderItem> poItems = new HashMap<>();
        po.getItems().forEach(item -> poItems.put(item.getId(), item));
        Set<Long> seen = new HashSet<>();
        for (GoodsReceiptItem line : receiptItems) {
            PurchaseOrderItem poItem = poItems.get(line.getPurchaseOrderItemId());
            if (poItem == null || !seen.add(line.getPurchaseOrderItemId())) {
                throw ApiException.validation("Dòng hàng không hợp lệ trong phiếu nhận");
            }
            if (lines.sumConfirmedForPurchaseOrderItem(poItem.getId())
                    .add(line.getReceivedQuantity()).compareTo(poItem.getQuantity()) > 0) {
                throw ApiException.overReceipt();
            }
        }
    }

    private void replaceLines(Long receiptId, List<GoodsReceiptLineRequest> requested) {
        lines.deleteAll(lines.findByGoodsReceiptIdOrderByIdAsc(receiptId));
        lines.flush();
        for (GoodsReceiptLineRequest request : requested) {
            GoodsReceiptItem line = new GoodsReceiptItem();
            line.setGoodsReceiptId(receiptId);
            line.setPurchaseOrderItemId(request.purchaseOrderItemId());
            line.setReceivedQuantity(request.receivedQuantity());
            lines.save(line);
        }
        lines.flush();
    }

    private GoodsReceiptResponse response(GoodsReceipt receipt) {
        return GoodsReceiptResponse.from(receipt, lines.findByGoodsReceiptIdOrderByIdAsc(receipt.getId()));
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void requireDraft(GoodsReceipt receipt) {
        if (receipt.getStatus() != GoodsReceiptStatus.DRAFT) {
            throw ApiException.invalidStateAudited("Chỉ phiếu nhận DRAFT mới được sửa hoặc xử lý", "GR",
                    receipt.getId());
        }
    }

    private static void requireWarehouse() {
        if (!CurrentUser.is(Role.WAREHOUSE_STAFF)) throw ApiException.forbiddenRole("GR", null);
    }
}
