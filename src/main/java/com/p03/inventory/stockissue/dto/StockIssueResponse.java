package com.p03.inventory.stockissue.dto;
import com.p03.inventory.stockissue.StockIssueStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
public record StockIssueResponse(Long id, String issueCode, Long requestedBy,
        Long confirmedBy, StockIssueStatus status, Instant requestedAt,
        Instant confirmedAt, String reason, String cancelReason, List<Line> items) {
    public record Line(Long id, Long productId, BigDecimal quantity) {}
}
