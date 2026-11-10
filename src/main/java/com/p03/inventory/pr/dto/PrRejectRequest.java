package com.p03.inventory.pr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload khi Manager từ chối PR. Lý do bắt buộc (BR-10: ck_pr_reject_reason).
 */
public record PrRejectRequest(
        @NotBlank(message = "Lý do từ chối là bắt buộc")
        @Size(max = 10_000, message = "Lý do tối đa 10.000 ký tự") String reason
) {
}

