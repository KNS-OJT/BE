package com.p03.inventory.goodsreceipt.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record GoodsReceiptLineRequest(
        @NotNull @Positive Long purchaseOrderItemId,
        @NotNull @DecimalMin(value = "0.001") @Digits(integer = 11, fraction = 3) BigDecimal receivedQuantity) {
}
