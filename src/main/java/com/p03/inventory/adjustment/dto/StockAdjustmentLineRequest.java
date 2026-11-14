package com.p03.inventory.adjustment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record StockAdjustmentLineRequest(
        @NotNull Long productId,
        @NotNull @DecimalMin("0") BigDecimal countedQuantity,
        @Size(max = 4000) String note) {
}
