package com.p03.inventory.stockadjustment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record StockAdjustmentLineRequest(
        @NotNull @Positive Long productId,
        @NotNull @DecimalMin("0") @Digits(integer = 11, fraction = 3) BigDecimal countedQuantity,
        @Size(max = 4000) String note) {
}
