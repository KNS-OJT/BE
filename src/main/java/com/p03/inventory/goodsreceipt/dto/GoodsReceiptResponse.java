package com.p03.inventory.goodsreceipt.dto;

import com.p03.inventory.goodsreceipt.GoodsReceipt;
import com.p03.inventory.goodsreceipt.GoodsReceiptItem;
import com.p03.inventory.goodsreceipt.GoodsReceiptStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record GoodsReceiptResponse(
        Long id,
        String code,
        Long purchaseOrderId,
        Long receivedBy,
        Instant receivedAt,
        Long confirmedBy,
        Instant confirmedAt,
        GoodsReceiptStatus status,
        String documentReference,
        String note,
        String cancelReason,
        List<Line> items) {
    public record Line(Long id, Long purchaseOrderItemId, BigDecimal receivedQuantity) {
        static Line from(GoodsReceiptItem item) {
            return new Line(item.getId(), item.getPurchaseOrderItemId(), item.getReceivedQuantity());
        }
    }

    public static GoodsReceiptResponse from(GoodsReceipt receipt, List<GoodsReceiptItem> items) {
        return new GoodsReceiptResponse(receipt.getId(), receipt.getCode(), receipt.getPurchaseOrderId(),
                receipt.getReceivedBy(), receipt.getReceivedAt(), receipt.getConfirmedBy(), receipt.getConfirmedAt(),
                receipt.getStatus(), receipt.getDocumentReference(), receipt.getNote(), receipt.getCancelReason(),
                items.stream().map(Line::from).toList());
    }
}
