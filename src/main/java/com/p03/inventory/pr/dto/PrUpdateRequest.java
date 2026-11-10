package com.p03.inventory.pr.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Cập nhật nội dung phiếu Draft (BR-11: chỉ DRAFT mới sửa được).
 * Thay thế toàn bộ reason + danh sách dòng hàng.
 */
public record PrUpdateRequest(
        @NotBlank @Size(max = 10_000, message = "Lý do tối đa 10.000 ký tự") String reason,
        @NotEmpty(message = "Phải có ít nhất một dòng hàng") @Valid List<PrItemRequest> items
) {
}

