package com.p03.inventory.goodsreceipt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GoodsReceiptConfirmRequest(@NotBlank @Size(max = 100) String documentReference) {
}
