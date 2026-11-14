package com.p03.inventory.goodsreceipt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GoodsReceiptDecisionRequest(@NotBlank @Size(max = 4000) String reason) {
}
