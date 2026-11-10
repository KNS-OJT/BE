package com.p03.inventory.pr.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Tạo phiếu yêu cầu mua hàng (Draft). Requester lấy từ JWT – KHÔNG nhận từ body (IA-IN07).
 * Không có trường status, requesterId, submittedAt, reviewedBy, reviewedAt.
 */
public record PrCreateRequest(
        @NotBlank @Size(max = 10_000, message = "Lý do tối đa 10.000 ký tự") String reason,
        @NotEmpty(message = "Phải có ít nhất một dòng hàng") @Valid List<PrItemRequest> items
) {
}

