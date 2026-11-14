package com.p03.inventory.adjustment.dto;

import com.p03.inventory.adjustment.StockAdjustment;
import com.p03.inventory.adjustment.StockAdjustmentItem;
import com.p03.inventory.adjustment.StockAdjustmentStatus;
import com.p03.inventory.adjustment.StockAdjustmentType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record StockAdjustmentResponse(
        Long id,
        String code,
        StockAdjustmentType type,
        Long createdBy,
        Long confirmedBy,
        StockAdjustmentStatus status,
        String reason,
        String cancelReason,
        Instant createdAt,
        Instant confirmedAt,
        List<Line> items) {
    public record Line(Long id, Long productId, BigDecimal countedQuantity,
                       BigDecimal systemQuantity, String note) {
        static Line from(StockAdjustmentItem item) {
            return new Line(item.getId(), item.getProductId(), item.getCountedQuantity(),
                    item.getSystemQuantity(), item.getNote());
        }
    }

    public static StockAdjustmentResponse from(StockAdjustment adjustment, List<StockAdjustmentItem> items) {
        return new StockAdjustmentResponse(adjustment.getId(), adjustment.getCode(), adjustment.getType(),
                adjustment.getCreatedBy(), adjustment.getConfirmedBy(), adjustment.getStatus(),
                adjustment.getReason(), adjustment.getCancelReason(), adjustment.getCreatedAt(),
                adjustment.getConfirmedAt(), items.stream().map(Line::from).toList());
    }
}
