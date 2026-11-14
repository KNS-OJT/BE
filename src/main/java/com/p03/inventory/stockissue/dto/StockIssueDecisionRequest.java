package com.p03.inventory.stockissue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StockIssueDecisionRequest(@NotBlank @Size(max = 4000) String reason) {
}
