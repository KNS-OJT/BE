package com.p03.inventory.po.dto;

import com.p03.inventory.po.PurchaseOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record PoResponse(
        Long id,
        String orderCode,
        Long purchaseRequestId,
        Long supplierId,
        String supplierName,
        Long createdBy,
        LocalDate orderDate,
        LocalDate expectedDeliveryDate,
        String status,
        String note,
        String cancelReason,
        Instant createdAt,
        List<Item> items
) {

    public record Item(
            Long id,
            Long prItemId,
            Long productId,
            BigDecimal quantity
    ) {
    }

    /** @param productIdByPrItem product_id theo purchase_request_item_id (đọc từ PR) */
    public static PoResponse from(PurchaseOrder po, Map<Long, Long> productIdByPrItem) {
        List<Item> items = po.getItems().stream()
                .map(i -> new Item(i.getId(), i.getPurchaseRequestItemId(),
                        productIdByPrItem.get(i.getPurchaseRequestItemId()), i.getQuantity()))
                .toList();
        return new PoResponse(po.getId(), po.getOrderCode(), po.getPurchaseRequestId(), po.getSupplier().getId(),
                po.getSupplier().getName(), po.getCreatedBy(), po.getOrderDate(), po.getExpectedDeliveryDate(),
                po.getStatus().name(), po.getNote(), po.getCancelReason(), po.getCreatedAt(), items);
    }
}
