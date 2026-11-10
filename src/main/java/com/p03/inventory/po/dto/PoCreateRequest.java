package com.p03.inventory.po.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PoCreateRequest(
        @NotNull @Positive Long purchaseRequestId,
        @NotNull @Positive Long supplierId,
        @FutureOrPresent LocalDate expectedDeliveryDate,
        @NotEmpty @Size(max = 100) List<@NotNull @Valid Item> items
) {

    public record Item(
            @NotNull @Positive Long prItemId,
            @NotNull @Positive @Digits(integer = 11, fraction = 3) BigDecimal quantity
    ) {
    }
}
