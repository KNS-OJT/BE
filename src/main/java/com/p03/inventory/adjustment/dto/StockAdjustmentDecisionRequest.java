package com.p03.inventory.adjustment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StockAdjustmentDecisionRequest(@NotBlank @Size(max = 4000) String reason) {
}
