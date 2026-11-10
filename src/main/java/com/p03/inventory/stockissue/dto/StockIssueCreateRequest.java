package com.p03.inventory.stockissue.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
public record StockIssueCreateRequest(@NotBlank @Size(max=4000) String reason,
        @NotEmpty @Size(max=100) List<@NotNull @Valid StockIssueLineRequest> items) {}
