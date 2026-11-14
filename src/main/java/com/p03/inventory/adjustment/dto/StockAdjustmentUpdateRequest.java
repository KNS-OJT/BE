package com.p03.inventory.adjustment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record StockAdjustmentUpdateRequest(
        @NotBlank @Size(max = 4000) String reason,
        @NotEmpty @Size(max = 100) List<@NotNull @Valid StockAdjustmentLineRequest> items) {
}
