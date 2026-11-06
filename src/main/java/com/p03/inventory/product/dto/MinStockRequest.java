package com.p03.inventory.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** IN05: ngưỡng tồn tối thiểu ≥ 0, tối đa 3 chữ số thập phân (numeric(14,3)). */
public record MinStockRequest(@NotNull @DecimalMin("0") @Digits(integer = 11, fraction = 3) BigDecimal minimumStock) {
}
