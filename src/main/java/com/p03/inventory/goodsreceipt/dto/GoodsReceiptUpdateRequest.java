package com.p03.inventory.goodsreceipt.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record GoodsReceiptUpdateRequest(
        @Size(max = 100) String documentReference,
        @Size(max = 4000) String note,
        @NotEmpty @Size(max = 100) List<@NotNull @Valid GoodsReceiptLineRequest> items) {
}
