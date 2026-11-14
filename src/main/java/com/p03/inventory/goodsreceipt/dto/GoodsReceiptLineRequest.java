package com.p03.inventory.goodsreceipt.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record GoodsReceiptLineRequest(
        @NotNull Long purchaseOrderItemId,
        @NotNull @DecimalMin(value = "0.001") BigDecimal receivedQuantity) {
}
