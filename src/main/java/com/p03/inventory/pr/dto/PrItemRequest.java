package com.p03.inventory.pr.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Một dòng hàng trong phiếu yêu cầu mua. Số lượng phải dương, tối đa numeric(14,3).
 */
public record PrItemRequest(
        @NotNull(message = "productId là bắt buộc") @Positive Long productId,
        @NotNull(message = "quantity là bắt buộc")
        @Positive(message = "Số lượng phải lớn hơn 0")
        @Digits(integer = 11, fraction = 3, message = "Số lượng tối đa 11 chữ số phần nguyên và 3 chữ số thập phân")
        BigDecimal quantity,
        @Size(max = 500, message = "Ghi chú dòng hàng tối đa 500 ký tự") String note
) {
}

