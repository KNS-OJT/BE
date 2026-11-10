package com.p03.inventory.pr.dto;

import com.p03.inventory.pr.PrStatus;
import com.p03.inventory.pr.PurchaseRequest;

import java.time.Instant;

/**
 * Thông tin tóm tắt phiếu yêu cầu mua (dùng trong danh sách).
 */
public record PrSummaryResponse(
        Long id,
        String requestCode,
        Long requesterId,
        String requesterName,
        String reason,
        PrStatus status,
        Instant submittedAt,
        Instant createdAt
) {
    public static PrSummaryResponse from(PurchaseRequest pr) {
        return new PrSummaryResponse(
                pr.getId(),
                pr.getRequestCode(),
                pr.getRequester().getId(),
                pr.getRequester().getFullName(),
                pr.getReason(),
                pr.getStatus(),
                pr.getSubmittedAt(),
                pr.getCreatedAt()
        );
    }
}

