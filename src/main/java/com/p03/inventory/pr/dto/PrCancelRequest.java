package com.p03.inventory.pr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload khi Requester huỷ PR (DRAFT/SUBMITTED → CANCELED). Lý do bắt buộc (ck_pr_cancel_reason).
 */
public record PrCancelRequest(
        @NotBlank(message = "Lý do huỷ là bắt buộc")
        @Size(max = 10_000, message = "Lý do tối đa 10.000 ký tự") String reason
) {
}

