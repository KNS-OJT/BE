package com.p03.inventory.pr.dto;

import com.p03.inventory.pr.PrStatus;
import com.p03.inventory.pr.PurchaseRequest;
import com.p03.inventory.pr.PurchaseRequestItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Chi tiết phiếu yêu cầu mua, kèm danh sách dòng hàng.
 */
public record PrDetailResponse(
        Long id,
        String requestCode,
        Long requesterId,
        String requesterName,
        String reason,
        PrStatus status,
        Instant submittedAt,
        Long reviewedBy,
        String reviewerName,
        Instant reviewedAt,
        String reviewReason,
        String cancelReason,
        Instant createdAt,
        List<PrItemResponse> items
) {
    public static PrDetailResponse from(PurchaseRequest pr, List<PurchaseRequestItem> items) {
        return new PrDetailResponse(
                pr.getId(),
                pr.getRequestCode(),
                pr.getRequester().getId(),
                pr.getRequester().getFullName(),
                pr.getReason(),
                pr.getStatus(),
                pr.getSubmittedAt(),
                pr.getReviewer() != null ? pr.getReviewer().getId() : null,
                pr.getReviewer() != null ? pr.getReviewer().getFullName() : null,
                pr.getReviewedAt(),
                pr.getReviewReason(),
                pr.getCancelReason(),
                pr.getCreatedAt(),
                items.stream().map(PrItemResponse::from).toList()
        );
    }

    public record PrItemResponse(
            Long id,
            Long productId,
            String sku,
            String productName,
            String unit,
            BigDecimal quantity,
            String note
    ) {
        public static PrItemResponse from(PurchaseRequestItem item) {
            return new PrItemResponse(
                    item.getId(),
                    item.getProduct().getId(),
                    item.getProduct().getSku(),
                    item.getProduct().getName(),
                    item.getProduct().getUnit(),
                    item.getQuantity(),
                    item.getNote()
            );
        }
    }
}

