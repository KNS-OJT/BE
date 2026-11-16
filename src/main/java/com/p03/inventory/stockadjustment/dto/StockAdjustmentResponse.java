package com.p03.inventory.stockadjustment.dto;

import com.p03.inventory.stockadjustment.StockAdjustment;
import com.p03.inventory.stockadjustment.StockAdjustmentItem;
import com.p03.inventory.stockadjustment.StockAdjustmentStatus;
import com.p03.inventory.stockadjustment.StockAdjustmentType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record StockAdjustmentResponse(
        Long id,
        String code,
        StockAdjustmentType adjustmentType,
        Long createdBy,
        Long confirmedBy,
        StockAdjustmentStatus status,
        String reason,
        String cancelReason,
        Instant createdAt,
        Instant confirmedAt,
        List<Line> items) {
    public record Line(Long id, Long productId, BigDecimal countedQuantity,
                       BigDecimal systemQuantity, BigDecimal difference, String note) {
        static Line from(StockAdjustmentItem item) {
            BigDecimal difference = item.getSystemQuantity() == null ? null
                    : item.getCountedQuantity().subtract(item.getSystemQuantity());
            return new Line(item.getId(), item.getProductId(), item.getCountedQuantity(),
                    item.getSystemQuantity(), difference, item.getNote());
        }
    }

    public static StockAdjustmentResponse from(StockAdjustment adjustment, List<StockAdjustmentItem> items) {
        return new StockAdjustmentResponse(adjustment.getId(), adjustment.getCode(), adjustment.getAdjustmentType(),
                adjustment.getCreatedBy(), adjustment.getConfirmedBy(), adjustment.getStatus(),
                adjustment.getReason(), adjustment.getCancelReason(), adjustment.getCreatedAt(),
                adjustment.getConfirmedAt(), items.stream().map(Line::from).toList());
    }
}
