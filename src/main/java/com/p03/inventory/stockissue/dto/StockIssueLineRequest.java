package com.p03.inventory.stockissue.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record StockIssueLineRequest(@NotNull @Positive Long productId,
        @NotNull @Positive @Digits(integer=11, fraction=3) BigDecimal quantity) {}
