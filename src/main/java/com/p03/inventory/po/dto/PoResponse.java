package com.p03.inventory.po.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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
}
