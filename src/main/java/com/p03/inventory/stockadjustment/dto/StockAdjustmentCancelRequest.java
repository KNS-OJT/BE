package com.p03.inventory.stockadjustment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StockAdjustmentCancelRequest(@NotBlank @Size(max = 4000) String reason) {
}
